package com.ryan.rokidardemo.vuforia;

public class ArException extends Exception
{
    private static final long serialVersionUID = 2L;

    // Error codes
    public static final int INITIALIZATION_FAILURE = 0;
    public static final int VUFORIA_ALREADY_INITIALIZATED = 1;
    public static final int TRACKERS_INITIALIZATION_FAILURE = 2;
    public static final int LOADING_TRACKERS_FAILURE = 3;
    public static final int UNLOADING_TRACKERS_FAILURE = 4;
    public static final int TRACKERS_DEINITIALIZATION_FAILURE = 5;
    public static final int CAMERA_INITIALIZATION_FAILURE = 6;

    private final int mCode;
    private final String mString;

    public ArException(int code, String description)
    {
        super(description);
        mCode = code;
        mString = description;
    }


    public int getCode()
    {
        return mCode;
    }


    public String getString()
    {
        return mString;
    }
}
