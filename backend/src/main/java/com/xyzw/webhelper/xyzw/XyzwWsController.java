package com.xyzw.webhelper.xyzw;

import com.xyzw.webhelper.xyzw.dto.XyzwBottleRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwWsConnectRequest;
import com.xyzw.webhelper.xyzw.ws.XyzwWsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(XyzwWsController.class);
    private final XyzwWsManager wsManager;

    public XyzwWsController(XyzwWsManager wsManager) {
        this.wsManager = wsManager;
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody XyzwWsConnectRequest request) {
        String token = request.getToken();
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
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
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        wsManager.disconnect(token);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/bottlehelper/restart")
    public ResponseEntity<Map<String, Object>> restartBottle(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        int bottleType = request.getBottleType() == null ? 0 : request.getBottleType();
        if (!wsManager.isConnected(token)) {
            String wsUrl = buildDefaultWsUrl(token);
            logger.info("收到重启罐子请求，先建立 WebSocket。bottleType={}, wsUrl={}", bottleType, wsUrl);
            wsManager.connect(token, wsUrl);
            if (!waitForConnection(token, 3000)) {
                logger.warn("重启罐子失败：WebSocket 连接超时。bottleType={}, wsUrl={}", bottleType, wsUrl);
                return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
            }
        }

        logger.info("WebSocket 已连接，发送重启罐子指令。bottleType={}", bottleType);
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

    @GetMapping("/roleinfo")
    public ResponseEntity<Map<String, Object>> roleInfo(
        @RequestParam("token") String token,
        @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh
    ) {
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        logger.info("身份牌查询开始 refresh={} token={}", refresh, token);

        if (refresh) {
            if (!wsManager.isConnected(token)) {
                String wsUrl = buildDefaultWsUrl(token);
                wsManager.connect(token, wsUrl);
                if (!waitForConnection(token, 3000)) {
                    logger.warn("身份牌查询失败：WebSocket 连接超时 token={}", token);
                    return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
                }
            }
            try {
                wsManager.requestRoleInfo(token);
            } catch (IllegalStateException ex) {
                logger.warn("身份牌查询失败：{}", ex.getMessage());
                return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
            }
        }

        Map<String, Object> roleInfo = wsManager.getRoleInfo(token);
        if (roleInfo == null && refresh) {
            roleInfo = wsManager.waitForRoleInfo(token, 5000);
            if (roleInfo == null) {
                try {
                    wsManager.requestRoleInfo(token);
                    roleInfo = wsManager.waitForRoleInfo(token, 3000);
                } catch (IllegalStateException ex) {
                    logger.warn("身份牌查询失败：{}", ex.getMessage());
                    return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
                }
            }
        }

        Map<String, Object> data = new HashMap<String, Object>();
        if (roleInfo != null) {
            data.put("roleInfo", roleInfo);
            logger.info("身份牌查询成功 token={}", token);
        } else {
            logger.warn("身份牌查询结果为空 token={}", token);
        }
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/hangup/extend")
    public ResponseEntity<Map<String, Object>> extendHangup(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!wsManager.isConnected(token)) {
            String wsUrl = buildDefaultWsUrl(token);
            wsManager.connect(token, wsUrl);
            if (!waitForConnection(token, 3000)) {
                logger.warn("挂机加钟失败：WebSocket 连接超时 token={}", token);
                return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
            }
        }
        try {
            wsManager.extendHangUp(token);
        } catch (IllegalStateException ex) {
            logger.warn("挂机加钟失败：{}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
        return ResponseEntity.ok(build(true, "success", null));
    }

    private String buildDefaultWsUrl(String token) {
        try {
            return "wss://xxz-xyzw.hortorgames.com/agent?p=" +
                java.net.URLEncoder.encode(token, "UTF-8") +
                "&e=x&lang=chinese";
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException("不支持的编码 UTF-8", ex);
        }
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

    private boolean waitForConnection(String token, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (wsManager.isConnected(token)) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
