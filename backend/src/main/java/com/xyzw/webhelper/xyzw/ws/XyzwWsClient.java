package com.xyzw.webhelper.xyzw.ws;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
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
    private static final String NO_REASON = "<none>";
    private static final String NO_COMMAND = "<none>";

    private final BonCodec codec = new BonCodec();
    private final Crypto crypto = new Crypto();
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger seq = new AtomicInteger(0);
    private final AtomicInteger ack = new AtomicInteger(0);
    private final String key;
    private final PacketListener packetListener;
    private final CloseListener closeListener;

    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile long connectStartMs = System.currentTimeMillis();
    private volatile long openedAtMs = -1L;
    private volatile long lastSendAtMs = -1L;
    private volatile String lastCmd = NO_COMMAND;
    private volatile boolean closedByClient = false;
    private volatile String closeReasonByClient = NO_REASON;
    private volatile boolean kickedByOtherLogin = false;

    interface PacketListener {
        void onPacket(String key, String cmd, Object body);
    }

    interface CloseListener {
        void onClosed(
            String key,
            int code,
            String reason,
            boolean remote,
            boolean closedByClient,
            long aliveMs,
            String lastCmd
        );
    }

    XyzwWsClient(
        URI serverUri,
        ScheduledExecutorService scheduler,
        String key,
        PacketListener packetListener,
        CloseListener closeListener
    ) {
        super(serverUri);
        this.scheduler = scheduler;
        this.key = key;
        this.packetListener = packetListener;
        this.closeListener = closeListener;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        openedAtMs = System.currentTimeMillis();
        kickedByOtherLogin = false;
        logger.info("WebSocket 已连接 uri={} token={}", getURI(), key);
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
                logger.warn("WebSocket 收到空载荷 token={}", key);
            }
            Object decoded = codec.decode(plain);
            if (!(decoded instanceof Map)) {
                logger.warn("WebSocket 解码结果不是 Map token={}", key);
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> packet = (Map<String, Object>) decoded;
            Object seqValue = packet.get("seq");
            if (seqValue instanceof Number) {
                ack.set(((Number) seqValue).intValue());
            }
            if (packetListener != null) {
                Object error = packet.get("error");
                String cmd = normalizeCmd(packet.get("cmd"));
                if ((cmd == null || cmd.isEmpty()) && hasPacketError(error)) {
                    String inferred = inferResponseCmdFromLastCmd();
                    if (inferred != null) {
                        cmd = inferred;
                        logger.info("WebSocket 错误包缺少 cmd，按最近指令推断 cmd={} token={}", cmd, key);
                    }
                }
                if (isOtherLoginFatal(cmd, error)) {
                    kickedByOtherLogin = true;
                    logger.warn("WebSocket 收到顶号通知，连接即将被服务端关闭 token={}", key);
                }
                if (hasPacketError(error)) {
                    if (isBottleAlreadyOccupiedError(cmd, error)) {
                        logger.info("WebSocket 罐子启动提示：{} cmd={} token={}", error, cmd, key);
                    } else {
                        logger.warn("WebSocket 命令返回错误 cmd={} error={} token={}", cmd, error, key);
                    }
                }
                Object body = packet.get("body");
                Object bodyDecoded = body;
                if (body instanceof byte[]) {
                    bodyDecoded = codec.decode((byte[]) body);
                }
                packetListener.onPacket(key, cmd, bodyDecoded);
            }
        } catch (Exception ex) {
            logger.warn("WebSocket 解码失败 token={}", key, ex);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        long now = System.currentTimeMillis();
        long aliveMs = openedAtMs > 0 ? Math.max(0L, now - openedAtMs) : Math.max(0L, now - connectStartMs);
        long lastSendAgoMs = lastSendAtMs > 0 ? Math.max(0L, now - lastSendAtMs) : -1L;
        String normalizedReason = normalizeReason(reason);
        if (kickedByOtherLogin) {
            normalizedReason = "other login";
        }
        logger.info(
            "WebSocket 连接关闭: code={}, reason={}, remote={}, closedByClient={}, closeReasonByClient={}, aliveMs={}, lastCmd={}, lastSendAgoMs={}, token={}",
            code,
            normalizedReason,
            remote,
            closedByClient,
            closeReasonByClient,
            aliveMs,
            lastCmd,
            lastSendAgoMs,
            key
        );
        if (remote && !closedByClient && code == 1006 && aliveMs <= 2000L) {
            logger.warn("WebSocket 被服务端快速断开，疑似 token 失效/会话过期/被顶号 token={} lastCmd={}", key, lastCmd);
        }
        if (closeListener != null) {
            try {
                closeListener.onClosed(key, code, normalizedReason, remote, closedByClient, aliveMs, lastCmd);
            } catch (Exception ex) {
                logger.debug("WebSocket onClose callback failed token={}", key, ex);
            }
        }
        stopHeartbeat();
    }

    @Override
    public void onError(Exception ex) {
        logger.warn("WebSocket 异常 token={}", key, ex);
    }

    void sendCommand(String cmd, Map<String, Object> body) {
        if (!isOpen()) {
            logger.warn("WebSocket 未连接，跳过指令 {} token={}", cmd, key);
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
            lastCmd = cmd;
            lastSendAtMs = System.currentTimeMillis();
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
                logger.warn("WebSocket 心跳发送失败 token={}", key, ex);
            }
        }, 3, 5, TimeUnit.SECONDS);
    }

    void stopHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        if (task != null) {
            task.cancel(true);
            heartbeatTask = null;
        }
    }

    void closeByClient(String reason) {
        closedByClient = true;
        closeReasonByClient = normalizeReason(reason);
        stopHeartbeat();
        try {
            close();
        } catch (Exception ex) {
            logger.debug("WebSocket 主动关闭失败 token={}", key, ex);
        }
    }

    boolean isConnecting() {
        return getReadyState() == ReadyState.NOT_YET_CONNECTED && !isOpen();
    }

    long getConnectingAgeMs() {
        return Math.max(0L, System.currentTimeMillis() - connectStartMs);
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return NO_REASON;
        }
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? NO_REASON : trimmed;
    }

    private boolean hasPacketError(Object error) {
        if (error == null) {
            return false;
        }
        if (error instanceof Number) {
            return ((Number) error).intValue() != 0;
        }
        if (error instanceof String) {
            String text = ((String) error).trim();
            if (text.isEmpty()) {
                return false;
            }
            return !"0".equals(text);
        }
        return true;
    }

    private String normalizeCmd(Object cmdObj) {
        if (cmdObj == null) {
            return null;
        }
        String text = String.valueOf(cmdObj).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private String inferResponseCmdFromLastCmd() {
        String latest = lastCmd;
        if (latest == null) {
            return null;
        }
        String text = latest.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || NO_COMMAND.equals(text) || "null".equals(text)) {
            return null;
        }
        if (text.endsWith("resp")) {
            return text;
        }
        return text + "resp";
    }

    private boolean isBottleAlreadyOccupiedError(String cmd, Object error) {
        if (cmd == null || !"bottlehelper_startresp".equals(cmd)) {
            return false;
        }
        if (!(error instanceof String)) {
            return false;
        }
        String text = ((String) error).trim();
        return text.contains("已经占领过这种类型的罐子");
    }

    private boolean isOtherLoginFatal(String cmd, Object error) {
        if (cmd == null || !"_sys/fatal".equals(cmd)) {
            return false;
        }
        if (!(error instanceof String)) {
            return false;
        }
        String text = ((String) error).trim().toLowerCase(Locale.ROOT);
        return "other login".equals(text);
    }
}
