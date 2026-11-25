package pl.grafikpracy;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

final class Reminders {
    static final String ACTION = "pl.grafikpracy.REMIND";
    static final String OPEN_WEEK = "pl.grafikpracy.OPEN_WEEK";
    static final String SNOOZE = "pl.grafikpracy.SNOOZE_REMINDER";
    private static final long SNOOZE_MILLIS = 15 * 60 * 1000L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, d MMMM",
            Locale.forLanguageTag("pl-PL"));

    private Reminders() {}

    static boolean enabled(Context context) {
        return Store.prefs(context).getBoolean("reminders_enabled", false);
    }

    static int lead(Context context) {
        return Store.prefs(context).getInt("reminders_lead", 120);
    }

    static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(NotificationChannels.REMINDER);
        return manager.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    static long time(Context context, Shift shift) {
        return shift.startsAt().minusMinutes(lead(context)).toInstant().toEpochMilli();
    }

    static void reconfigure(Context context) {
        for (Shift shift : Store.shifts(context)) {
            cancel(context, shift.id);
        }
        restore(context);
    }

    static void restore(Context context) {
        if (!enabled(context) || !canNotify(context)) {
            return;
        }
        Map<LocalDate, DayPlan> days = Store.days(context);
        for (Shift shift : Store.shifts(context)) {
            if (!shift.deleted && !days.getOrDefault(shift.date, DayPlan.EMPTY).isAbsent()) {
                schedule(context, shift);
            }
        }
    }

    static void schedule(Context context, Shift shift) {
        long trigger = time(context, shift);
        if (!enabled(context) || !canNotify(context) || trigger <= System.currentTimeMillis()) {
            return;
        }
        scheduleAt(context, shift.id, trigger, trigger);
    }

    private static void scheduleAt(Context context, String id, long trigger, long baseTrigger) {
        PendingIntent reminder = PendingIntent.getBroadcast(context, 0,
                reminderIntent(context, id).putExtra("trigger", baseTrigger),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (Alarms.allowed(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, reminder);
                return;
            } catch (SecurityException ignored) {
                // The permission can change between the check and scheduling.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, reminder);
    }

    static void cancel(Context context, String id) {
        PendingIntent reminder = PendingIntent.getBroadcast(context, 0, reminderIntent(context, id),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (reminder != null) {
            context.getSystemService(AlarmManager.class).cancel(reminder);
            reminder.cancel();
        }
        context.getSystemService(NotificationManager.class).cancel(id, 1);
    }

    static void snooze(Context context, Shift shift, long baseTrigger) {
        long delayUntil = System.currentTimeMillis() + SNOOZE_MILLIS;
        if (enabled(context) && canNotify(context) && baseTrigger == time(context, shift)
                && delayUntil < shift.startsAt().toInstant().toEpochMilli()) {
            scheduleAt(context, shift.id, delayUntil, baseTrigger);
            context.getSystemService(NotificationManager.class).cancel(shift.id, 1);
        }
    }

    static void show(Context context, Shift shift, long trigger) {
        if (!enabled(context) || !canNotify(context) || trigger != time(context, shift)
                || shift.startsAt().plusMinutes(5).toInstant().toEpochMilli() < System.currentTimeMillis()) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannels.register(context);
        Intent open = new Intent(context, MainActivity.class).setAction(OPEN_WEEK)
                .setData(Uri.parse("grafikpracy://week/" + shift.id)).putExtra("date", shift.date.toString())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String hours = shift.date.format(DATE) + "  ·  " + shift.start.format(ShiftHours.TIME)
                + " – " + shift.end.format(ShiftHours.TIME) + (shift.endNextDay ? " +1" : "");
        Notification.Builder notice = new Notification.Builder(context, NotificationChannels.REMINDER)
                .setSmallIcon(R.drawable.ic_notification).setColor(Ui.ACCENT).setContentTitle("Nadchodząca zmiana")
                .setContentText(hours).setStyle(new Notification.BigTextStyle().bigText(hours))
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true).setContentIntent(pending)
                .addAction(new Notification.Action.Builder(null, "Otwórz grafik", pending).build());
        if (System.currentTimeMillis() + SNOOZE_MILLIS < shift.startsAt().toInstant().toEpochMilli()) {
            Intent snooze = new Intent(context, AlarmReceiver.class).setAction(SNOOZE)
                    .setData(Uri.parse("grafikpracy://snooze/" + shift.id))
                    .putExtra("shift", shift.id).putExtra("trigger", trigger);
            PendingIntent later = PendingIntent.getBroadcast(context, 0, snooze,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            notice.addAction(new Notification.Action.Builder(null, "Za 15 min", later).build());
        }
        try {
            manager.notify(shift.id, 1, notice.build());
        } catch (SecurityException ignored) {
            // The user can revoke notification access while a reminder is being delivered.
        }
    }

    private static Intent reminderIntent(Context context, String id) {
        return new Intent(context, AlarmReceiver.class).setAction(ACTION)
                .setData(Uri.parse("grafikpracy://reminder/" + id)).putExtra("shift", id);
    }
}
