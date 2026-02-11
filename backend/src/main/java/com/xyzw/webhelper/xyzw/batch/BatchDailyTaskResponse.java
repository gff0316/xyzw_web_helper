package com.xyzw.webhelper.xyzw.batch;

import java.util.List;

public class BatchDailyTaskResponse {
    private Long id;
    private String name;
    private String time;
    private boolean enabled;
    private Integer taskDelayMs;
    private int tokenCount;
    private List<String> tokenNames;
    private String lastRunAt;
    private String lastStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getTaskDelayMs() {
        return taskDelayMs;
    }

    public void setTaskDelayMs(Integer taskDelayMs) {
        this.taskDelayMs = taskDelayMs;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public List<String> getTokenNames() {
        return tokenNames;
    }

    public void setTokenNames(List<String> tokenNames) {
        this.tokenNames = tokenNames;
    }

    public String getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(String lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }
}
