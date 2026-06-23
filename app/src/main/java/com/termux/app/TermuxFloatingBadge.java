package com.termux.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.shared.logger.Logger;

public class TermuxFloatingBadge {

    private static final String PREFERENCES_NAME = "termux_floating_badge";
    private static final String KEY_WINDOW_X = "window_x";
    private static final String KEY_WINDOW_Y = "window_y";
    private static final int DEFAULT_X_DP = 16;
    private static final int DEFAULT_Y_DP = 160;
    private static final int BADGE_SIZE_DP = 48;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final SharedPreferences mPreferences;
    private final int mTouchSlop;

    private View mBadgeView;
    private WindowManager.LayoutParams mLayoutParams;

    private static final String LOG_TAG = "TermuxFloatingBadge";

    public TermuxFloatingBadge(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mPreferences = mContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        mTouchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
    }

    public boolean show() {
        if (isShowing())
            return true;

        try {
            mBadgeView = buildBadgeView();
            mLayoutParams = buildLayoutParams();
            mWindowManager.addView(mBadgeView, mLayoutParams);
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to show floating badge", e);
            mBadgeView = null;
            mLayoutParams = null;
            return false;
        }
    }

    public void hide() {
        if (!isShowing())
            return;

        try {
            mWindowManager.removeView(mBadgeView);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to hide floating badge", e);
        } finally {
            mBadgeView = null;
            mLayoutParams = null;
        }
    }

    public boolean isShowing() {
        return mBadgeView != null;
    }

    private View buildBadgeView() {
        TextView badgeView = new TextView(mContext);
        badgeView.setText("T");
        badgeView.setTextColor(Color.WHITE);
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        badgeView.setTypeface(Typeface.DEFAULT_BOLD);
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setContentDescription(mContext.getString(R.string.floating_badge_content_description));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xE6263238);
        background.setStroke(dp(1), 0x99FFFFFF);
        badgeView.setBackground(background);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            badgeView.setElevation(dp(6));

        badgeView.setOnClickListener(v -> openTermux());
        badgeView.setOnTouchListener(new BadgeTouchListener());
        return badgeView;
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        int size = dp(BADGE_SIZE_DP);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = mPreferences.getInt(KEY_WINDOW_X, dp(DEFAULT_X_DP));
        layoutParams.y = mPreferences.getInt(KEY_WINDOW_Y, dp(DEFAULT_Y_DP));
        return layoutParams;
    }

    private void openTermux() {
        TermuxActivity.startTermuxActivity(mContext);
    }

    private void savePosition() {
        if (mLayoutParams == null)
            return;

        mPreferences.edit()
            .putInt(KEY_WINDOW_X, mLayoutParams.x)
            .putInt(KEY_WINDOW_Y, mLayoutParams.y)
            .apply();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            mContext.getResources().getDisplayMetrics()));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private class BadgeTouchListener implements View.OnTouchListener {

        private int mStartX;
        private int mStartY;
        private float mStartRawX;
        private float mStartRawY;
        private boolean mMoved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mStartX = mLayoutParams.x;
                    mStartY = mLayoutParams.y;
                    mStartRawX = event.getRawX();
                    mStartRawY = event.getRawY();
                    mMoved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - mStartRawX;
                    float deltaY = event.getRawY() - mStartRawY;
                    if (!mMoved && Math.hypot(deltaX, deltaY) > mTouchSlop)
                        mMoved = true;
                    if (mMoved)
                        moveTo(mStartX + Math.round(deltaX), mStartY + Math.round(deltaY));
                    return true;
                case MotionEvent.ACTION_UP:
                    if (mMoved)
                        savePosition();
                    else
                        view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (mMoved)
                        savePosition();
                    return true;
                default:
                    return true;
            }
        }

        private void moveTo(int x, int y) {
            if (mBadgeView == null || mLayoutParams == null)
                return;

            DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
            mLayoutParams.x = clamp(x, 0, Math.max(0, displayMetrics.widthPixels - mLayoutParams.width));
            mLayoutParams.y = clamp(y, 0, Math.max(0, displayMetrics.heightPixels - mLayoutParams.height));
            try {
                mWindowManager.updateViewLayout(mBadgeView, mLayoutParams);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to move floating badge", e);
            }
        }
    }

}
