package com.xyzw.webhelper.xyzw.batch;

import com.xyzw.webhelper.xyzw.XyzwUserToken;
import com.xyzw.webhelper.xyzw.XyzwUserTokenMapper;
import com.xyzw.webhelper.xyzw.ws.XyzwWsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

@Service
public class XyzwTokenMaintenanceService {
    private static final Logger logger = LoggerFactory.getLogger(XyzwTokenMaintenanceService.class);
    private static final long CONNECT_TIMEOUT_MS = 5000;
    private static final long PROBE_TIMEOUT_MS = 3000;
    private static final int DEFAULT_BOTTLE_TYPE = 0;
    private static final int MAX_RECONNECT_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 800;

    private final XyzwUserTokenMapper tokenMapper;
    private final XyzwWsManager wsManager;
    @Value("${jobs.token-maintenance.enable-bottle-restart:false}")
    private boolean enableBottleRestart;

    public XyzwTokenMaintenanceService(XyzwUserTokenMapper tokenMapper, XyzwWsManager wsManager) {
        this.tokenMapper = tokenMapper;
        this.wsManager = wsManager;
    }

    public void runAllTokens() {
        List<XyzwUserToken> tokens = tokenMapper.findAll();
        if (tokens == null || tokens.isEmpty()) {
            logger.info("批量维护任务结束，没有可用的 token");
            return;
        }
        logger.info("批量维护任务开始 count={}", tokens.size());
        int success = 0;
        int failed = 0;
        for (XyzwUserToken token : tokens) {
            if (token == null || isBlank(token.getToken())) {
                logger.warn("忽略空 token id={}", token == null ? null : token.getId());
                continue;
            }
            String rawToken = token.getToken();
            String tokenValue = wsManager.normalizeToken(rawToken);
            if (isBlank(tokenValue)) {
                logger.warn("token 规范化失败，忽略 id={}", token.getId());
                continue;
            }
            String wsUrl = resolveWsUrl(token.getWsUrl(), tokenValue);
            String label = buildTokenLabel(token);
            if (!tokenValue.equals(rawToken)) {
                logger.info("token 规范化完成 {} raw={} normalized={}", label, briefToken(rawToken), briefToken(tokenValue));
            }

            if (processTokenWithRetry(tokenValue, wsUrl, label)) {
                success += 1;
            } else {
                failed += 1;
            }
            sleep(300);
        }
        logger.info("批量维护任务结束 success={} failed={}", success, failed);
    }

    private void runForToken(String token, String wsUrl, String label) {
        logger.info("开始执行维护任务 {}", label);

        if (enableBottleRestart) {
            wsManager.sendBottleHelperRestart(token, DEFAULT_BOTTLE_TYPE);
            sleep(800);
            ensureConnectedOrReconnect(token, wsUrl, label, "bottle-restart");
        } else {
            logger.debug("skip bottle helper restart {}", label);
        }

        ensureConnectedOrReconnect(token, wsUrl, label, "extend-hangup");
        long beforeExtend = wsManager.getRoleInfoVersion(token);
        wsManager.extendHangUp(token);
        if (wsManager.waitForRoleInfoUpdated(token, beforeExtend, 6000) == null) {
            logger.warn("挂机加钟超时 {}", label);
        } else {
            logger.info("挂机加钟完成 {}", label);
        }

        ensureConnectedOrReconnect(token, wsUrl, label, "claim-reward");
        long beforeClaim = wsManager.getRoleInfoVersion(token);
        wsManager.claimHangUpReward(token);
        if (wsManager.waitForRoleInfoUpdated(token, beforeClaim, 6000) == null) {
            logger.warn("领取挂机奖励超时 {}", label);
        } else {
            logger.info("领取挂机奖励完成 {}", label);
        }
    }

    private boolean processTokenWithRetry(String tokenValue, String wsUrl, String label) {
        for (int attempt = 1; attempt <= MAX_RECONNECT_RETRIES; attempt++) {
            boolean connected = false;
            try {
                if (wsManager.isReconnectPaused(tokenValue)) {
                    logger.warn("自动重连已暂停，终止维护 {} reason={}", label, wsManager.getReconnectPauseReason(tokenValue));
                    return false;
                }
                connected = connectAndWait(tokenValue, wsUrl);
                if (!connected) {
                    logger.warn("token 连接失败 {} attempt={}/{}", label, attempt, MAX_RECONNECT_RETRIES);
                    continue;
                }
                if (!probeRoleInfo(tokenValue, label)) {
                    logger.warn("token 探活失败 {} attempt={}/{}", label, attempt, MAX_RECONNECT_RETRIES);
                    continue;
                }
                runForToken(tokenValue, wsUrl, label);
                logger.info("维护任务成功 {} attempt={}/{}", label, attempt, MAX_RECONNECT_RETRIES);
                return true;
            } catch (Exception ex) {
                logger.warn(
                    "维护任务失败 {} attempt={}/{} msg={}",
                    label,
                    attempt,
                    MAX_RECONNECT_RETRIES,
                    ex.getMessage()
                );
            }
            if (attempt < MAX_RECONNECT_RETRIES) {
                safeDisconnect(tokenValue);
                sleep(RETRY_DELAY_MS);
            }
        }
        logger.error("维护任务最终失败 {}", label);
        safeDisconnect(tokenValue);
        return false;
    }

    private boolean probeRoleInfo(String token, String label) {
        try {
            long before = wsManager.getRoleInfoVersion(token);
            wsManager.requestRoleInfo(token);
            return wsManager.waitForRoleInfoUpdated(token, before, PROBE_TIMEOUT_MS) != null;
        } catch (Exception ex) {
            logger.warn("探活异常 {} msg={}", label, ex.getMessage());
            return false;
        }
    }

    private boolean connectAndWait(String token, String wsUrl) {
        if (wsManager.isConnected(token)) {
            return true;
        }
        if (wsManager.isReconnectPaused(token)) {
            logger.warn("自动重连已暂停 token={} reason={}", token, wsManager.getReconnectPauseReason(token));
            return false;
        }
        try {
            wsManager.connect(token, wsUrl);
        } catch (IllegalStateException ex) {
            logger.warn("自动重连被拒绝 token={} reason={}", token, ex.getMessage());
            return false;
        }
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < CONNECT_TIMEOUT_MS) {
            if (wsManager.isConnected(token)) {
                return true;
            }
            sleep(100);
        }
        return false;
    }

    private String buildDefaultWsUrl(String token) {
        try {
            return "wss://xxz-xyzw.hortorgames.com/agent?p=" +
                URLEncoder.encode(token, "UTF-8") +
                "&e=x&lang=chinese";
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("不支持的编码 UTF-8", ex);
        }
    }

    private String resolveWsUrl(String configuredWsUrl, String token) {
        if (isBlank(configuredWsUrl)) {
            return buildDefaultWsUrl(token);
        }
        String wsUrl = configuredWsUrl.trim();
        String encoded = encodeToken(token);

        int pIndex = wsUrl.indexOf("p=");
        if (pIndex < 0) {
            String sep = wsUrl.contains("?") ? "&" : "?";
            return wsUrl + sep + "p=" + encoded;
        }
        int valueStart = pIndex + 2;
        int valueEnd = wsUrl.indexOf('&', valueStart);
        if (valueEnd < 0) {
            valueEnd = wsUrl.length();
        }
        return wsUrl.substring(0, valueStart) + encoded + wsUrl.substring(valueEnd);
    }

    private String encodeToken(String token) {
        try {
            return URLEncoder.encode(token, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("不支持的编码 UTF-8", ex);
        }
    }

    private String buildTokenLabel(XyzwUserToken token) {
        String name = token.getName();
        if (name == null || name.trim().isEmpty()) {
            name = "未命名账号";
        }
        return "id=" + token.getId() + " name=" + name;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String briefToken(String token) {
        if (token == null) {
            return "<null>";
        }
        String value = token.trim();
        if (value.isEmpty()) {
            return "<empty>";
        }
        int len = value.length();
        if (len <= 16) {
            return value + "(len=" + len + ")";
        }
        return value.substring(0, 6) + "..." + value.substring(len - 6) + "(len=" + len + ")";
    }

    private void ensureConnectedOrReconnect(String token, String wsUrl, String label, String stage) {
        if (wsManager.isConnected(token)) {
            return;
        }
        logger.warn("WebSocket disconnected stage={} {}, reconnecting...", stage, label);
        if (!connectAndWait(token, wsUrl)) {
            throw new IllegalStateException("WebSocket reconnect failed stage=" + stage + ": " + label);
        }
    }

    private void safeDisconnect(String token) {
        try {
            wsManager.disconnect(token);
        } catch (Exception ex) {
            logger.debug("关闭 WebSocket 失败 token={}", token, ex);
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
