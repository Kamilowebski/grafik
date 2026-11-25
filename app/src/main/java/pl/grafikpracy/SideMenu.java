package pl.grafikpracy;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class SideMenu extends FrameLayout {
    private final Ui ui;
    private final Runnable onClosed;
    private final View shade;
    private final LinearLayout panel;
    private final LinearLayout items;
    private boolean closing;

    SideMenu(Context context, Ui ui, Runnable onClosed) {
        super(context);
        this.ui = ui;
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        this.onClosed = onClosed;
        shade = new View(context);
        shade.setBackgroundColor(0x80191C3A);
        shade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        shade.setOnClickListener(view -> dismiss());
        addView(shade, new LayoutParams(-1, -1));

        panel = ui.column();
        panel.setBackgroundColor(Ui.WHITE);
        panel.setClickable(true);
        panel.setElevation(ui.dp(10));
        if (Build.VERSION.SDK_INT >= 28) {
            panel.setAccessibilityPaneTitle("Menu grafiku");
        }

        LinearLayout heading = ui.row();
        heading.setPadding(ui.dp(14), ui.dp(10), ui.dp(9), ui.dp(10));
        heading.setBackground(ui.weekBackground());
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.ic_grafik);
        heading.addView(logo, new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)));
        ui.gap(heading, 9);
        heading.addView(ui.text("Grafik pracy", 19, Ui.WHITE, true), ui.weight());
        ImageView close = new ImageView(context);
        close.setImageResource(R.drawable.drawer_close);
        close.setImageTintList(ColorStateList.valueOf(Ui.WHITE));
        close.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        close.setContentDescription("Zamknij menu");
        ui.action(close, 0x28FFFFFF, 12, 0, this::dismiss);
        heading.addView(close, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        LinearLayout.LayoutParams banner = new LinearLayout.LayoutParams(-1, -2);
        banner.setMargins(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(6));
        panel.addView(heading, banner);

        ScrollView itemScroll = new ScrollView(context);
        itemScroll.setFillViewport(true);
        itemScroll.setVerticalScrollBarEnabled(false);
        items = ui.column();
        items.setPadding(ui.dp(10), ui.dp(2), ui.dp(10), ui.dp(8));
        itemScroll.addView(items);
        panel.addView(itemScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    void add(int icon, String label, Runnable action) {
        LinearLayout row = ui.row();
        row.setPadding(ui.dp(7), ui.dp(2), ui.dp(8), ui.dp(2));
        row.setMinimumHeight(ui.dp(48));
        ImageView image = new ImageView(getContext());
        image.setImageResource(icon);
        image.setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(7));
        image.setBackground(ui.shape(Ui.TINT, 10, 0));
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(image, new LinearLayout.LayoutParams(ui.dp(35), ui.dp(35)));
        ui.gap(row, 11);
        row.addView(ui.text(label, 15, Ui.INK, true), ui.weight());
        row.setContentDescription(label);
        ui.action(row, 0, 12, 0, () -> dismiss(action));
        items.addView(row);
    }

    void divider() {
        View line = new View(getContext());
        line.setBackgroundColor(Ui.BORDER);
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, ui.dp(1));
        position.setMargins(ui.dp(7), ui.dp(5), ui.dp(7), ui.dp(5));
        items.addView(line, position);
    }

    void show(FrameLayout host) {
        int usableWidth = host.getWidth() - host.getPaddingLeft() - host.getPaddingRight();
        int width = Math.min(ui.dp(328), Math.max(1, usableWidth - ui.dp(40)));
        addView(panel, new LayoutParams(width, -1, Gravity.START));
        host.addView(this, new LayoutParams(-1, -1));
        if (ValueAnimator.areAnimatorsEnabled()) {
            panel.setTranslationX(-width);
            shade.setAlpha(0f);
            shade.animate().alpha(1f).setDuration(230).start();
            panel.animate().translationX(0f).setDuration(250)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    void dismiss() {
        dismiss(null);
    }

    private void dismiss(Runnable action) {
        if (closing) {
            return;
        }
        closing = true;
        Runnable remove = () -> {
            FrameLayout parent = (FrameLayout) getParent();
            if (parent != null) {
                parent.removeView(this);
                onClosed.run();
                if (action != null) {
                    parent.postOnAnimation(action);
                }
            }
        };
        shade.animate().cancel();
        panel.animate().cancel();
        if (!ValueAnimator.areAnimatorsEnabled() || panel.getWidth() == 0) {
            remove.run();
            return;
        }
        shade.animate().alpha(0f).setDuration(170).start();
        panel.animate().translationX(-panel.getWidth()).setDuration(190)
                .withEndAction(remove).start();
    }
}
