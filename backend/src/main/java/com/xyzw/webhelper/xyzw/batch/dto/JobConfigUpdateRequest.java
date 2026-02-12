package com.xyzw.webhelper.xyzw.batch.dto;

public class JobConfigUpdateRequest {
    private String cronExpr;
    private Boolean enabled;

    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
