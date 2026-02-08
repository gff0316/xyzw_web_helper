package com.xyzw.webhelper.xyzw.ws;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
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
    private final String key;
    private final PacketListener packetListener;

    interface PacketListener {
        void onPacket(String key, String cmd, Object body);
    }

    XyzwWsClient(URI serverUri, ScheduledExecutorService scheduler, String key, PacketListener packetListener) {
        super(serverUri);
        this.scheduler = scheduler;
        this.key = key;
        this.packetListener = packetListener;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        logger.info("WebSocket 已连接 {}", getURI());
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
            if (plain.length == 0) {
                logger.warn("WebSocket 收到空载荷");
            }
            Object decoded = codec.decode(plain);
            if (!(decoded instanceof Map)) {
                logger.warn("WebSocket 解码结果不是 Map");
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> packet = (Map<String, Object>) decoded;
            Object seqValue = packet.get("seq");
            if (seqValue instanceof Number) {
                ack.set(((Number) seqValue).intValue());
            }
            if (packetListener != null) {
                String cmd = String.valueOf(packet.get("cmd"));
                if (cmd != null) {
                    cmd = cmd.toLowerCase(Locale.ROOT);
                }
                Object body = packet.get("body");
                Object bodyDecoded = body;
                if (body instanceof byte[]) {
                    bodyDecoded = codec.decode((byte[]) body);
                }
                packetListener.onPacket(key, cmd, bodyDecoded);
            }
        } catch (Exception ex) {
            logger.warn("WebSocket 解码失败", ex);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("WebSocket 连接关闭: code={}, reason={}", code, reason);
        stopHeartbeat();
    }

    @Override
    public void onError(Exception ex) {
        logger.warn("WebSocket 异常", ex);
    }

    void sendCommand(String cmd, Map<String, Object> body) {
        if (!isOpen()) {
            logger.warn("WebSocket 未连接，跳过指令 {}", cmd);
            return;
        }
        int seqValue = "_sys/ack".equals(cmd) ? 0 : seq.incrementAndGet();
        Map<String, Object> packet = new LinkedHashMap<String, Object>();
        packet.put("cmd", cmd);
        packet.put("ack", ack.get());
        packet.put("seq", seqValue);
        packet.put("time", System.currentTimeMillis());
        if (!"_sys/ack".equals(cmd)) {
            packet.put("rtt", ThreadLocalRandom.current().nextInt(0, 500));
            packet.put("code", 0);
        }
        if (body != null) {
            packet.put("body", codec.encode(body));
        } else {
            packet.put("body", codec.encode(new LinkedHashMap<String, Object>()));
        }

        if (!"_sys/ack".equals(cmd)) {
            logger.info("发送指令 cmd={} token={}", cmd, key);
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
                logger.warn("WebSocket 心跳发送失败", ex);
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