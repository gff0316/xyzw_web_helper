package com.xyzw.webhelper.xyzw.ws;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class XyzwWsClient extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(XyzwWsClient.class);

    private final BonCodec codec = new BonCodec();
    private final Crypto crypto = new Crypto();
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger seq = new AtomicInteger(0);
    private final AtomicInteger ack = new AtomicInteger(0);
    private ScheduledFuture<?> heartbeatTask;

    XyzwWsClient(URI serverUri, ScheduledExecutorService scheduler) {
        super(serverUri);
        this.scheduler = scheduler;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        logger.info("websocket已连接: {}", getURI());
        startHeartbeat();
    }

    @Override
    public void onMessage(String message) {
        // Ignore text messages.
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        byte[] payload = new byte[bytes.remaining()];
        bytes.get(payload);
        try {
            byte[] plain = crypto.decryptAuto(payload);
            Object decoded = codec.decode(plain);
            if (decoded instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> packet = (Map<String, Object>) decoded;
                Object seqValue = packet.get("seq");
                if (seqValue instanceof Number) {
                    ack.set(((Number) seqValue).intValue());
                }
            }
        } catch (Exception ex) {
            logger.debug("Failed to parse ws message", ex);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("WebSocket链接关闭: code={}, reason={}", code, reason);
        stopHeartbeat();
    }

    @Override
    public void onError(Exception ex) {
        logger.warn("WebSocket error", ex);
    }

    void sendCommand(String cmd, Map<String, Object> body) {
        if (!isOpen()) {
            logger.warn("WebSocket not open, skip command {}", cmd);
            return;
        }
        int seqValue = "_sys/ack".equals(cmd) ? 0 : seq.incrementAndGet();
        Map<String, Object> packet = new LinkedHashMap<String, Object>();
        packet.put("cmd", cmd);
        packet.put("ack", ack.get());
        packet.put("seq", seqValue);
        packet.put("time", System.currentTimeMillis());
        if (body != null) {
            packet.put("body", codec.encode(body));
        } else {
            packet.put("body", codec.encode(new LinkedHashMap<String, Object>()));
        }

        byte[] encoded = codec.encode(packet);
        byte[] encrypted = crypto.encryptX(encoded);
        send(encrypted);
    }

    void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendCommand("_sys/ack", new LinkedHashMap<String, Object>());
            } catch (Exception ex) {
                logger.debug("Heartbeat failed", ex);
            }
        }, 3, 5, TimeUnit.SECONDS);
    }

    void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
    }
}
