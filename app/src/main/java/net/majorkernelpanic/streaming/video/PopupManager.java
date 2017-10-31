package net.majorkernelpanic.streaming.video;

/**
 * Created by dengfengwang on 17-10-26.
 */
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import net.majorkernelpanic.spydroid.R;
import net.majorkernelpanic.spydroid.SpydroidApplication;
import net.majorkernelpanic.spydroid.ui.PopupLayout;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.rtsp.RtspServer;


public class PopupManager implements RtspServer.CallbackListener,GestureDetector.OnDoubleTapListener, View.OnClickListener, GestureDetector.OnGestureListener {

    private static final String TAG ="PopupManager";

    private static final int FLING_STOP_VELOCITY = 3000;
    private static final int MSG_DELAY = 3000;

    private static final int SHOW_BUTTONS = 0;
    private static final int HIDE_BUTTONS = 1;

    private RtspServer mService;

    private PopupLayout mRootView;
    private ImageView mExpandButton;
    private ImageView mCloseButton;
    private net.majorkernelpanic.streaming.gl.SurfaceView mSurfaceView,mSurfaceView1;

    public PopupManager(RtspServer service) {
        mService = service;
    }

    public void removePopup() {
        Log.d(TAG,"removePopup......");
        if (mRootView == null)
            return;
        mService.removeCallbackListener(this);
        android.os.Process.killProcess(android.os.Process.myPid());    //获取PID
        System.exit(0);   //常规java、c#的标准退出法，返回值为0代表正常退出

        mRootView.close();
        mRootView = null;
    }

    public void showPopup() {
        Log.d(TAG,"showPopup......");
        mService.addCallbackListener(this);
        LayoutInflater li = (LayoutInflater) SpydroidApplication.getInstance().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRootView = (PopupLayout) li.inflate(R.layout.popup, null);
        mCloseButton = (ImageView) mRootView.findViewById(R.id.popup_close);
        mExpandButton = (ImageView) mRootView.findViewById(R.id.popup_expand);
        mSurfaceView1 = (net.majorkernelpanic.streaming.gl.SurfaceView)mRootView.findViewById(R.id.player_surface_second);
        SessionBuilder.getInstance().setSurfaceView(mSurfaceView1);
        SessionBuilder.getInstance().setPreviewOrientation(90);
        Log.d(TAG,"mservice......."+ mService);
        mCloseButton.setOnClickListener(this);
        mExpandButton.setOnClickListener(this);

        GestureDetectorCompat gestureDetector = new GestureDetectorCompat(mService, this);
        gestureDetector.setOnDoubleTapListener(this);
        mRootView.setGestureDetector(gestureDetector);
        mService.start();


    }

    public void removePopup1() {
        Log.d(TAG,"removePopup......");
        if (mRootView == null)
            return;
        mService.removeCallbackListener(this);

        mRootView.close();
        mRootView = null;
    }

    public void showPopup1() {
        Log.d(TAG,"showPopup......");
        mService.addCallbackListener(this);
        LayoutInflater li = (LayoutInflater) SpydroidApplication.getInstance().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRootView = (PopupLayout) li.inflate(R.layout.popup, null);
        mCloseButton = (ImageView) mRootView.findViewById(R.id.popup_close);
        mExpandButton = (ImageView) mRootView.findViewById(R.id.popup_expand);
        mSurfaceView = (net.majorkernelpanic.streaming.gl.SurfaceView)mRootView.findViewById(R.id.player_surface);
        SessionBuilder.getInstance_back().setSurfaceView(mSurfaceView);
        SessionBuilder.getInstance_back().setPreviewOrientation(90);
        Log.d(TAG,"mservice......."+ mService);
        mCloseButton.setOnClickListener(this);
        mExpandButton.setOnClickListener(this);

        GestureDetectorCompat gestureDetector = new GestureDetectorCompat(mService, this);
        gestureDetector.setOnDoubleTapListener(this);
        mRootView.setGestureDetector(gestureDetector);
        mService.start();

    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        Log.d(TAG,"onSingleTapConfirmed.......");
        mHandler.sendEmptyMessage(SHOW_BUTTONS);
        mHandler.sendEmptyMessageDelayed(HIDE_BUTTONS, MSG_DELAY);
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        Log.d(TAG,"onDoubleTap.......");
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {}

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {}

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        Log.d(TAG,"onFling.........");
        if (Math.abs(velocityX) > FLING_STOP_VELOCITY || velocityY > FLING_STOP_VELOCITY) {
            return true;
        }
        return false;
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_BUTTONS:
                    mCloseButton.setVisibility(View.VISIBLE);
                    mExpandButton.setVisibility(View.VISIBLE);
                    break;
                case HIDE_BUTTONS:
                    mCloseButton.setVisibility(View.GONE);
                    mExpandButton.setVisibility(View.GONE);
                    break;
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.popup_close:
                Log.d(TAG,"popup_close......");
                removePopup();
                break;
            case R.id.popup_expand:
                Log.d(TAG,"popup_expand......");
                break;
        }
    }

    @Override
    public void onError(RtspServer server, Exception e, int error) {

    }

    @Override
    public void onMessage(RtspServer server, int message) {

    }
}
