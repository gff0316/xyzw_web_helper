package com.xyzw.webhelper.xyzw.batch;

import com.xyzw.webhelper.auth.AuthService;
import com.xyzw.webhelper.auth.dto.AuthResponse;
import com.xyzw.webhelper.xyzw.batch.dto.JobConfigUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobAdminController {
    private static final Logger logger = LoggerFactory.getLogger(JobAdminController.class);

    private final AuthService authService;
    private final JobDispatchService dispatchService;

    public JobAdminController(AuthService authService, JobDispatchService dispatchService) {
        this.authService = authService;
        this.dispatchService = dispatchService;
    }

    @GetMapping("/configs")
    public ResponseEntity<Map<String, Object>> listConfigs(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        List<JobScheduleConfig> configs = dispatchService.listConfigs();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("configs", configs);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PutMapping("/configs/{jobKey}")
    public ResponseEntity<Map<String, Object>> updateConfig(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("jobKey") String jobKey,
        @RequestBody JobConfigUpdateRequest request
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        if (request == null) {
            return ResponseEntity.badRequest().body(build(false, "request body required", null));
        }
        try {
            JobScheduleConfig config = dispatchService.updateConfig(jobKey, request.getCronExpr(), request.getEnabled(), user.getUsername());
            return ResponseEntity.ok(build(true, "success", config));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
    }

    @PostMapping("/run/{jobKey}")
    public ResponseEntity<Map<String, Object>> runNow(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @PathVariable("jobKey") String jobKey
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        try {
            String message = dispatchService.runOnce(jobKey, "MANUAL", user.getUsername());
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("jobKey", jobKey);
            data.put("message", message);
            return ResponseEntity.ok(build(true, "success", data));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        } catch (Exception ex) {
            logger.warn("manual run failed jobKey={} msg={}", jobKey, ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> logs(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @RequestParam(value = "jobKey", required = false) String jobKey,
        @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        AuthResponse user = resolveUser(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body(build(false, "unauthorized", null));
        }
        List<JobExecutionLog> logs = dispatchService.listRecentLogs(jobKey, limit == null ? 100 : limit);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("logs", logs);
        return ResponseEntity.ok(build(true, "success", data));
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
