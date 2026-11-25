package pl.grafikpracy;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ScrollView;

import java.util.function.IntConsumer;

final class SwipeScrollView extends ScrollView {
    private final IntConsumer onSwipe;
    private final float threshold;
    private float downX;
    private float downY;
    private boolean horizontal;
    private boolean vertical;

    SwipeScrollView(Context context, IntConsumer onSwipe) {
        super(context);
        this.onSwipe = onSwipe;
        threshold = 48 * context.getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            horizontal = false;
            vertical = false;
        }
        if (detectSwipe(event)) {
            return true;
        }
        boolean intercepted = super.onInterceptTouchEvent(event);
        vertical |= intercepted;
        return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (detectSwipe(event)) {
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel);
            cancel.recycle();
        }
        if (!horizontal) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP:
                float distance = event.getX() - downX;
                horizontal = false;
                if (Math.abs(distance) >= threshold
                        && Math.abs(distance) > 1.4f * Math.abs(event.getY() - downY)) {
                    onSwipe.accept(distance < 0 ? 1 : -1);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                horizontal = false;
                break;
            default:
                break;
        }
        return true;
    }

    private boolean detectSwipe(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            horizontal = false;
            vertical = true;
        }
        if (vertical || horizontal || event.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return false;
        }
        float x = Math.abs(event.getX() - downX);
        float y = Math.abs(event.getY() - downY);
        if (y > threshold / 3 && y > x) {
            vertical = true;
        }
        horizontal = x >= threshold && x > y * 1.4f;
        return horizontal;
    }
}
