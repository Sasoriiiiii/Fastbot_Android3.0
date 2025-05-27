package com.android.commands.monkey.utils;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StepMonkeyRequest {
    @SerializedName("block_widgets")
    private List<String> blockWidgets;

    @SerializedName("steps_count")
    private int stepsCount;

    public List<String> getBlockWidgets() {
        return blockWidgets;
    }

    public int getStepsCount(){
        return stepsCount;
    }
}
