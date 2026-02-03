package com.xyzw.webhelper.auth;

import com.xyzw.webhelper.auth.dto.AuthResponse;
import com.xyzw.webhelper.auth.dto.CaptchaResponse;
import com.xyzw.webhelper.auth.dto.LoginRequest;
import com.xyzw.webhelper.auth.dto.RegisterRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public ResponseEntity<Map<String, Object>> captcha() {
        CaptchaResponse response = captchaService.generate();
        return ResponseEntity.ok(build(true, "success", response));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        try {
            boolean ok = captchaService.verify(request.getCaptchaId(), request.getCaptchaAnswer());
            if (!ok) {
                return ResponseEntity.badRequest().body(build(false, "captcha_invalid", null));
            }
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(build(true, "success", response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        try {
            boolean ok = captchaService.verify(request.getCaptchaId(), request.getCaptchaAnswer());
            if (!ok) {
                return ResponseEntity.badRequest().body(build(false, "captcha_invalid", null));
            }
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(build(true, "success", response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String token = extractToken(authHeader);
        authService.logout(token);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> user(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String token = extractToken(authHeader);
        AuthResponse response = authService.getUserByToken(token);
        if (response == null) {
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        return ResponseEntity.ok(build(true, "success", response));
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.trim().isEmpty()) {
            return null;
        }
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }

    private Map<String, Object> build(boolean success, String message, Object data) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("success", success);
        payload.put("message", message);
        if (data != null) {
            payload.put("data", data);
        }
        return payload;
    }
}
