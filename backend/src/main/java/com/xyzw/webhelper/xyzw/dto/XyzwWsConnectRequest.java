package com.xyzw.webhelper.xyzw.dto;

public class XyzwWsConnectRequest {
    private String token;
    private String wsUrl;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }
}
