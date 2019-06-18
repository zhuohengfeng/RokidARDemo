package com.ryan.rokidardemo.vuforia;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.ryan.rokidardemo.utils.Logger;
import com.ryan.rokidardemo.vuforia.utils.SampleUtils;
import com.ryan.rokidardemo.vuforia.utils.Texture;
import com.ryan.rokidardemo.vuforia.utils.VideoBackgroundShader;
import com.vuforia.CameraCalibration;
import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.GLTextureUnit;
import com.vuforia.Matrix34F;
import com.vuforia.Mesh;
import com.vuforia.Renderer;
import com.vuforia.RenderingPrimitives;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackerManager;
import com.vuforia.VIEW;
import com.vuforia.Vec2F;
import com.vuforia.Vec2I;
import com.vuforia.Vec4I;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.ViewList;

import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.renderer.RenderTarget;

import java.lang.ref.WeakReference;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Vuforia的渲染Renderer
 * 这个类主要是负责绘制相机背景的初始化等，并调用ImageTargetRender的renderFrame来绘制模型(如果识别到)和相机预览
 */
public abstract class ArRenderer extends org.rajawali3d.renderer.Renderer {
    private static final String LOGTAG = "SampleAppRenderer";

    private RenderingPrimitives mRenderingPrimitives = null;
    private WeakReference<Activity> mActivityRef;

    private int mVideoMode;

    private Renderer mRenderer;
    private int currentView = VIEW.VIEW_SINGULAR;
    private float mNearPlane = -1.0f;
    private float mFarPlane = -1.0f;

    private GLTextureUnit videoBackgroundTex = null;

    // Shader user to render the video background on AR mode
    private int vbShaderProgramID = 0;
    private int vbTexSampler2DHandle = 0;
    private int vbVertexHandle = 0;
    private int vbTexCoordHandle = 0;
    private int vbProjectionMatrixHandle = 0;

    // Display size of the device:
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    // Stores orientation
    private boolean mIsPortrait = false;
    private boolean mIsActive = false;

    private boolean mIsRenderingInit = false;

    // -----------for Rajawali ------------
    private   Vector3      mPosition;
    private   Quaternion   mOrientation;
    protected ScreenQuad mBackgroundQuad;
    protected RenderTarget mBackgroundRenderTarget;
    private   double[]     mModelViewMatrix;
    private int mI = 0;

//    protected ArRenderer mArRenderer;
    protected ArManager mArManager;
    protected Vector<Texture> mTextures;

    // This method must be implemented by the Renderer class that handles the content rendering.
    // This function is called for each view inside of a loop
    abstract protected void renderFrame(State state, float[] projectionMatrix);

    // Initializes shaders
    abstract protected void initRendering();

    abstract protected void onFoundImageMarker(String trackableName, Vector3 position, Quaternion orientation);
    // ------------------------------------


    public ArRenderer(Activity activity,
                             int deviceMode, int videoMode, boolean stereo,
                             float nearPlane, float farPlane)
    {
        super(activity);

        mActivityRef = new WeakReference<>(activity);

        mRenderer = Renderer.getInstance();

        if(farPlane < nearPlane)
        {
            Log.e(LOGTAG, "Far plane should be greater than near plane");
            throw new IllegalArgumentException();
        }

        // -----------------------------
        mPosition = new Vector3();
        mOrientation = new Quaternion();
        mModelViewMatrix = new double[16];
        setNearFarPlanes(nearPlane, farPlane);
        // -----------------------------

        if(deviceMode != Device.MODE.MODE_AR && deviceMode != Device.MODE.MODE_VR)
        {
            Log.e(LOGTAG, "Device mode should be Device.MODE.MODE_AR or Device.MODE.MODE_VR");
            throw new IllegalArgumentException();
        }

        Device device = Device.getInstance();
        device.setViewerActive(stereo);  // Indicates if the app will be using a viewer, stereo mode and initializes the rendering primitives
        device.setMode(deviceMode);  // Select if we will be in AR or VR mode

        mVideoMode = videoMode;
    }


    public void onSurfaceCreated()
    {
        initArRendering();
    }

    private double getFOV(CameraCalibration cameraCalibration) {
        Vec2F size = cameraCalibration.getSize();
        Vec2F focalLength = cameraCalibration.getFocalLength();
        double fovRadians = 2 * Math.atan(0.5f * size.getData()[1] / focalLength.getData()[1]);
        double fovDegrees = fovRadians * 180.0f / M_PI;
        Logger.d("getFOV="+fovDegrees);
        return fovDegrees;
    }

    private int getVideoWidth() {
        Logger.d("getVideoWidth="+mVideoWidth);
        return mVideoWidth;
    }

    private int getVideoHeight() {
        Logger.d("getVideoHeight="+mVideoHeight);
        return mVideoHeight;
    }

    // Called whenever the device orientation or screen resolution changes
    // and we need to update the rendering primitives
    public void onConfigurationChanged(int width, int height)
    {
        updateActivityOrientation();
        storeScreenDimensions();

        // 初始化绘制camera预览
        configureVideoBackground();
        updateRenderingPrimitives();

        // 初始化模型renderer
        if (!mIsRenderingInit)
        {
            this.initRendering();

            //------------------------------
            if(mBackgroundRenderTarget == null) {
                mBackgroundRenderTarget = new RenderTarget("rajVuforia", width, height);

                addRenderTarget(mBackgroundRenderTarget);
                Material material = new Material();
                material.setColorInfluence(0);
                try {
                    material.addTexture(mBackgroundRenderTarget.getTexture());
                } catch (ATexture.TextureException e) {
                    e.printStackTrace();
                }

                mBackgroundQuad = new ScreenQuad();
                if(mArManager.getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    mBackgroundQuad.setScaleY((float)height / (float)getVideoHeight());
                else
                    mBackgroundQuad.setScaleX((float)width / (float)getVideoWidth());
                mBackgroundQuad.setMaterial(material);
                mBackgroundQuad.rotate(0, 0, 1, 180);
                getCurrentScene().addChildAt(mBackgroundQuad, 0);
            }
            //------------------------------

            mIsRenderingInit = true;
        }
    }


    public void setActive(boolean value)
    {
        if (mIsActive == value)
        {
            return;
        }

        mIsActive = value;
        configureVideoBackground();
    }


    public synchronized void updateRenderingPrimitives()
    {
        mRenderingPrimitives = Device.getInstance().getRenderingPrimitives();
    }

    // Initializes shaderr
    private void initArRendering()
    {
        vbShaderProgramID = SampleUtils.createProgramFromShaderSrc(VideoBackgroundShader.VB_VERTEX_SHADER,
                VideoBackgroundShader.VB_FRAGMENT_SHADER);

        // Rendering configuration for video background
        if (vbShaderProgramID > 0)
        {
            // Activate shader:
            GLES20.glUseProgram(vbShaderProgramID);

            // Retrieve handler for texture sampler shader uniform variable:
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D");

            // Retrieve handler for projection matrix shader uniform variable:
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");

            vbVertexHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexPosition");
            vbTexCoordHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexTexCoord");
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix");
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D");

            // Stop using the program
            GLES20.glUseProgram(0);
        }

        videoBackgroundTex = new GLTextureUnit();
    }

    /**
     * 所有渲染的总入口
     * TODO 这里要改成绘制到FBO上
     */
    // Main rendering method
    // The method setup state for rendering, setup 3D transformations required for AR augmentation
    // and call any specific rendering method
    public void render()
    {
        if (!mIsActive)
        {
            return;
        }

        //GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Get our current state
        State state;
        state = TrackerManager.getInstance().getStateUpdater().updateState();
        mRenderer.begin(state);

        // 设置Camera投影矩阵
        if (state.getCameraCalibration() != null) {
            getCurrentCamera().setProjectionMatrix(getFOV(state.getCameraCalibration()), getVideoWidth(),
                    getVideoHeight());
        }

        //GLES20.glFrontFace(GLES20.GL_CCW);  // Back camera

        // We get a list of views which depend on the mode we are working on, for mono we have
        // only one view, in stereo we have three: left, right and postprocess
        ViewList viewList = mRenderingPrimitives.getRenderingViews();

        // Cycle through the view list
        for (int v = 0; v < viewList.getNumViews(); v++)
        {
            // Get the view id
            int viewID = viewList.getView(v);

            // Get the viewport for that specific view
            Vec4I viewport;
            viewport = mRenderingPrimitives.getViewport(viewID);

            // Set viewport for current view
            //GLES20.glViewport(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

            // Set scissor
            //GLES20.glScissor(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

            // Get projection matrix for the current view.
            Matrix34F projMatrix = mRenderingPrimitives.getProjectionMatrix(viewID,
                    state.getCameraCalibration());

            // Create GL matrix setting up the near and far planes
            float rawProjectionMatrixGL[] = Tool.convertPerspectiveProjection2GLMatrix(
                    projMatrix,
                    mNearPlane,
                    mFarPlane)
                    .getData();

            // Apply the appropriate eye adjustment to the raw projection matrix, and assign to the global variable
            float eyeAdjustmentGL[] = Tool.convert2GLMatrix(mRenderingPrimitives
                    .getEyeDisplayAdjustmentMatrix(viewID)).getData();

            // Apply the adjustment to the projection matrix
            float projectionMatrix[] = new float[16];
            Matrix.multiplyMM(projectionMatrix, 0, rawProjectionMatrixGL, 0, eyeAdjustmentGL, 0);

            currentView = viewID;

            // Call renderFrame from the app renderer class which implements SampleAppRendererControl
            // This will be called for MONO, LEFT and RIGHT views, POSTPROCESS will not render the
            // frame
            if(currentView != VIEW.VIEW_POSTPROCESS)
                this.renderFrame(state, projectionMatrix);
        }

        mRenderer.end();
    }


    private void setNearFarPlanes(float near, float far)
    {
        mNearPlane = near;
        mFarPlane = far;
        getCurrentCamera().setNearPlane(mNearPlane); //10
        getCurrentCamera().setFarPlane(mFarPlane); //2500
    }


    /**
     * 通过opengl的方式来绘制相机预览数据
     * @param state
     */
    public void renderVideoBackground(State state)
    {
        if(currentView == VIEW.VIEW_POSTPROCESS)
            return;

        // Bind the video bg texture and get the Texture ID from Vuforia Engine
        int vbVideoTextureUnit = 0;
        videoBackgroundTex.setTextureUnit(vbVideoTextureUnit);

        if (!mRenderer.updateVideoBackgroundTexture(videoBackgroundTex))
        {
            Log.e(LOGTAG, "Unable to update video background texture");
            return;
        }

        float[] vbProjectionMatrix = Tool.convert2GLMatrix(
                mRenderingPrimitives.getVideoBackgroundProjectionMatrix(currentView)).getData();

        // Apply the scene scale on video see-through eyewear, to scale the video background and augmentation
        // so that the display lines up with the real world
        // This should not be applied on optical see-through devices, as there is no video background,
        // and the calibration ensures that the augmentation matches the real world
        if (Device.getInstance().isViewerActive())
        {
//            float sceneScaleFactor = (float) getSceneScaleFactor(state.getCameraCalibration());
//            Matrix.scaleM(vbProjectionMatrix, 0, sceneScaleFactor, sceneScaleFactor, 1.0f);
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        // 开始绘制到模型上
        int frameBufferId = mBackgroundRenderTarget.getFrameBufferHandle();
        int frameBufferTextureId = mBackgroundRenderTarget.getTexture().getTextureId();

        // zhf++
        //当一个FBO绑定以后，所有的OpenGL操作将会作用在这个绑定的帧缓冲区对象上。
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBufferId);
        //把一幅空的纹理图像关联到一个FBO 一个FBO在同一个时间内可以绑定多个颜色缓冲区，每个对应FBO的一个绑定点
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, frameBufferTextureId, 0);

        Mesh vbMesh = mRenderingPrimitives.getVideoBackgroundMesh(currentView);

        // Load the shader and upload the vertex/texcoord/index data
        GLES20.glUseProgram(vbShaderProgramID);
        GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT, false, 0, vbMesh.getPositions().asFloatBuffer());
        GLES20.glVertexAttribPointer(vbTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, vbMesh.getUVs().asFloatBuffer());

        GLES20.glUniform1i(vbTexSampler2DHandle, vbVideoTextureUnit);

        // Render the video background with the custom shader
        // First, we enable the vertex arrays
        GLES20.glEnableVertexAttribArray(vbVertexHandle);
        GLES20.glEnableVertexAttribArray(vbTexCoordHandle);

        // Pass the projection matrix to OpenGL
        GLES20.glUniformMatrix4fv(vbProjectionMatrixHandle, 1, false, vbProjectionMatrix, 0);

        // Then, we issue the render call
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, vbMesh.getNumTriangles() * 3, GLES20.GL_UNSIGNED_SHORT,
                vbMesh.getTriangles().asShortBuffer());

        // Finally, we disable the vertex arrays
        GLES20.glDisableVertexAttribArray(vbVertexHandle);
        GLES20.glDisableVertexAttribArray(vbTexCoordHandle);

        // zhf++
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        SampleUtils.checkGLError("Rendering of the video background failed");
    }

    private static final float VIRTUAL_FOV_Y_DEGS = 85.0f;
    private static final float M_PI = 3.14159f;


    // Returns scene scale factor primarily used for eye-wear devices
    private double getSceneScaleFactor(CameraCalibration cameraCalib)
    {
        if (cameraCalib == null)
        {
            Log.e(LOGTAG, "Cannot compute scene scale factor, camera calibration is invalid");
            return 0.0;
        }

        // Get the y-dimension of the physical camera field of view
        Vec2F fovVector = cameraCalib.getFieldOfViewRads();
        float cameraFovYRads = fovVector.getData()[1];

        // Get the y-dimension of the virtual camera field of view
        float virtualFovYRads = VIRTUAL_FOV_Y_DEGS * M_PI / 180;

        // The scene-scale factor represents the proportion of the viewport that is filled by
        // the video background when projected onto the same plane.
        // In order to calculate this, let 'd' be the distance between the cameras and the plane.
        // The height of the projected image 'h' on this plane can then be calculated:
        //   tan(fov/2) = h/2d
        // which rearranges to:
        //   2d = h/tan(fov/2)
        // Since 'd' is the same for both cameras, we can combine the equations for the two cameras:
        //   hPhysical/tan(fovPhysical/2) = hVirtual/tan(fovVirtual/2)
        // Which rearranges to:
        //   hPhysical/hVirtual = tan(fovPhysical/2)/tan(fovVirtual/2)
        // ... which is the scene-scale factor
        return Math.tan(cameraFovYRads / 2) / Math.tan(virtualFovYRads / 2);
    }

    // Configures the video mode and sets offsets for the camera's image
    private void configureVideoBackground()
    {
        if (!mIsActive)
        {
            return;
        }

        CameraDevice cameraDevice = CameraDevice.getInstance();
        VideoMode vm = cameraDevice.getVideoMode(mVideoMode);

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setPosition(new Vec2I(0, 0));

        int xSize, ySize;

        // We keep the aspect ratio to keep the video correctly rendered. If it is portrait we
        // preserve the height and scale width and vice versa if it is landscape
        // We then check if the selected values fill the screen, otherwise we invert
        // the selection
        if (mIsPortrait)
        {
            xSize = (int) (vm.getHeight() * (mScreenHeight / (float) vm
                    .getWidth()));
            ySize = mScreenHeight;

            if (xSize < mScreenWidth)
            {
                xSize = mScreenWidth;
                ySize = (int) (mScreenWidth * (vm.getWidth() / (float) vm
                        .getHeight()));
            }
        }
        else
        {
            xSize = mScreenWidth;
            ySize = (int) (vm.getHeight() * (mScreenWidth / (float) vm
                    .getWidth()));

            if (ySize < mScreenHeight)
            {
                xSize = (int) (mScreenHeight * (vm.getWidth() / (float) vm
                        .getHeight()));
                ySize = mScreenHeight;
            }
        }

        config.setSize(new Vec2I(xSize, ySize));

        Logger.d( "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + mScreenWidth + " , "
                + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        mVideoWidth = vm.getWidth();
        mVideoHeight = vm.getHeight();

        Renderer.getInstance().setVideoBackgroundConfig(config);
    }


    private void storeScreenDimensions()
    {
        // Query display dimensions:
        Point size = new Point();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
        {
            mActivityRef.get().getWindowManager().getDefaultDisplay().getRealSize(size);
        }
        else
        {
            WindowManager windowManager = (WindowManager) mActivityRef.get().getSystemService(Context.WINDOW_SERVICE);

            if (windowManager != null)
            {
                DisplayMetrics metrics = new DisplayMetrics();
                Display display = windowManager.getDefaultDisplay();
                display.getMetrics(metrics);

                size.x = metrics.widthPixels;
                size.y = metrics.heightPixels;
            }
            else
            {
                Log.e(LOGTAG, "Could not get display metrics!");
                size.x = 0;
                size.y = 0;
            }
        }

        mScreenWidth = size.x;
        mScreenHeight = size.y;
    }


    // Stores the orientation depending on the current resources configuration
    private void updateActivityOrientation()
    {
        Configuration config = mActivityRef.get().getResources().getConfiguration();

        switch (config.orientation)
        {
            case Configuration.ORIENTATION_PORTRAIT:
                mIsPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                mIsPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
        }

        Log.i(LOGTAG, "Activity is in "
                + (mIsPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }


    //---------------For rajawali---------------------------
    @Override
    public void onRenderSurfaceCreated(EGLConfig config, GL10 gl, int width, int height) {
        super.onRenderSurfaceCreated(config, gl, width, height);

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        mArManager.onSurfaceCreated();

        this.onSurfaceCreated();
    }

    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);

        // Call Vuforia function to handle render surface size changes:
        mArManager.onSurfaceChanged(width, height);

        // RenderingPrimitives to be updated when some rendering change is done
        onConfigurationChanged(width, height);
    }


    @Override
    public void onRenderSurfaceDestroyed(SurfaceTexture surface) {
        super.onRenderSurfaceDestroyed(surface);
    }


    @Override
    public void onRenderFrame(GL10 gl) {
        super.onRenderFrame(gl);
    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        super.onRender(ellapsedRealtime, deltaTime);
        // 原始的渲染Render
        this.render();
    }



    //--------------------------------------------------------
    public void foundImageMarker(String trackableName, float[] modelViewMatrix) {
        synchronized (this) {
            transformPositionAndOrientation(modelViewMatrix);
            onFoundImageMarker(trackableName, mPosition, mOrientation);
        }
    }

    private void copyFloatToDoubleMatrix(float[] src, double[] dst)
    {
        for(mI = 0; mI < 16; mI++)
        {
            dst[mI] = src[mI];
        }
    }


    private void transformPositionAndOrientation(float[] modelViewMatrix) {
        mPosition.setAll(modelViewMatrix[12], -modelViewMatrix[13],
                -modelViewMatrix[14]);
        copyFloatToDoubleMatrix(modelViewMatrix, mModelViewMatrix);
        mOrientation.fromMatrix(mModelViewMatrix);

        if(mArManager.getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        {
            mPosition.setAll(modelViewMatrix[12], -modelViewMatrix[13],
                    -modelViewMatrix[14]);
            mOrientation.y = -mOrientation.y;
            mOrientation.z = -mOrientation.z;
        }
        else
        {
            mPosition.setAll(-modelViewMatrix[13], -modelViewMatrix[12],
                    -modelViewMatrix[14]);
            double orX = mOrientation.x;
            mOrientation.x = -mOrientation.y;
            mOrientation.y = -orX;
            mOrientation.z = -mOrientation.z;
        }
    }

}
