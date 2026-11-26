package pl.grafikpracy;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

final class Ui {
    static final int BACKGROUND = 0xFFF2F3FA;
    static final int INK = 0xFF18213B;
    static final int SECONDARY = 0xFF545C78;
    static final int ACCENT = 0xFF5039C3;
    static final int NAVY = 0xFF241858;
    static final int LAVENDER = 0xFFE3DDFF;
    static final int TINT = 0xFFEEEAFF;
    static final int WHITE = Color.WHITE;
    static final int BORDER = 0xFFE6E8F3;
    static final int LEAVE = 0xFF9E384F;
    static final int DUTY = 0xFF156C73;

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
            if (Build.VERSION.SDK_INT >= 28) {
                text.setTypeface(Typeface.create(Typeface.DEFAULT, size >= 19 ? 700 : 600, false));
            } else {
                text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
        }
        return text;
    }

    TextView heading(String label, int size, int color) {
        TextView heading = text(label, size, color, true);
        heading.setLetterSpacing(-0.028f);
        if (Build.VERSION.SDK_INT >= 28) {
            heading.setAccessibilityHeading(true);
        }
        return heading;
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
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x235039C3),
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
        action(button, primary ? ACCENT : TINT, 24, 0, action);
        return button;
    }

    ImageButton arrow(String label, boolean previous, boolean onDark, Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(R.drawable.week_chevron);
        button.setImageTintList(ColorStateList.valueOf(onDark ? WHITE : ACCENT));
        button.setScaleType(ImageView.ScaleType.CENTER);
        if (previous) {
            button.setScaleX(-1);
        }
        button.setContentDescription(label);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(onDark ? 0x40FFFFFF : 0x235039C3),
                shape(onDark ? 0x24FFFFFF : TINT, 24, 0), shape(WHITE, 24, 0));
        button.setBackground(new InsetDrawable(ripple, dp(5)));
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    void divider(LinearLayout column, int inset) {
        View line = new View(context);
        line.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(dp(inset), 0, dp(inset), 0);
        column.addView(line, params);
    }

    void field(EditText field) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, shape(TINT, 16, ACCENT));
        background.addState(new int[0], shape(BACKGROUND, 16, 0));
        field.setBackground(background);
        field.setMinHeight(dp(50));
        field.setPadding(dp(15), dp(8), dp(15), dp(8));
        field.setTextColor(INK);
        field.setTextSize(17);
    }

    Switch toggle(String label, boolean checked) {
        Switch toggle = new Switch(context);
        toggle.setText(label);
        toggle.setTextColor(INK);
        toggle.setTextSize(15);
        toggle.setMinHeight(dp(48));
        toggle.setSwitchPadding(dp(12));
        toggle.setChecked(checked);
        return toggle;
    }

    GradientDrawable weekBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{NAVY, 0xFF4835A4});
        drawable.setCornerRadius(dp(30));
        return drawable;
    }

    AlertDialog.Builder dialog() {
        return dialog(false);
    }

    AlertDialog.Builder sheet() {
        return dialog(true);
    }

    private AlertDialog.Builder dialog(boolean sheet) {
        return new AlertDialog.Builder(context) {
            @Override
            public AlertDialog create() {
                AlertDialog dialog = super.create();
                dialog.setOnShowListener(ignored -> styleDialog(dialog, sheet));
                return dialog;
            }
        };
    }

    void showPicker(AlertDialog picker) {
        picker.show();
        styleDialog(picker, false);
        picker.getButton(AlertDialog.BUTTON_POSITIVE).setText("Ustaw");
        picker.getButton(AlertDialog.BUTTON_NEGATIVE).setText("Anuluj");
    }

    private void styleDialog(AlertDialog dialog, boolean sheet) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new InsetDrawable(shape(WHITE, 28, 0), dp(12), dp(8), dp(12), dp(8)));
            window.setDimAmount(0.32f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (sheet) {
                window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                int width = Math.min(context.getResources().getDisplayMetrics().widthPixels, dp(540));
                window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        }
        for (int position : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(position);
            if (button != null) {
                boolean primary = position == AlertDialog.BUTTON_POSITIVE;
                button.setAllCaps(false);
                button.setTextColor(primary ? WHITE : ACCENT);
                button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x335039C3),
                        shape(primary ? ACCENT : WHITE, 22, 0), shape(WHITE, 22, 0)));
                button.setPadding(dp(14), dp(4), dp(14), dp(4));
            }
        }
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
