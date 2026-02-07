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
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
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
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        wsManager.disconnect(token);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/bottlehelper/restart")
    public ResponseEntity<Map<String, Object>> restartBottle(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        int bottleType = request.getBottleType() == null ? 0 : request.getBottleType();
        if (!wsManager.isConnected(token)) {
            String wsUrl = buildDefaultWsUrl(token);
            logger.info("閺€璺哄煂闁插秴鎯庣純鎰摍鐠囬攱鐪伴敍灞藉帥瀵よ櫣鐝?WebSocket閵嗕繝ottleType={}, wsUrl={}", bottleType, wsUrl);
            wsManager.connect(token, wsUrl);
            if (!waitForConnection(token, 3000)) {
                logger.warn("闁插秴鎯庣純鎰摍婢惰精瑙﹂敍姝恊bSocket 鏉╃偞甯寸搾鍛閵嗕繝ottleType={}, wsUrl={}", bottleType, wsUrl);
                return ResponseEntity.badRequest().body(build(false, "WebSocket 鏉╃偞甯存径杈Е", null));
            }
        }
        logger.info("WebSocket 瀹歌尪绻涢幒銉礉閸欐垿鈧線鍣搁崥顖滅秷鐎涙劖瀵氭禒銈冣偓淇爋ttleType={}", bottleType);
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
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        logger.info("韬唤鐗屾煡璇㈠紑濮?refresh={} token={}", refresh, token);
        if (refresh) {
            if (!wsManager.isConnected(token)) {
                String wsUrl = buildDefaultWsUrl(token);
                wsManager.connect(token, wsUrl);
                if (!waitForConnection(token, 3000)) {
                    logger.warn("韬唤鐗屾煡璇㈠け璐ワ細WebSocket 杩炴帴瓒呮椂 token={}", token);
                    return ResponseEntity.badRequest().body(build(false, "WebSocket 鏉╃偞甯存径杈Е", null));
                }
            }
            try {
                wsManager.requestRoleInfo(token);
            } catch (IllegalStateException ex) {
                logger.warn("韬唤鐗屾煡璇㈠け璐ワ細{}", ex.getMessage());
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
                    logger.warn("韬唤鐗屾煡璇㈠け璐ワ細{}", ex.getMessage());
                    return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
                }
            }
        }
        Map<String, Object> data = new HashMap<String, Object>();
        if (roleInfo != null) {
            data.put("roleInfo", roleInfo);
            logger.info("韬唤鐗屾煡璇㈡垚鍔?token={}", token);
        } else {
            logger.warn("韬唤鐗屾煡璇㈢粨鏋滀负绌?token={}", token);
        }
        return ResponseEntity.ok(build(true, "success", data));
    }

    private String buildDefaultWsUrl(String token) {
        try {
            return "wss://xxz-xyzw.hortorgames.com/agent?p=" +
                java.net.URLEncoder.encode(token, "UTF-8") +
                "&e=x&lang=chinese";
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException("涓嶆敮鎸佺殑缂栫爜 UTF-8", ex);
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

    @PostMapping("/hangup/extend")
    public ResponseEntity<Map<String, Object>> extendHangup(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!wsManager.isConnected(token)) {
            String wsUrl = buildDefaultWsUrl(token);
            wsManager.connect(token, wsUrl);
            if (!waitForConnection(token, 3000)) {
                logger.warn("鎸傛満鍔犻挓澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={}", token);
                return ResponseEntity.badRequest().body(build(false, "WebSocket 鏉╃偞甯存径杈Е", null));
            }
        }
        try {
            wsManager.extendHangUp(token);
        } catch (IllegalStateException ex) {
            logger.warn("鎸傛満鍔犻挓澶辫触锛歿}}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }
        return ResponseEntity.ok(build(true, "success", null));
    }
}
