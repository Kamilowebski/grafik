package pl.grafikpracy;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.util.Log;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class GoogleCalendar {
    private static final String EVENT_TITLE = "Praca";
    static final String[] PERMISSIONS = {Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR};

    static final class Choice {
        final long id;
        final String label;

        Choice(long id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private GoogleCalendar() {}

    static boolean permitted(Context context) {
        for (String permission : PERMISSIONS) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    static List<Choice> choices(Context context) {
        String[] columns = {Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME};
        String selection = Calendars.ACCOUNT_TYPE + "=? AND " + Calendars.SYNC_EVENTS
                + "=1 AND " + Calendars.CALENDAR_ACCESS_LEVEL + ">=?";
        List<Choice> choices = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(Calendars.CONTENT_URI, columns,
                selection, new String[]{"com.google", String.valueOf(Calendars.CAL_ACCESS_CONTRIBUTOR)},
                Calendars.CALENDAR_DISPLAY_NAME)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    choices.add(new Choice(cursor.getLong(0), cursor.getString(1) + " · " + cursor.getString(2)));
                }
            }
        }
        return choices;
    }

    // The caller serializes calendar work and changes to the local schedule.
    static boolean sync(Context context, boolean refreshAll) {
        long calendarId = Store.calendarId(context);
        if (calendarId < 0 || !permitted(context)) {
            return false;
        }
        List<Shift> shifts = Store.shifts(context);
        try {
            if (choices(context).stream().noneMatch(choice -> choice.id == calendarId)) {
                return false;
            }
            if (refreshAll || Store.prefs(context).getBoolean("calendar_refresh", false)) {
                for (Shift shift : shifts) {
                    shift.pending = true;
                }
                Store.save(context, shifts);
                Store.prefs(context).edit().remove("calendar_refresh").apply();
            }
            ContentResolver resolver = context.getContentResolver();
            Map<LocalDate, DayPlan> days = Store.days(context);
            Iterator<Shift> iterator = shifts.iterator();
            while (iterator.hasNext()) {
                Shift shift = iterator.next();
                if (!shift.pending) {
                    continue;
                }
                try {
                    if (shift.deleted) {
                        removeEvents(resolver, shift);
                        iterator.remove();
                    } else if (days.getOrDefault(shift.date, DayPlan.EMPTY).isAbsent()) {
                        removeEvents(resolver, shift);
                        shift.pending = false;
                    } else {
                        writeEvent(resolver, shift, calendarId);
                        shift.pending = false;
                    }
                } catch (RuntimeException exception) {
                    Log.w("GrafikPracy", "Pominięto aktualizację wydarzenia", exception);
                    continue;
                }
                Store.save(context, shifts);
            }
            return shifts.stream().noneMatch(shift -> shift.pending);
        } catch (RuntimeException exception) {
            Log.w("GrafikPracy", "Synchronizacja kalendarza wymaga ponowienia", exception);
            return false;
        }
    }

    private static Cursor findEvents(ContentResolver resolver, Shift shift) {
        return resolver.query(Events.CONTENT_URI, new String[]{Events._ID, Events.CALENDAR_ID,
                        Events.DTSTART, Events.DTEND, Events.TITLE},
                Events.CUSTOM_APP_URI + "=? AND " + Events.DELETED + "=0",
                new String[]{shift.marker()}, Events._ID);
    }

    private static void removeEvents(ContentResolver resolver, Shift shift) {
        try (Cursor cursor = findEvents(resolver, shift)) {
            if (cursor == null) {
                throw new IllegalStateException("Kalendarz jest niedostępny");
            }
            while (cursor.moveToNext()) {
                resolver.delete(eventUri(cursor.getLong(0)), null, null);
            }
        }
    }

    private static void writeEvent(ContentResolver resolver, Shift shift, long calendarId) {
        ContentValues values = new ContentValues();
        long start = shift.startsAt().toInstant().toEpochMilli();
        long end = shift.endsAt().toInstant().toEpochMilli();
        values.put(Events.TITLE, EVENT_TITLE);
        values.put(Events.DTSTART, start);
        values.put(Events.DTEND, end);
        values.put(Events.EVENT_TIMEZONE, shift.zone.getId());
        values.put(Events.CUSTOM_APP_URI, shift.marker());

        long targetEvent = -1;
        boolean targetMatches = false;
        List<Long> obsoleteEvents = new ArrayList<>();
        try (Cursor cursor = findEvents(resolver, shift)) {
            if (cursor == null) {
                throw new IllegalStateException("Kalendarz jest niedostępny");
            }
            while (cursor.moveToNext()) {
                long event = cursor.getLong(0);
                if (cursor.getLong(1) == calendarId && targetEvent < 0) {
                    targetEvent = event;
                    targetMatches = cursor.getLong(2) == start && cursor.getLong(3) == end
                            && EVENT_TITLE.equals(cursor.getString(4));
                } else {
                    obsoleteEvents.add(event);
                }
            }
        }
        if (!targetMatches && hasEquivalentEvent(resolver, shift, calendarId, start, end)) {
            if (targetEvent >= 0) {
                obsoleteEvents.add(targetEvent);
            }
        } else if (targetEvent < 0 || resolver.update(eventUri(targetEvent), values, null, null) == 0) {
            values.put(Events.CALENDAR_ID, calendarId);
            Uri inserted = resolver.insert(Events.CONTENT_URI, values);
            if (inserted == null) {
                throw new IllegalStateException("Kalendarz nie zapisał wydarzenia");
            }
        }
        for (long obsolete : obsoleteEvents) {
            resolver.delete(eventUri(obsolete), null, null);
        }
    }

    private static boolean hasEquivalentEvent(ContentResolver resolver, Shift shift, long calendarId,
            long start, long end) {
        String criteria = Events.CALENDAR_ID + "=? AND " + Events.DTSTART + "=? AND " + Events.DTEND
                + "=? AND " + Events.DELETED + "=0 AND " + Events.ALL_DAY + "=0 AND ("
                + Events.CUSTOM_APP_URI + " IS NULL OR " + Events.CUSTOM_APP_URI + "<>?)";
        String[] args = {String.valueOf(calendarId), String.valueOf(start), String.valueOf(end), shift.marker()};
        try (Cursor cursor = resolver.query(Events.CONTENT_URI, new String[]{Events.TITLE, Events.STATUS},
                criteria, args, null)) {
            if (cursor == null) {
                throw new IllegalStateException("Kalendarz jest niedostępny");
            }
            while (cursor.moveToNext()) {
                String title = cursor.getString(0);
                if (title != null && EVENT_TITLE.equalsIgnoreCase(title.trim())
                        && cursor.getInt(1) != Events.STATUS_CANCELED) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Uri eventUri(long id) {
        return ContentUris.withAppendedId(Events.CONTENT_URI, id);
    }
}
