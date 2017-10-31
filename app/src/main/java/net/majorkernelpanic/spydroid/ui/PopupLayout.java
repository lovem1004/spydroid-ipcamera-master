/*
 * ************************************************************************
 *  PopupView.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package net.majorkernelpanic.spydroid.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import net.majorkernelpanic.spydroid.R;
import net.majorkernelpanic.spydroid.SpydroidApplication;

public class PopupLayout extends RelativeLayout implements ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {
    private static final String TAG = "PopupView";

    private WindowManager mWindowManager;
    private GestureDetectorCompat mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private double mScaleFactor = 1.d;
    private int mPopupWidth, mPopupHeight;
    private int mScreenWidth, mScreenHeight;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    WindowManager.LayoutParams mLayoutParams;

    public PopupLayout(Context context) {
        super(context);
        init(context);
    }

    public PopupLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PopupLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /*
     * Remove layout from window manager
     */
    public void close() {
        setKeepScreenOn(false);
        mWindowManager.removeView(this);
        mWindowManager = null;
    }

    public void setGestureDetector(GestureDetectorCompat gdc) {
        mGestureDetector = gdc;
    }

    /*
     * Update layout dimensions and apply layout params to window manager
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    public void setViewSize(int width, int height) {
        if (width > mScreenWidth) {
            height = height * mScreenWidth / width;
            width = mScreenWidth;
        }
        if (height > mScreenHeight){
            width = width * mScreenHeight / height;
            height = mScreenHeight;
        }
        containInScreen(width, height);
        mLayoutParams.width = width;
        mLayoutParams.height = height;
        mWindowManager.updateViewLayout(this, mLayoutParams);
    }

    @SuppressWarnings("deprecation")
    private void init(Context context) {
        Log.d(TAG,"init.........");
        mWindowManager = (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                270,
                480,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE);

        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.x = 50;
        params.y = 50;
        if (isHoneycombOrLater())
            mScaleGestureDetector = new ScaleGestureDetector(context, this);
        setOnTouchListener(this);
        mWindowManager.addView(this, params);
        mLayoutParams = (WindowManager.LayoutParams)getLayoutParams();

        updateWindowSize();
    }

    private void updateWindowSize() {
        Log.d(TAG,"updateWindowSize.........");
        if (isHoneycombMr2OrLater()) {
            Point size = new Point();
            mWindowManager.getDefaultDisplay().getSize(size);
            mScreenWidth = size.x;
            mScreenHeight = size.y;
        } else {
            mScreenWidth = mWindowManager.getDefaultDisplay().getWidth();
            mScreenHeight = mWindowManager.getDefaultDisplay().getHeight();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mWindowManager == null)
            return false;
        if (mScaleGestureDetector != null)
            mScaleGestureDetector.onTouchEvent(event);
        if (mGestureDetector != null && mGestureDetector.onTouchEvent(event))
            return true;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = mLayoutParams.x;
                initialY = mLayoutParams.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                updateWindowSize();
                return true;
            case MotionEvent.ACTION_UP:
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mScaleGestureDetector == null || !mScaleGestureDetector.isInProgress()) {
                    mLayoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                    mLayoutParams.y = initialY - (int) (event.getRawY() - initialTouchY);
                    containInScreen(mLayoutParams.width, mLayoutParams.height);
                    mWindowManager.updateViewLayout(PopupLayout.this, mLayoutParams);
                    return true;
                }
        }
        return false;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        Log.d(TAG,"onScale.........");
        mScaleFactor *= detector.getScaleFactor();

        mScaleFactor = Math.max(0.1d, Math.min(mScaleFactor, 5.0d));
        mPopupWidth = (int) (getWidth()*mScaleFactor);
        mPopupHeight = (int) (getHeight()*mScaleFactor);
        return true;
    }
    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        lp.width *= mScaleFactor;
        lp.height *= mScaleFactor;
        setViewSize(mPopupWidth, mPopupHeight);
        mScaleFactor = 1.0d;
    }

    private void containInScreen(int width, int height) {

        mLayoutParams.x = Math.max(mLayoutParams.x, 0);
        mLayoutParams.y = Math.max(mLayoutParams.y, 0);
        if (mLayoutParams.x + width > mScreenWidth)
            mLayoutParams.x = mScreenWidth - width;
        if (mLayoutParams.y + height > mScreenHeight)
            mLayoutParams.y = mScreenHeight - height;
    }

    public static boolean isHoneycombMr2OrLater() {
        return Build.VERSION.SDK_INT >= 13;
    }
    public static boolean isHoneycombOrLater() {
        return Build.VERSION.SDK_INT >= 11;
    }
}
