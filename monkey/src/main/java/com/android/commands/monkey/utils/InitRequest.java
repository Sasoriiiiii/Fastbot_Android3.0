package com.android.commands.monkey.utils;

import com.google.gson.annotations.SerializedName;


public class InitRequest {
    @SerializedName("takeScreenshots")
    private boolean takeScreenshots;

    @SerializedName("Stamp")
    private String logStamp;

    @SerializedName("deviceOutputRoot")
    private String deviceOutputRoot;

    public String getDeviceOutputRoot() {return deviceOutputRoot;}

    public String getLogStamp(){
        return logStamp;
    }

    public boolean isTakeScreenshots(){
        return takeScreenshots;
    }

}

