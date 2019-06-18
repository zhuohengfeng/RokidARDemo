package com.ryan.rokidardemo.vuforia;

import com.vuforia.State;

public interface IArRendererControl {
    // This method must be implemented by the Renderer class that handles the content rendering.
    // This function is called for each view inside of a loop
    void renderFrame(State state, float[] projectionMatrix);

    // Initializes shaders
    void initRendering();
}
