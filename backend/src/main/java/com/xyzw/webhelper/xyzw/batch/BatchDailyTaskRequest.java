package com.xyzw.webhelper.xyzw.batch;

import com.xyzw.webhelper.xyzw.dto.XyzwDailyTaskRequest;

import java.util.List;

public class BatchDailyTaskRequest {
    private String name;
    private String time;
    private Boolean enabled;
    private Integer taskDelayMs;
    private List<BatchDailyToken> tokens;
    private XyzwDailyTaskRequest.DailySettings settings;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getTaskDelayMs() {
        return taskDelayMs;
    }

    public void setTaskDelayMs(Integer taskDelayMs) {
        this.taskDelayMs = taskDelayMs;
    }

    public List<BatchDailyToken> getTokens() {
        return tokens;
    }

    public void setTokens(List<BatchDailyToken> tokens) {
        this.tokens = tokens;
    }

    public XyzwDailyTaskRequest.DailySettings getSettings() {
        return settings;
    }

    public void setSettings(XyzwDailyTaskRequest.DailySettings settings) {
        this.settings = settings;
    }
}
