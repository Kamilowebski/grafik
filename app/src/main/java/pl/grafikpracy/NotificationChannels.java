package pl.grafikpracy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;

final class NotificationChannels {
    static final String ALARM = "alarm";
    static final String REMINDER = "shift_reminders";
    private static final String GROUP = "schedule";

    private NotificationChannels() {}

    static void register(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannelGroup(new NotificationChannelGroup(GROUP, "Grafik pracy"));

        NotificationChannel reminder = new NotificationChannel(REMINDER, "Przypomnienia o zmianach",
                NotificationManager.IMPORTANCE_DEFAULT);
        reminder.setDescription("Powiadomienia przed zaplanowaną zmianą.");
        reminder.setGroup(GROUP);
        manager.createNotificationChannel(reminder);

        NotificationChannel alarm = new NotificationChannel(ALARM, "Budzik przed pracą",
                NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription("Powiadomienie z przyciskiem wyłączenia budzika.");
        alarm.setSound(null, null);
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        alarm.setGroup(GROUP);
        manager.createNotificationChannel(alarm);
    }
}
