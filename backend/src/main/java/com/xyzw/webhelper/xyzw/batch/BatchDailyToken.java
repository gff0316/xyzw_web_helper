package com.xyzw.webhelper.xyzw.batch;

import java.util.Map;

public class BatchDailyToken {
    private String name;
    private Map<String, Object> tokenData;
    private Long sessId;
    private Long connId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getTokenData() {
        return tokenData;
    }

    public void setTokenData(Map<String, Object> tokenData) {
        this.tokenData = tokenData;
    }

    public Long getSessId() {
        return sessId;
    }

    public void setSessId(Long sessId) {
        this.sessId = sessId;
    }

    public Long getConnId() {
        return connId;
    }

    public void setConnId(Long connId) {
        this.connId = connId;
    }
}
