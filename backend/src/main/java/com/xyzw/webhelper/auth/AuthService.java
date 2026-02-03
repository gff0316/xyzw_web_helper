package com.xyzw.webhelper.auth;

import com.xyzw.webhelper.auth.dto.AuthResponse;
import com.xyzw.webhelper.auth.dto.LoginRequest;
import com.xyzw.webhelper.auth.dto.RegisterRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.time.LocalDateTime;

@Service
public class AuthService {
    private final AuthUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AuthUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = safe(request.getUsername());
        String email = safe(request.getEmail());
        String password = safe(request.getPassword());

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("username, email, password required");
        }

        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("username already exists");
        }

        if (userMapper.findByEmail(email) != null) {
            throw new IllegalArgumentException("email already exists");
        }

        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setToken(null);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return new AuthResponse(user.getId(), user.getUsername(), user.getEmail(), null);
    }

    public AuthResponse login(LoginRequest request) {
        String username = safe(request.getUsername());
        String password = safe(request.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("username and password required");
        }

        AuthUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("invalid credentials");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }

        String token = UUID.randomUUID().toString();
        user.setToken(token);
        userMapper.updateToken(user.getId(), token, LocalDateTime.now());
        return new AuthResponse(user.getId(), user.getUsername(), user.getEmail(), token);
    }

    public void logout(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        AuthUser user = userMapper.findByToken(token);
        if (user != null) {
            userMapper.updateToken(user.getId(), null, LocalDateTime.now());
        }
    }

    public AuthResponse getUserByToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        AuthUser user = userMapper.findByToken(token);
        if (user == null) {
            return null;
        }
        return new AuthResponse(user.getId(), user.getUsername(), user.getEmail(), user.getToken());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
