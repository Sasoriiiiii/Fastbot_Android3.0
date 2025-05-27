package com.android.commands.monkey.source;

public class CoverageData {
    public float coverage;
    public String[] totalActivities;
    public String[] testedActivities;
    public int totalActivitiesCount;
    public int testedActivitiesCount;

    public CoverageData(float coverage, String[] totalActivities, String[] testedActivities) {
        this.coverage = coverage;
        this.totalActivities = totalActivities;
        this.testedActivities = testedActivities;
        this.totalActivitiesCount = totalActivities != null ? totalActivities.length : 0;
        this.testedActivitiesCount = testedActivities != null ? testedActivities.length : 0;
    }

    public CoverageData() {
    }
}