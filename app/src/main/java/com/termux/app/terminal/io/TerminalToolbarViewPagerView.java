package com.termux.app.terminal.io;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

/**
 * A {@link ViewPager} for the terminal toolbar that additionally turns a rightward swipe on the
 * extra keys page (page 0) into an "open sidebar drawer" gesture. Leftward swipes keep the default
 * behaviour of paging to the text input box (page 1).
 *
 * Touch events are observed via {@link #dispatchTouchEvent} (not {@link #onTouchEvent}) so that the
 * gesture detector still sees ACTION_DOWN even when the press lands on one of the extra key buttons
 * that consumes touch events.
 */
public class TerminalToolbarViewPagerView extends ViewPager {

    private static final int EXTRA_KEYS_PAGE = 0;

    @Nullable
    private DrawerLayout mDrawer;

    private final GestureDetector mGestureDetector;

    public TerminalToolbarViewPagerView(Context context) {
        this(context, null);
    }

    public TerminalToolbarViewPagerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (getCurrentItem() != EXTRA_KEYS_PAGE || mDrawer == null) return false;
                // Don't fight the copy-mode lock that keeps the drawer closed.
                if (mDrawer.getDrawerLockMode(Gravity.LEFT) == DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    return false;
                float dx = e2.getX() - e1.getX();
                // Only react to a clearly horizontal rightward swipe; vertical swipes keep their
                // existing role (e.g. swipe-up key popups) and the default paging behaviour is
                // preserved for the opposite direction.
                if (dx < ViewConfiguration.get(getContext()).getScaledTouchSlop()) return false;
                if (Math.abs(velocityX) <= Math.abs(velocityY)) return false;
                mDrawer.openDrawer(Gravity.LEFT);
                return true;
            }
        });
    }

    public void setDrawer(@Nullable DrawerLayout drawer) {
        mDrawer = drawer;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mGestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }
}
