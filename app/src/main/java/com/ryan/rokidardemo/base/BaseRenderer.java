package com.ryan.rokidardemo.base;

import android.opengl.GLSurfaceView;


import com.ryan.rokidardemo.utils.Logger;
import com.ryan.rokidardemo.vuforia.ArManager;
import com.ryan.rokidardemo.vuforia.ArRenderer;
import com.ryan.rokidardemo.vuforia.utils.Texture;

import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 这里要改成使用Rajawali的Render,声明周期一致
 */
public abstract class BaseRenderer implements GLSurfaceView.Renderer {

    protected ArRenderer mArRenderer;
    protected ArManager mArManager;
    protected Vector<Texture> mTextures;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Logger.d("GLRenderer.onSurfaceCreated");

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        mArManager.onSurfaceCreated();
        mArRenderer.onSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Logger.d("GLRenderer.onSurfaceChanged width="+width+", height="+height);

        // Call Vuforia function to handle render surface size changes:
        mArManager.onSurfaceChanged(width, height);

        // RenderingPrimitives to be updated when some rendering change is done
        onConfigurationChanged();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 原始的渲染Render
        mArRenderer.render();
    }

    public void onConfigurationChanged()
    {
        mArRenderer.onConfigurationChanged();
    }
}
