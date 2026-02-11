package com.xyzw.webhelper.xyzw.batch;

import com.xyzw.webhelper.xyzw.XyzwUserToken;
import com.xyzw.webhelper.xyzw.XyzwUserTokenMapper;
import com.xyzw.webhelper.xyzw.ws.XyzwWsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

@Service
public class XyzwTokenMaintenanceService {
    private static final Logger logger = LoggerFactory.getLogger(XyzwTokenMaintenanceService.class);
    private static final long CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_BOTTLE_TYPE = 0;

    private final XyzwUserTokenMapper tokenMapper;
    private final XyzwWsManager wsManager;

    public XyzwTokenMaintenanceService(XyzwUserTokenMapper tokenMapper, XyzwWsManager wsManager) {
        this.tokenMapper = tokenMapper;
        this.wsManager = wsManager;
    }

    public void runAllTokens() {
        List<XyzwUserToken> tokens = tokenMapper.findAll();
        if (tokens == null || tokens.isEmpty()) {
            logger.info("\u6279\u91cf\u7ef4\u62a4\u4efb\u52a1\u7ed3\u675f\uff0c\u6ca1\u6709\u53ef\u7528\u7684 token");
            return;
        }
        logger.info("\u6279\u91cf\u7ef4\u62a4\u4efb\u52a1\u5f00\u59cb count={}", tokens.size());
        for (XyzwUserToken token : tokens) {
            if (token == null || isBlank(token.getToken())) {
                logger.warn("\u5ffd\u7565\u7a7a token id={}", token == null ? null : token.getId());
                continue;
            }
            String tokenValue = token.getToken();
            String wsUrl = isBlank(token.getWsUrl()) ? buildDefaultWsUrl(tokenValue) : token.getWsUrl();
            String label = buildTokenLabel(token);

            if (!connectAndWait(tokenValue, wsUrl)) {
                logger.warn("token \u8fde\u63a5\u5931\u8d25 {}", label);
                continue;
            }

            try {
                runForToken(tokenValue, label);
            } finally {
                wsManager.disconnect(tokenValue);
            }
            sleep(300);
        }
        logger.info("\u6279\u91cf\u7ef4\u62a4\u4efb\u52a1\u7ed3\u675f");
    }

    private void runForToken(String token, String label) {
        logger.info("\u5f00\u59cb\u6267\u884c\u7ef4\u62a4\u4efb\u52a1 {}", label);

        wsManager.sendBottleHelperRestart(token, DEFAULT_BOTTLE_TYPE);
        sleep(800);

        long beforeExtend = wsManager.getRoleInfoVersion(token);
        wsManager.extendHangUp(token);
        if (wsManager.waitForRoleInfoUpdated(token, beforeExtend, 6000) == null) {
            logger.warn("\u6302\u673a\u52a0\u949f\u8d85\u65f6 {}", label);
        } else {
            logger.info("\u6302\u673a\u52a0\u949f\u5b8c\u6210 {}", label);
        }

        long beforeClaim = wsManager.getRoleInfoVersion(token);
        wsManager.claimHangUpReward(token);
        if (wsManager.waitForRoleInfoUpdated(token, beforeClaim, 6000) == null) {
            logger.warn("\u9886\u53d6\u6302\u673a\u5956\u52b1\u8d85\u65f6 {}", label);
        } else {
            logger.info("\u9886\u53d6\u6302\u673a\u5956\u52b1\u5b8c\u6210 {}", label);
        }
    }

    private boolean connectAndWait(String token, String wsUrl) {
        if (wsManager.isConnected(token)) {
            return true;
        }
        wsManager.connect(token, wsUrl);
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
            throw new IllegalStateException("\u4e0d\u652f\u6301\u7684\u7f16\u7801 UTF-8", ex);
        }
    }

    private String buildTokenLabel(XyzwUserToken token) {
        String name = token.getName();
        if (name == null || name.trim().isEmpty()) {
            name = "\u672a\u547d\u540d\u8d26\u53f7";
        }
        return "id=" + token.getId() + " name=" + name;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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