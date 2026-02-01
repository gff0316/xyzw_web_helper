package com.xyzw.webhelper.xyzw.dto;

public class XyzwTokenResponse {
    private String name;
    private String token;
    private String server;
    private String wsUrl;

    public XyzwTokenResponse() {
    }

    public XyzwTokenResponse(String name, String token, String server, String wsUrl) {
        this.name = name;
        this.token = token;
        this.server = server;
        this.wsUrl = wsUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }
}
