package pl.grafikpracy;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BACKGROUND = Color.rgb(245, 246, 252);
    static final int INK = Color.rgb(25, 28, 58);
    static final int SECONDARY = Color.rgb(80, 86, 115);
    static final int ACCENT = Color.rgb(73, 57, 168);
    static final int NAVY = Color.rgb(31, 32, 75);
    static final int LAVENDER = Color.rgb(228, 222, 255);
    static final int TINT = Color.rgb(236, 232, 255);
    static final int WHITE = Color.WHITE;
    static final int BORDER = Color.rgb(225, 226, 240);

    private final Context context;

    Ui(Context context) {
        this.context = context;
    }

    int dp(int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    LinearLayout row() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    TextView text(String label, int size, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) {
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return text;
    }

    GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (stroke != 0) {
            shape.setStroke(dp(1), stroke);
        }
        return shape;
    }

    void action(View view, int color, int radius, int stroke, Runnable action) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x294939A8),
                shape(color, radius, stroke), shape(WHITE, radius, 0)));
        view.setOnClickListener(ignored -> action.run());
        view.setFocusable(true);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
    }

    TextView button(String label, boolean primary, Runnable action) {
        TextView button = text(label, 15, primary ? WHITE : ACCENT, true);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        action(button, primary ? ACCENT : TINT, 14, 0, action);
        return button;
    }

    GradientDrawable weekBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{NAVY, ACCENT});
        drawable.setCornerRadius(dp(26));
        return drawable;
    }

    void enter(View view, int horizontal, int vertical) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(0.55f);
        view.setTranslationX(dp(horizontal));
        view.setTranslationY(dp(vertical));
        view.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    void space(LinearLayout layout, int height) {
        layout.addView(new View(context), new LinearLayout.LayoutParams(1, dp(height)));
    }

    void gap(LinearLayout layout, int width) {
        layout.addView(new View(context), new LinearLayout.LayoutParams(dp(width), 1));
    }

    LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }
}
