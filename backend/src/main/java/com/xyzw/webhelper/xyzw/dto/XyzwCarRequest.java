package com.xyzw.webhelper.xyzw.dto;

public class XyzwCarRequest {
    private String token;
    private Long carId;
    private Long helperId;
    private String text;
    private Boolean isUpgrade;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public Long getHelperId() {
        return helperId;
    }

    public void setHelperId(Long helperId) {
        this.helperId = helperId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean getIsUpgrade() {
        return isUpgrade;
    }

    public void setIsUpgrade(Boolean isUpgrade) {
        this.isUpgrade = isUpgrade;
    }
}
