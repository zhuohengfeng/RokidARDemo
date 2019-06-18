package com.ryan.rokidardemo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ryan.rokidardemo.base.BaseActivity;
import com.ryan.rokidardemo.utils.Logger;
import com.ryan.rokidardemo.vuforia.ArException;
import com.ryan.rokidardemo.vuforia.ArManager;
import com.ryan.rokidardemo.vuforia.IArControl;
import com.ryan.rokidardemo.vuforia.utils.LoadingDialogHandler;
import com.ryan.rokidardemo.vuforia.utils.SampleApplicationGLView;
import com.ryan.rokidardemo.vuforia.utils.Texture;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.DeviceTracker;
import com.vuforia.ObjectTracker;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.TrackableList;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import org.rajawali3d.view.SurfaceView;

import java.util.ArrayList;
import java.util.Vector;

public class ImageTargetActivity extends BaseActivity implements IArControl {

    // 控制vuforia的生命周期
    private ArManager mArManager;
    private DataSet mCurrentDataset;

    private int mCurrentDatasetSelectionIndex = 0;
    private final ArrayList<String> mDatasetStrings = new ArrayList<>();

//    private SampleApplicationGLView mGlView;
    private SurfaceView mGlView;

    private ImageTargetRenderer mRenderer;

    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    private RelativeLayout mUILayout;

    final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);

    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mArManager = new ArManager(this);

        startLoadingAnimation();
        // 模型信息
        mDatasetStrings.add("StonesAndChips.xml");
        mDatasetStrings.add("Tarmac.xml");

        // 开始初始化
        mArManager.initAR(this, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Load any sample specific textures:
        mTextures = new Vector<>();
        loadTextures();
    }


    // Load specific textures from the APK, which we will later use for rendering.
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotBrass.png",
                getAssets()));
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotBlue.png",
                getAssets()));
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotRed.png",
                getAssets()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        showProgressIndicator(true);
        mArManager.onResume();
    }

    // Called whenever the device orientation or screen resolution changes
    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mArManager.onConfigurationChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        mArManager.onPause();
    }


    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        try
        {
            mArManager.stopAR();
        } catch (ArException e)
        {
            Logger.e( e.getString());
        }

        // Unload texture:
        mTextures.clear();
        mTextures = null;
        System.gc();
    }

    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        mGlView = new SurfaceView(getApplicationContext());
//        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new ImageTargetRenderer(this, mArManager);
        mRenderer.setTextures(mTextures);
        mGlView.setSurfaceRenderer(mRenderer);
//        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
    }


    private void startLoadingAnimation()
    {
        mUILayout = (RelativeLayout) View.inflate(getApplicationContext(), R.layout.camera_overlay, null);

        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
                .findViewById(R.id.loading_indicator);

        // Shows the loading indicator at start
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        // Adds the inflated layout to the view
        addContentView(mUILayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }



    /** ------------SampleApplicationControl start-------------------*/
    /**
     * 初始化Trackers
     * @return
     */
    @Override
    public boolean doInitTrackers() {
        // Indicate if the trackers were initialized correctly
        boolean result = true;
        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null)
        {
            Logger.e("Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else
        {
            Logger.d("Tracker successfully initialized");
        }

        // Initialize the Positional Device Tracker
        DeviceTracker deviceTracker = (PositionalDeviceTracker)
                tManager.initTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null)
        {
            Logger.d("Successfully initialized Device Tracker");
        }
        else
        {
            Logger.e("Failed to initialize Device Tracker");
        }

        return result;
    }

    /**
     * 开始加载模型
     * @return
     */
    @Override
    public boolean doLoadTrackersData() {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
        {
            return false;
        }

        if (mCurrentDataset == null)
        {
            mCurrentDataset = objectTracker.createDataSet();
        }

        if (mCurrentDataset == null)
        {
            return false;
        }

        if (!mCurrentDataset.load(
                mDatasetStrings.get(mCurrentDatasetSelectionIndex),
                STORAGE_TYPE.STORAGE_APPRESOURCE))
        {
            return false;
        }

        if (!objectTracker.activateDataSet(mCurrentDataset))
        {
            return false;
        }

        TrackableList trackableList = mCurrentDataset.getTrackables();
        for (Trackable trackable : trackableList)
        {
            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);
            Logger.d("UserData:Set the following user data "
                    + trackable.getUserData());
        }

        return true;
    }

    /**
     * 卸载模型
     * @return
     */
    @Override
    public boolean doUnloadTrackersData() {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
                .getTracker(ObjectTracker.getClassType());

        if (objectTracker == null)
        {
            return false;
        }

        if (mCurrentDataset != null && mCurrentDataset.isActive())
        {
            if (objectTracker.getActiveDataSets().at(0).equals(mCurrentDataset)
                    && !objectTracker.deactivateDataSet(mCurrentDataset))
            {
                result = false;
            }
            else if (!objectTracker.destroyDataSet(mCurrentDataset))
            {
                result = false;
            }

            mCurrentDataset = null;
        }

        return result;
    }

    /**
     * 开始识别
     * @return
     */
    @Override
    public boolean doStartTrackers() {
        // Indicate if the trackers were started correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker != null && objectTracker.start())
        {
            Logger.d("Successfully started Object Tracker");
        }
        else
        {
            Logger.e("Failed to start Object Tracker");
            result = false;
        }

        return result;
    }

    /**
     * 停止识别
     * @return
     */
    @Override
    public boolean doStopTrackers() {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker != null)
        {
            objectTracker.stop();
            Logger.d("Successfully stopped object tracker");
        }
        else
        {
            Logger.e("Failed to stop object tracker");
            result = false;
        }

        return result;
    }

    @Override
    public boolean doDeinitTrackers() {
        TrackerManager tManager = TrackerManager.getInstance();

        // Indicate if the trackers were deinitialized correctly
        boolean result = tManager.deinitTracker(ObjectTracker.getClassType());
        tManager.deinitTracker(PositionalDeviceTracker.getClassType());

        return result;
    }

    // Called once Vuforia has been initialized or
    // an error has caused Vuforia initialization to stop
    @Override
    public void onInitARDone(ArException exception) {
        if (exception == null)
        {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            mArManager.startAR();
        }
        else
        {
            Logger.e( exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
    }

    @Override
    public void onVuforiaUpdate(State state) {
    }

    @Override
    public void onVuforiaResumed() {
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }

    @Override
    public void onVuforiaStarted() {
        mRenderer.updateRenderingPrimitives();
        showProgressIndicator(false);
    }
    /** ------------SampleApplicationControl end-------------------*/

    private void showProgressIndicator(boolean show)
    {
        if (show)
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        }
        else
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }


    private void showInitializationErrorMessage(String message)
    {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        ImageTargetActivity.this);
                builder
                        .setMessage(errorMessage)
                        .setTitle(getString(R.string.INIT_ERROR))
                        .setCancelable(false)
                        .setIcon(0)
                        .setPositiveButton("ok",
                                new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int id)
                                    {
                                        finish();
                                    }
                                });

                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }


}
