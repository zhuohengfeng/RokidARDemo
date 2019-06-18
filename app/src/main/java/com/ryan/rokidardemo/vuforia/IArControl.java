package com.ryan.rokidardemo.vuforia;

import com.vuforia.State;

public interface IArControl {

    // To be called to initialize the trackers
    boolean doInitTrackers();


    // To be called to load the trackers' data
    boolean doLoadTrackersData();


    // To be called to start tracking with the initialized trackers and their
    // loaded data
    @SuppressWarnings("UnusedReturnValue")
    boolean doStartTrackers();


    // To be called to stop the trackers
    @SuppressWarnings("UnusedReturnValue")
    boolean doStopTrackers();


    // To be called to destroy the trackers' data
    boolean doUnloadTrackersData();


    // To be called to deinitialize the trackers
    boolean doDeinitTrackers();


    // This callback is called after the Vuforia Engine initialization is complete,
    // the trackers are initialized, their data loaded and
    // tracking is ready to start
    // If an exception is passed, it will notify the user and stop the experience
    void onInitARDone(ArException e);


    // This callback is called every cycle
    void onVuforiaUpdate(State state);


    // This callback is called on Vuforia Engine resume
    void onVuforiaResumed();


    // This callback is called once Vuforia Engine has been started
    void onVuforiaStarted();

}
