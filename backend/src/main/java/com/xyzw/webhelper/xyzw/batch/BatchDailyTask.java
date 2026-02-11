package com.xyzw.webhelper.xyzw.batch;

import com.xyzw.webhelper.xyzw.dto.XyzwDailyTaskRequest;

import java.util.List;

public class BatchDailyTask {
    private Long id;
    private Long userId;
    private String name;
    private String time;
    private boolean enabled;
    private Integer taskDelayMs;
    private List<BatchDailyToken> tokens;
    private XyzwDailyTaskRequest.DailySettings settings;
    private String lastRunDate;
    private String lastRunAt;
    private String lastStatus;
    private String createdAt;
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getLastRunDate() {
        return lastRunDate;
    }

    public void setLastRunDate(String lastRunDate) {
        this.lastRunDate = lastRunDate;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
