package pl.grafikpracy;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.time.LocalDate;
import java.util.Map;

final class Alarms {
    static final String RING = "pl.grafikpracy.RING";

    private Alarms() {}

    static boolean allowed(Context context) {
        return Build.VERSION.SDK_INT < 31
                || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    static void restore(Context context) {
        if (!allowed(context)) {
            return;
        }
        Map<LocalDate, DayPlan> days = Store.days(context);
        for (Shift shift : Store.shifts(context)) {
            if (!shift.deleted && !days.getOrDefault(shift.date, DayPlan.EMPTY).isAbsent()) {
                schedule(context, shift);
            }
        }
    }

    static boolean schedule(Context context, Shift shift) {
        long trigger = shift.alarmAt();
        if (shift.lead < 0 || trigger <= System.currentTimeMillis() || !allowed(context)) {
            return false;
        }
        PendingIntent ring = PendingIntent.getBroadcast(context, 0, ringIntent(context, shift.id)
                .putExtra("trigger", trigger), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent show = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        try {
            context.getSystemService(AlarmManager.class)
                    .setAlarmClock(new AlarmManager.AlarmClockInfo(trigger, show), ring);
            return true;
        } catch (SecurityException exception) {
            return false;
        }
    }

    static void cancel(Context context, String id) {
        PendingIntent ring = PendingIntent.getBroadcast(context, 0, ringIntent(context, id),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (ring != null) {
            context.getSystemService(AlarmManager.class).cancel(ring);
            ring.cancel();
        }
    }

    private static Intent ringIntent(Context context, String id) {
        return new Intent(context, AlarmReceiver.class).setAction(RING)
                .setData(Uri.parse("grafikpracy://alarm/" + id)).putExtra("shift", id);
    }
}
