package com.ryan.rokidardemo.base;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.ryan.rokidardemo.utils.Logger;
import com.ryan.rokidardemo.vuforia.ArRenderer;

public class BaseActivity extends Activity {

    private DisplayManager.DisplayListener mDisplayListener;
    private DisplayManager mDisplayManager;

    private int mDeviceOrientation;

    protected ArRenderer mBaseRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideStatusNavigationBar();

        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
        {
            mDisplayListener = new DisplayManager.DisplayListener()
            {
                @Override
                public void onDisplayAdded(int displayId)
                {
                    Logger.d("BaseActivity: onDisplayAdded");
                }

                @Override
                public void onDisplayChanged(int displayId)
                {
                    int newOrientation = getDeviceOrientation();
                    Logger.d("BaseActivity: onDisplayChanged newOrientation="+newOrientation+", mDeviceOrientation="+mDeviceOrientation);
                    if (mDeviceOrientation != newOrientation)
                    {
                        // onSurfaceChanged() does not get called when switching from
                        // portrait to reverse portrait or landscape to reverse landscape,
                        // so we must handle this explicitly here
                        // For upside down rotation, the difference in orientation values will be 2
                        // ie: Surface.ROTATION_0 has an enum of 0 and Surface.ROTATION_180 has an enum of 2
                        boolean isUpsideDownRotation = Math.abs(mDeviceOrientation - newOrientation) == 2;

                        if (isUpsideDownRotation)
                        {
                            if (mBaseRenderer != null)
                            {
                                //mBaseRenderer.onConfigurationChanged();
                            }
                        }

                        mDeviceOrientation = newOrientation;
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId)
                {

                }
            };

            mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        }
    }


    @Override
    protected void onResume()
    {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && mDisplayManager != null)
        {
            mDisplayManager.registerDisplayListener(mDisplayListener, null);
        }
    }


    @Override
    protected void onPause()
    {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && mDisplayManager != null)
        {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
    }


    private int getDeviceOrientation()
    {
        return getWindowManager().getDefaultDisplay().getRotation();
    }


    public void setRendererReference(ArRenderer renderer)
    {
        mBaseRenderer = renderer;
    }

    private void hideStatusNavigationBar(){
        if(Build.VERSION.SDK_INT<16){
            this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }else{
            int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN //hide statusBar
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION; //hide navigationBar
            getWindow().getDecorView().setSystemUiVisibility(uiFlags);
        }
    }
}
