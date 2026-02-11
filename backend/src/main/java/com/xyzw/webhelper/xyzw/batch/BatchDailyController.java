package com.xyzw.webhelper.xyzw.batch;

import com.xyzw.webhelper.auth.AuthService;
import com.xyzw.webhelper.auth.dto.AuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/xyzw/batch/daily")
public class BatchDailyController {
    private static final Logger logger = LoggerFactory.getLogger(BatchDailyController.class);
    private final AuthService authService;
    private final BatchDailySchedulerService schedulerService;

    public BatchDailyController(AuthService authService, BatchDailySchedulerService schedulerService) {
        this.authService = authService;
        this.schedulerService = schedulerService;
    }

    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> listTasks(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            logger.warn("batchDaily listTasks unauthorized");
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        List<BatchDailyTaskResponse> tasks = schedulerService.listTasks(user.getId());
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("tasks", tasks);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @RequestBody BatchDailyTaskRequest request
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            logger.warn("batchDaily createTask unauthorized");
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        BatchDailyTaskResponse task = schedulerService.createTask(user.getId(), request);
        return ResponseEntity.ok(build(true, "success", task));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> updateTask(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("id") Long id,
        @RequestBody BatchDailyTaskRequest request
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            logger.warn("batchDaily updateTask unauthorized id={}", id);
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        BatchDailyTaskResponse task = schedulerService.updateTask(user.getId(), id, request);
        if (task == null) {
            return ResponseEntity.status(404).body(build(false, "task not found", null));
        }
        return ResponseEntity.ok(build(true, "success", task));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("id") Long id
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            logger.warn("batchDaily deleteTask unauthorized id={}", id);
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        boolean removed = schedulerService.deleteTask(user.getId(), id);
        if (!removed) {
            return ResponseEntity.status(404).body(build(false, "task not found", null));
        }
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/tasks/{id}/run")
    public ResponseEntity<Map<String, Object>> runTask(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("id") Long id
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            logger.warn("batchDaily runTask unauthorized id={}", id);
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        boolean started = schedulerService.runTaskNow(user.getId(), id);
        if (!started) {
            return ResponseEntity.status(404).body(build(false, "task not found", null));
        }
        return ResponseEntity.ok(build(true, "success", null));
    }

    private AuthResponse resolveUser(String authHeader) {
        String token = extractToken(authHeader);
        return authService.getUserByToken(token);
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
