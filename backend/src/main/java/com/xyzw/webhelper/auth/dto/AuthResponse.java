package com.xyzw.webhelper.auth.dto;

public class AuthResponse {
    private Long id;
    private String username;
    private String email;
    private String token;

    public AuthResponse(Long id, String username, String email, String token) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.token = token;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }
}
