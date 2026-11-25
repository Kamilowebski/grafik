package pl.grafikpracy;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.IOException;

public final class AlarmService extends Service {
    static final String STOP = "pl.grafikpracy.STOP";
    static boolean ringing;
    static Runnable onRingingChanged;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::stopSelf;
    private MediaPlayer player;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String start = intent.getStringExtra("start");
        ringing = true;
        startForeground(1, notification(start));
        notifyRingingChanged();
        if (player == null) {
            play();
        }
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, 10 * 60 * 1000L);
        return START_NOT_STICKY;
    }

    private Notification notification(String start) {
        NotificationChannels.register(this);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, AlarmActivity.class).putExtra("start", start)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, AlarmService.class)
                .setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, NotificationChannels.ALARM).setSmallIcon(R.drawable.ic_alarm_notification)
                .setColor(Ui.ACCENT)
                .setContentTitle("Czas wstać").setContentText("Zmiana zaczyna się o " + start)
                .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true).setContentIntent(open).setFullScreenIntent(open, true)
                .addAction(new Notification.Action.Builder(null, "Wyłącz budzik", stop).build())
                .build();
    }

    private void play() {
        Uri sound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.alarm_tone);
        player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this, sound);
            player.setLooping(true);
            player.setOnErrorListener((media, what, extra) -> {
                releasePlayer();
                return true;
            });
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            releasePlayer();
        }
    }

    private void notifyRingingChanged() {
        if (onRingingChanged != null) {
            onRingingChanged.run();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        ringing = false;
        handler.removeCallbacks(timeout);
        releasePlayer();
        stopForeground(STOP_FOREGROUND_REMOVE);
        notifyRingingChanged();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
