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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public synchronized void connect(String key, String wsUrl) {
        disconnect(key);
        try {
            XyzwWsClient client = new XyzwWsClient(new URI(wsUrl), scheduler);
            clients.put(key, client);
            client.connect();
        } catch (URISyntaxException ex) {
            logger.warn("Invalid ws url: {}", wsUrl, ex);
            throw new IllegalArgumentException("Invalid ws url");
        }
    }

    public synchronized void disconnect(String key) {
        XyzwWsClient existing = clients.remove(key);
        if (existing != null) {
            try {
                existing.stopHeartbeat();
                existing.close();
            } catch (Exception ex) {
                logger.debug("Failed to close ws client", ex);
            }
        }
    }

    public boolean isConnected(String key) {
        XyzwWsClient client = clients.get(key);
        return client != null && client.isOpen();
    }

    public void sendBottleHelperRestart(String key, int bottleType) {
        XyzwWsClient client = clients.get(key);
        if (client == null || !client.isOpen()) {
            throw new IllegalStateException("WebSocket not connected");
        }

        client.sendCommand("bottlehelper_stop", buildBottleBody(bottleType));
        scheduler.schedule(() -> {
            client.sendCommand("bottlehelper_start", buildBottleBody(bottleType));
            client.sendCommand("role_getroleinfo", buildRoleInfoBody());
        }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private Map<String, Object> buildBottleBody(int bottleType) {
        Map<String, Object> body = new java.util.LinkedHashMap<String, Object>();
        body.put("bottleType", bottleType);
        return body;
    }

    private Map<String, Object> buildRoleInfoBody() {
        Map<String, Object> body = new java.util.LinkedHashMap<String, Object>();
        body.put("clientVersion", "2.10.3-f10a39eaa0c409f4-wx");
        body.put("inviteUid", 0);
        body.put("platform", "hortor");
        body.put("platformExt", "mix");
        body.put("scene", "");
        return body;
    }
}
