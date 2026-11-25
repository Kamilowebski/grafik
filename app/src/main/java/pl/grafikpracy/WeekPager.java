package pl.grafikpracy;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.function.Function;

final class WeekPager extends FrameLayout {
    private final Function<LocalDate, View> createPage;
    private final Consumer<LocalDate> onWeekChanged;
    private final int touchSlop;
    private LocalDate week;
    private LocalDate incomingWeek;
    private View current;
    private View incoming;
    private ValueAnimator animation;
    private float offset;
    private float downX;
    private float downY;
    private boolean dragging;
    private boolean vertical;

    WeekPager(Context context, LocalDate week, Function<LocalDate, View> createPage,
            Consumer<LocalDate> onWeekChanged) {
        super(context);
        this.week = week;
        this.createPage = createPage;
        this.onWeekChanged = onWeekChanged;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        current = createPage.apply(week);
        addView(current, pageLayout());
    }

    View currentPage() {
        return current;
    }

    void show(LocalDate target, Runnable after) {
        if (animation != null || dragging) {
            return;
        }
        if (week.equals(target)) {
            after.run();
            return;
        }
        prepare(target);
        settle(true, after);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            vertical = false;
            dragging = false;
        }
        if (animation != null) {
            return true;
        }
        if (startDrag(event)) {
            dragTo(event.getX());
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (animation != null) {
            return true;
        }
        if (!dragging && !startDrag(event)) {
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                dragTo(event.getX());
                break;
            case MotionEvent.ACTION_UP:
                dragTo(event.getX());
                dragging = false;
                settle(incoming != null && Math.abs(offset) >= getWidth() * 0.22f, () -> {});
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                dragging = false;
                vertical = true;
                settle(false, () -> {});
                break;
            default:
                break;
        }
        return true;
    }

    private boolean startDrag(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            vertical = true;
        }
        if (vertical || event.getActionMasked() != MotionEvent.ACTION_MOVE || event.getPointerCount() != 1) {
            return false;
        }
        float x = Math.abs(event.getX() - downX);
        float y = Math.abs(event.getY() - downY);
        if (y > touchSlop && y > x) {
            vertical = true;
        }
        dragging = !vertical && x > touchSlop && x > y * 1.3f;
        return dragging;
    }

    private void dragTo(float x) {
        float distance = x - downX;
        float moved = Math.max(0f, Math.abs(distance) - touchSlop);
        float position = Math.copySign(Math.min(moved, getWidth()), distance);
        if (moved > 0f) {
            prepare(week.plusWeeks(position < 0f ? 1 : -1));
        }
        positionPages(position);
    }

    private void prepare(LocalDate target) {
        if (target.equals(incomingWeek)) {
            return;
        }
        discardIncoming();
        incomingWeek = target;
        incoming = createPage.apply(target);
        incoming.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(incoming, pageLayout());
        positionPages(offset);
    }

    private void positionPages(float newOffset) {
        offset = newOffset;
        current.setTranslationX(newOffset);
        if (incoming != null) {
            int direction = incomingWeek.isAfter(week) ? 1 : -1;
            incoming.setTranslationX(newOffset + direction * getWidth());
        }
    }

    private void settle(boolean commit, Runnable after) {
        if (incoming == null) {
            positionPages(0f);
            return;
        }
        float target = commit ? (incomingWeek.isAfter(week) ? -getWidth() : getWidth()) : 0f;
        if (target == offset || !ValueAnimator.areAnimatorsEnabled()) {
            finish(commit, after);
            return;
        }
        animation = ValueAnimator.ofFloat(offset, target);
        animation.setDuration(commit ? 250 : 190);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(value -> positionPages((float) value.getAnimatedValue()));
        animation.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animator) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                animation = null;
                if (!cancelled) {
                    finish(commit, after);
                }
            }
        });
        animation.start();
    }

    private void finish(boolean commit, Runnable after) {
        if (commit) {
            removeView(current);
            current = incoming;
            current.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            week = incomingWeek;
            incoming = null;
            incomingWeek = null;
            onWeekChanged.accept(week);
            positionPages(0f);
            current.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            after.run();
        } else {
            discardIncoming();
            positionPages(0f);
        }
    }

    private void discardIncoming() {
        if (incoming != null) {
            removeView(incoming);
            incoming = null;
            incomingWeek = null;
        }
    }

    private LayoutParams pageLayout() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animation != null) {
            animation.cancel();
        }
        super.onDetachedFromWindow();
    }
}
