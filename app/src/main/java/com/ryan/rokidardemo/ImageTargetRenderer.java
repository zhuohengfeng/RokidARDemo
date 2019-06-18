package com.ryan.rokidardemo;

import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;

import com.ryan.rokidardemo.utils.Logger;
import com.ryan.rokidardemo.vuforia.ArManager;
import com.ryan.rokidardemo.vuforia.ArRenderer;
import com.ryan.rokidardemo.vuforia.utils.CubeShaders;
import com.ryan.rokidardemo.vuforia.utils.LoadingDialogHandler;
import com.ryan.rokidardemo.vuforia.utils.MeshObject;
import com.ryan.rokidardemo.vuforia.utils.SampleApplication3DModel;
import com.ryan.rokidardemo.vuforia.utils.SampleMath;
import com.ryan.rokidardemo.vuforia.utils.SampleUtils;
import com.ryan.rokidardemo.vuforia.utils.Teapot;
import com.ryan.rokidardemo.vuforia.utils.Texture;
import com.vuforia.Device;
import com.vuforia.DeviceTrackableResult;
import com.vuforia.ImageTargetResult;
import com.vuforia.Matrix44F;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.TrackableResultList;
import com.vuforia.Vuforia;

import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Plane;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Vector;

/**
 * 实际渲染的类，3个声明周期函数依附于父类BaseRenderer
 * 这个类是绘制定制化的一些内容，比如识别到后要显示的模型等
 * TODO 这个类要改成Rajawali的Renderer
 *
 * The renderer class for the Image Targets sample.
 *
 * In the renderFrame() function you can render augmentations to display over the Target
 */
public class ImageTargetRenderer extends ArRenderer
{
    private final WeakReference<ImageTargetActivity> mActivityRef;

    private int shaderProgramID;
    private int vertexHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;

    // Object to be rendered
    private Teapot mTeapot; // 绘制的茶壶模型

    private boolean mModelIsLoaded = false;
    private boolean mIsTargetCurrentlyTracked = false;

    private static final float OBJECT_SCALE_FLOAT = 0.003f;

    ImageTargetRenderer(ImageTargetActivity activity, ArManager manager)
    {
        super(activity,
                Device.MODE.MODE_AR, manager.getVideoMode(),
                false, 10 , 2500); //0.01f, 5f   10, 2500

        mActivityRef = new WeakReference<>(activity);
        mArManager = manager;
    }


    /**
     * 由ArRenderer来调用 ---- 识别到了，开始绘制各种模型
     * @param state
     * @param projectionMatrix
     */
    // The render function.
    // This function is called from the SampleAppRenderer by using the RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling its lifecycle.
    // NOTE: State should not be cached outside this method.
    @Override
    public void renderFrame(State state, float[] projectionMatrix)
    {
        // 这里绘制相机
        this.renderVideoBackground(state);


        // Set the device pose matrix as identity
        Matrix44F devicePoseMatrix = SampleMath.Matrix44FIdentity();
        Matrix44F modelMatrix;

        //GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        //GLES20.glEnable(GLES20.GL_CULL_FACE);
        //GLES20.glCullFace(GLES20.GL_BACK);
        //GLES20.glFrontFace(GLES20.GL_CCW);   // Back camera

        // Read device pose from the state and create a corresponding view matrix (inverse of the device pose)
        if (state.getDeviceTrackableResult() != null)
        {
            int statusInfo = state.getDeviceTrackableResult().getStatusInfo();
            int trackerStatus = state.getDeviceTrackableResult().getStatus();

            //mActivityRef.get().checkForRelocalization(statusInfo);

            if (trackerStatus != TrackableResult.STATUS.NO_POSE)
            {
                modelMatrix = Tool.convertPose2GLMatrix(state.getDeviceTrackableResult().getPose());

                // We transpose here because Matrix44FInverse returns a transposed matrix
                devicePoseMatrix = SampleMath.Matrix44FTranspose(SampleMath.Matrix44FInverse(modelMatrix));
            }
        }

        TrackableResultList trackableResultList = state.getTrackableResults();

        // Determine if target is currently being tracked
        setIsTargetCurrentlyTracked(trackableResultList);

        // Iterate through trackable results and render any augmentations
        for (TrackableResult result : trackableResultList)
        {
            Trackable trackable = result.getTrackable();

            if (result.isOfType(ImageTargetResult.getClassType()))
            {
                int textureIndex;
                modelMatrix = Tool.convertPose2GLMatrix(result.getPose());

                textureIndex = trackable.getName().equalsIgnoreCase("stones") ? 0
                        : 1;
                textureIndex = trackable.getName().equalsIgnoreCase("tarmac") ? 2
                        : textureIndex;

                // zhf
                float[] modelViewProjection = new float[16];
                float[] projectionMatrixArray = projectionMatrix;
                float[] viewMatrixArray = devicePoseMatrix.getData();
                float[] modelMatrixArray = modelMatrix.getData();
                Matrix.translateM(modelMatrixArray, 0, 0, 0, OBJECT_SCALE_FLOAT);
                Matrix.scaleM(modelMatrixArray, 0, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);
                // Combine device pose (view matrix) with model matrix
                Matrix.multiplyMM(modelMatrixArray, 0, viewMatrixArray, 0, modelMatrixArray, 0);
                // Do the final combination with the projection matrix
                Matrix.multiplyMM(modelViewProjection, 0, projectionMatrixArray, 0, modelMatrixArray, 0);
                foundImageMarker(trackable.getName(), modelViewProjection);


                // 识别到了，绘制模型
                //renderModel(projectionMatrix, devicePoseMatrix.getData(), modelMatrix.getData(), textureIndex);

                SampleUtils.checkGLError("Image Targets renderFrame");
            }
        }

        //GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void initRendering()
    {
        if (mTextures == null)
        {
            return;
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        for (Texture t : mTextures)
        {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
                CubeShaders.CUBE_MESH_VERTEX_SHADER,
                CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition");
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexTexCoord");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix");
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "texSampler2D");

        if(!mModelIsLoaded)
        {
            mTeapot = new Teapot();
            mModelIsLoaded = true;

            // Hide the Loading Dialog
            mActivityRef.get().loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }

    // projectionMatrix: 1.7970178 0.0 0.0 0.0 0.0 3.0050135 0.0 0.0 0.0 0.0027777778 -1.004008 -1.0 0.0 0.0 -0.02004008 0.0
    // viewMatrix: 1.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 1.0
    // modelMatrix: 0.8127379 0.15034762 -0.5628967 0.0 0.10913428 0.90974647 0.4005634 0.0 0.57231706 -0.38698432 0.7229774 0.0 -0.08235683 0.21225977 -0.39652106 1.0
    private void renderModel(float[] projectionMatrix, float[] viewMatrix, float[] modelMatrix, int textureIndex)
    {
//        Logger.printMatrix("projectionMatrix", projectionMatrix);
//        Logger.printMatrix("viewMatrix", viewMatrix);
//        Logger.printMatrix("modelMatrix", modelMatrix);

        MeshObject model;
        float[] modelViewProjection = new float[16];

        Matrix.translateM(modelMatrix, 0, 0, 0, OBJECT_SCALE_FLOAT);
        Matrix.scaleM(modelMatrix, 0, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);
//            printMatrix("modelMatrix-2 ", modelMatrix); // 执行这里
        model = mTeapot;

        // Combine device pose (view matrix) with model matrix
        Matrix.multiplyMM(modelMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Do the final combination with the projection matrix
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelMatrix, 0);


//        printMatrix("final-matrix ", modelViewProjection);

        // Activate the shader program and bind the vertex and tex coords
        GLES20.glUseProgram(shaderProgramID);

        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false, 0, model.getVertices());
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, model.getTexCoords());

        GLES20.glEnableVertexAttribArray(vertexHandle);
        GLES20.glEnableVertexAttribArray(textureCoordHandle);

        // Activate texture 0, bind it, pass to shader
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(textureIndex).mTextureID[0]);
        GLES20.glUniform1i(texSampler2DHandle, 0);

        // Pass the model view matrix to the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjection, 0);

        // Finally draw the model
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, model.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT, model.getIndices());

        // Disable the enabled arrays
        GLES20.glDisableVertexAttribArray(vertexHandle);
        GLES20.glDisableVertexAttribArray(textureCoordHandle);
    }


    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
    }


    private void setIsTargetCurrentlyTracked(TrackableResultList trackableResultList)
    {
        for(TrackableResult result : trackableResultList)
        {
            // Check the tracking status for result types
            // other than DeviceTrackableResult. ie: ImageTargetResult
            if (!result.isOfType(DeviceTrackableResult.getClassType()))
            {
                int currentStatus = result.getStatus();
                int currentStatusInfo = result.getStatusInfo();

                // The target is currently being tracked if the status is TRACKED|NORMAL
                if (currentStatus == TrackableResult.STATUS.TRACKED
                        || currentStatusInfo == TrackableResult.STATUS_INFO.NORMAL)
                {
                    mIsTargetCurrentlyTracked = true;
                    return;
                }
            }
        }

        mIsTargetCurrentlyTracked = false;
    }


    boolean isTargetCurrentlyTracked()
    {
        return mIsTargetCurrentlyTracked;
    }

    //=========================================================
    private Plane settingItem;

    @Override
    protected void initScene() {
        DirectionalLight light = new DirectionalLight(0.2f, -1f, 0f);
        light.setPower(.7f);
        getCurrentScene().addLight(light);

        light = new DirectionalLight(0.2f, 1f, 0f);
        light.setPower(1f);
        getCurrentScene().addLight(light);

//        getCurrentCamera().setFarPlane(100);
        getCurrentCamera().enableLookAt();
        getCurrentCamera().setLookAt(0, 0, 0);

        try {
            settingItem = new Plane(8, 3, 1, 1);
            Material sphereMaterial = new Material();
            sphereMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
            sphereMaterial.setColorInfluence(0);
            org.rajawali3d.materials.textures.Texture itemTexture = new org.rajawali3d.materials.textures.Texture("timeTexture", R.drawable.bt_item);
            try {
                sphereMaterial.addTexture(itemTexture);
            } catch (ATexture.TextureException e) {
                e.printStackTrace();
            }

            settingItem.setMaterial(sphereMaterial);
            settingItem.setPosition(0, 0, -20);
            getCurrentScene().addChildAt(settingItem, 0);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onFoundImageMarker(String trackableName, Vector3 position, Quaternion orientation) {
        Logger.d("onFoundImageMarker trackableName="+trackableName+", orientation="+orientation);
        settingItem.setVisible(true);
        settingItem.setPosition(position);
        settingItem.setOrientation(orientation);
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {

    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }
}

