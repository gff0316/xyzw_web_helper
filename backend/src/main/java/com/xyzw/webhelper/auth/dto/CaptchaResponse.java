package com.xyzw.webhelper.auth.dto;

public class CaptchaResponse {
    private String captchaId;
    private String question;

    public CaptchaResponse(String captchaId, String question) {
        this.captchaId = captchaId;
        this.question = question;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public String getQuestion() {
        return question;
    }
}
