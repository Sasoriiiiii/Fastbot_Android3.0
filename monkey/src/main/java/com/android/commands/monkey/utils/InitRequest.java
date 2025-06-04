package com.android.commands.monkey.utils;

import com.google.gson.annotations.SerializedName;


public class InitRequest {
    @SerializedName("takeScreenshots")
    private boolean takeScreenshots;

    @SerializedName("logStamp")
    private String logStamp;

    public String getLogStamp(){
        return logStamp;
    }

    public boolean isTakeScreenshots(){
        return takeScreenshots;
    }

}

