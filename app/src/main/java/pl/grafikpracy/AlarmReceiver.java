package pl.grafikpracy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean ring = Alarms.RING.equals(intent.getAction());
        boolean reminder = Reminders.ACTION.equals(intent.getAction());
        boolean snooze = Reminders.SNOOZE.equals(intent.getAction());
        if (!ring && !reminder && !snooze) {
            Alarms.restore(context);
            Reminders.restore(context);
            return;
        }
        String id = intent.getStringExtra("shift");
        long trigger = intent.getLongExtra("trigger", -1);
        for (Shift shift : Store.shifts(context)) {
            if (!shift.id.equals(id) || shift.deleted) {
                continue;
            }
            if (Store.isAbsent(context, shift.date)) {
                return;
            }
            if (reminder) {
                Reminders.show(context, shift, trigger);
            } else if (snooze) {
                Reminders.snooze(context, shift, trigger);
            } else if (shift.lead >= 0 && trigger == shift.alarmAt()) {
                context.startForegroundService(new Intent(context, AlarmService.class)
                        .putExtra("start", shift.start.toString()));
            }
            return;
        }
    }
}
