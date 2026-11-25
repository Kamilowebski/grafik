package pl.grafikpracy;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;

public final class AlarmActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            if (AlarmService.ringing) {
                handler.postDelayed(this, 500);
            } else {
                finish();
            }
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        display(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        display(intent);
    }

    private void display(Intent intent) {
        Ui ui = new Ui(this);
        LinearLayout content = ui.column();
        content.setGravity(Gravity.CENTER);
        content.setBackgroundColor(Ui.BACKGROUND);
        content.setPadding(ui.dp(28), ui.dp(32), ui.dp(28), ui.dp(32));
        content.addView(ui.text("Czas wstać", 36, Ui.INK, true));
        ui.space(content, 12);
        content.addView(ui.text("Zmiana zaczyna się o " + intent.getStringExtra("start"), 19, Ui.SECONDARY, false));
        ui.space(content, 28);
        content.addView(ui.button("Wyłącz budzik", true, () -> {
            stopService(new Intent(this, AlarmService.class));
            finish();
        }));
        setContentView(content);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(check);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(check);
        super.onStop();
    }
}
