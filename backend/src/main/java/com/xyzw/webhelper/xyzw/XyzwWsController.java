package com.xyzw.webhelper.xyzw;

import com.xyzw.webhelper.xyzw.dto.XyzwBottleRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwWsConnectRequest;
import com.xyzw.webhelper.xyzw.ws.XyzwWsManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/xyzw/ws")
public class XyzwWsController {
    private final XyzwWsManager wsManager;

    public XyzwWsController(XyzwWsManager wsManager) {
        this.wsManager = wsManager;
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody XyzwWsConnectRequest request) {
        String token = request.getToken();
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(build(false, "token is required", null));
        }

        String wsUrl = request.getWsUrl();
        if (wsUrl == null || wsUrl.trim().isEmpty()) {
            wsUrl = buildDefaultWsUrl(token);
        }

        wsManager.connect(token, wsUrl);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("wsUrl", wsUrl);
        data.put("status", "connecting");
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@RequestBody XyzwWsConnectRequest request) {
        String token = request.getToken();
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(build(false, "token is required", null));
        }
        wsManager.disconnect(token);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/bottlehelper/restart")
    public ResponseEntity<Map<String, Object>> restartBottle(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(build(false, "token is required", null));
        }
        int bottleType = request.getBottleType() == null ? 0 : request.getBottleType();
        wsManager.sendBottleHelperRestart(token, bottleType);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@RequestParam("token") String token) {
        boolean connected = wsManager.isConnected(token);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("status", connected ? "connected" : "disconnected");
        return ResponseEntity.ok(build(true, "success", data));
    }

    private String buildDefaultWsUrl(String token) {
        return "wss://xxz-xyzw.hortorgames.com/agent?p=" +
            java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8) +
            "&e=x&lang=chinese";
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
