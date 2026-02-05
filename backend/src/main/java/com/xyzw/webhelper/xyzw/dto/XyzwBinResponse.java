package com.xyzw.webhelper.xyzw.dto;

import java.time.LocalDateTime;
import java.util.List;

public class XyzwBinResponse {
    private Long id;
    private String name;
    private String filePath;
    private String remark;
    private LocalDateTime createdAt;
    private List<XyzwTokenRecordResponse> tokens;

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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<XyzwTokenRecordResponse> getTokens() {
        return tokens;
    }

    public void setTokens(List<XyzwTokenRecordResponse> tokens) {
        this.tokens = tokens;
    }
}
