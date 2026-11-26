package pl.grafikpracy;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setBackground(ui.weekBackground());
        LinearLayout content = ui.column();
        content.setGravity(Gravity.CENTER);
        content.setPadding(ui.dp(28), ui.dp(42), ui.dp(28), ui.dp(42));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.drawer_alarm);
        icon.setColorFilter(Ui.WHITE);
        icon.setBackground(ui.shape(0x28FFFFFF, 40, 0));
        icon.setPadding(ui.dp(17), ui.dp(17), ui.dp(17), ui.dp(17));
        content.addView(icon, new LinearLayout.LayoutParams(ui.dp(66), ui.dp(66)));
        ui.space(content, 16);
        TextView heading = ui.heading("Czas wstać", 34, Ui.WHITE);
        heading.setGravity(Gravity.CENTER);
        content.addView(heading);

        String start = intent.getStringExtra("start");
        if (start != null && !start.isEmpty()) {
            ui.space(content, 15);
            TextView caption = ui.text("Początek zmiany", 15, Ui.LAVENDER, false);
            caption.setGravity(Gravity.CENTER);
            content.addView(caption);
            TextView time = ui.heading(start, 52, Ui.WHITE);
            time.setGravity(Gravity.CENTER);
            content.addView(time);
        }
        ui.space(content, 28);
        TextView dismiss = ui.button("Wyłącz budzik", false, () -> {
            stopService(new Intent(this, AlarmService.class));
            finish();
        });
        dismiss.setMinHeight(ui.dp(54));
        content.addView(dismiss, new LinearLayout.LayoutParams(-1, -2));
        page.addView(content);
        setContentView(page);
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
