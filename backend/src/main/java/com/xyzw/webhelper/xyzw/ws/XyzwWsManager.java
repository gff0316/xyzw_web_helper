package com.xyzw.webhelper.xyzw.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
public class XyzwWsManager {
    private static final Logger logger = LoggerFactory.getLogger(XyzwWsManager.class);

    private final Map<String, XyzwWsClient> clients = new ConcurrentHashMap<String, XyzwWsClient>();
    private final Map<String, Map<String, Object>> roleInfos = new ConcurrentHashMap<String, Map<String, Object>>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public synchronized void connect(String key, String wsUrl) {
        disconnect(key);
        try {
            XyzwWsClient client = new XyzwWsClient(
                new URI(wsUrl),
                scheduler,
                key,
                this::handlePacket
            );
            clients.put(key, client);
            client.connect();
            logger.info("WebSocket开始连接 token={}", key);
        } catch (URISyntaxException ex) {
            logger.warn("WS 地址无效: {}", wsUrl, ex);
            throw new IllegalArgumentException("WS 地址无效");
        }
    }

    public synchronized void disconnect(String key) {
        XyzwWsClient existing = clients.remove(key);
        if (existing != null) {
            try {
                existing.stopHeartbeat();
                existing.close();
            } catch (Exception ex) {
                logger.debug("关闭 WebSocket 失败", ex);
            }
        }
        roleInfos.remove(key);
        logger.info("WebSocket已断开 token={}", key);
    }

    public boolean isConnected(String key) {
        XyzwWsClient client = clients.get(key);
        return client != null && client.isOpen();
    }

    public void sendBottleHelperRestart(String key, int bottleType) {
        XyzwWsClient client = clients.get(key);
        if (client == null || !client.isOpen()) {
            throw new IllegalStateException("WebSocket 未连接");
        }

        client.sendCommand("bottlehelper_stop", buildBottleBody(bottleType));
        scheduler.schedule(() -> {
            client.sendCommand("bottlehelper_start", buildBottleBody(bottleType));
            client.sendCommand("role_getroleinfo", buildRoleInfoBody());
        }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void requestRoleInfo(String key) {
        XyzwWsClient client = clients.get(key);
        if (client == null || !client.isOpen()) {
            throw new IllegalStateException("WebSocket 未连接");
        }
        logger.info("发送身份牌请求 token={}", key);
        client.sendCommand("role_getroleinfo", buildRoleInfoBody());
    }

    public Map<String, Object> getRoleInfo(String key) {
        return roleInfos.get(key);
    }

    public Map<String, Object> waitForRoleInfo(String key, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            Map<String, Object> info = roleInfos.get(key);
            if (info != null) {
                return info;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> buildBottleBody(int bottleType) {
        Map<String, Object> body = new java.util.LinkedHashMap<String, Object>();
        body.put("bottleType", bottleType);
        return body;
    }

    private Map<String, Object> buildRoleInfoBody() {
        Map<String, Object> body = new java.util.LinkedHashMap<String, Object>();
        body.put("clientVersion", "1.65.3-wx");
        body.put("inviteUid", 0);
        body.put("platform", "hortor");
        body.put("platformExt", "mix");
        body.put("scene", "");
        return body;
    }

    private void handlePacket(String key, String cmd, Object body) {
        if ("role_getroleinforesp".equals(cmd) && body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> roleInfo = (Map<String, Object>) body;
            roleInfos.put(key, roleInfo);
            logger.info("已接收身份牌信息 token={}", key);
        } else if (cmd != null && !cmd.isEmpty()) {
            logger.debug("收到 WebSocket 指令 cmd={} token={}", cmd, key);
        }
    }
}
