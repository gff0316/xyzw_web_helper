package com.xyzw.webhelper.xyzw.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class XyzwWsManager {
    private static final Logger logger = LoggerFactory.getLogger(XyzwWsManager.class);
    private static final long CONNECTING_STALE_MS = 8000L;
    private static final long KICKED_MIN_ALIVE_MS = 60000L;

    private final Map<String, XyzwWsClient> clients = new ConcurrentHashMap<String, XyzwWsClient>();
    private final Map<String, Map<String, Object>> roleInfos = new ConcurrentHashMap<String, Map<String, Object>>();
    private final Map<String, Long> roleInfoVersions = new ConcurrentHashMap<String, Long>();
    private final Map<String, Map<String, Object>> teamInfos = new ConcurrentHashMap<String, Map<String, Object>>();
    private final Map<String, Long> teamInfoVersions = new ConcurrentHashMap<String, Long>();
    private final Map<String, Map<String, Object>> towerInfos = new ConcurrentHashMap<String, Map<String, Object>>();
    private final Map<String, Long> towerInfoVersions = new ConcurrentHashMap<String, Long>();
    private final Map<String, Object> commandBodies = new ConcurrentHashMap<String, Object>();
    private final Map<String, Long> commandVersions = new ConcurrentHashMap<String, Long>();
    private final Map<String, String> reconnectPauseReasons = new ConcurrentHashMap<String, String>();
    private final Map<String, Long> reconnectPausedAtMs = new ConcurrentHashMap<String, Long>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public synchronized void connect(String key, String wsUrl) {
        if (isReconnectPaused(key)) {
            String reason = reconnectPauseReasons.get(key);
            throw new IllegalStateException("闂備胶鍘ч〃搴㈢濠婂嫭鍙忛柍鍝勬噺閻撳倻鈧箍鍎遍悧蹇曠不閸撗€鍋撻崷顓х劸鐎殿喛娉涢埢鎾圭疀濞戞锛?" + reason + ")");
        }
        connectInternal(key, wsUrl);
    }

    public synchronized void connectManually(String key, String wsUrl) {
        clearReconnectPause(key);
        connectInternal(key, wsUrl);
    }

    public boolean isReconnectPaused(String key) {
        return reconnectPauseReasons.containsKey(key);
    }

    public String getReconnectPauseReason(String key) {
        return reconnectPauseReasons.get(key);
    }

    private void connectInternal(String key, String wsUrl) {
        XyzwWsClient existing = clients.get(key);
        if (existing != null) {
            if (existing.isOpen()) {
                logger.info("WebSocket \u5df2\u8fde\u63a5\uff0c\u8df3\u8fc7\u91cd\u590d\u8fde\u63a5 token={}", key);
                return;
            }
            if (existing.isConnecting() && existing.getConnectingAgeMs() <= CONNECTING_STALE_MS) {
                logger.info("WebSocket \u6b63\u5728\u8fde\u63a5\u4e2d\uff0c\u8df3\u8fc7\u91cd\u590d\u8fde\u63a5 token={} connectingAgeMs={}", key, existing.getConnectingAgeMs());
                return;
            }
            closeClientQuietly(existing, "replace stale connection");
            clients.remove(key);
            logger.info("WebSocket \u53d1\u73b0\u5931\u6548\u8fde\u63a5\uff0c\u51c6\u5907\u91cd\u8fde token={}", key);
        }

        clearCachedState(key);
        try {
            XyzwWsClient client = new XyzwWsClient(new URI(wsUrl), scheduler, key, this::handlePacket, this::handleClosed);
            clients.put(key, client);
            client.connect();
            logger.info("WebSocket \u5f00\u59cb\u8fde\u63a5 token={}", key);
        } catch (URISyntaxException ex) {
            logger.warn("WS \u5730\u5740\u65e0\u6548: {}", wsUrl, ex);
            throw new IllegalArgumentException("WS \u5730\u5740\u65e0\u6548");
        }
    }

    public synchronized void disconnect(String key) {
        XyzwWsClient existing = clients.remove(key);
        if (existing != null) {
            closeClientQuietly(existing, "disconnect");
        }
        clearCachedState(key);
        logger.info("WebSocket \u5df2\u65ad\u5f00 token={}", key);
    }

    public boolean isConnected(String key) {
        XyzwWsClient client = clients.get(key);
        return client != null && client.isOpen();
    }

    public void sendBottleHelperRestart(String key, int bottleType) {
        XyzwWsClient client = getConnectedClient(key);
        client.sendCommand("bottlehelper_stop", buildBottleBody(bottleType));
        scheduler.schedule(() -> client.sendCommand("bottlehelper_start", buildBottleBody(bottleType)), 500, TimeUnit.MILLISECONDS);
    }

    public void requestRoleInfo(String key) {
        XyzwWsClient client = getConnectedClient(key);
        logger.info("\u53d1\u9001\u8eab\u4efd\u724c\u8bf7\u6c42 token={}", key);
        client.sendCommand("role_getroleinfo", buildRoleInfoBody());
    }

    public void extendHangUp(String key) {
        XyzwWsClient client = getConnectedClient(key);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("isSkipShareCard", true);
        body.put("type", 2);
        logger.info("\u6302\u673a\u52a0\u949f token={}", key);
        for (int i = 0; i < 4; i++) {
            int delay = i * 300;
            scheduler.schedule(() -> client.sendCommand("system_mysharecallback", body), delay, TimeUnit.MILLISECONDS);
        }
        scheduler.schedule(() -> client.sendCommand("role_getroleinfo", buildRoleInfoBody()), 1500, TimeUnit.MILLISECONDS);
    }

    public void claimHangUpReward(String key) {
        XyzwWsClient client = getConnectedClient(key);
        logger.info("\u9886\u53d6\u6302\u673a\u5956\u52b1 token={}", key);

        client.sendCommand("system_mysharecallback", new LinkedHashMap<String, Object>());
        scheduler.schedule(() -> client.sendCommand("system_claimhangupreward", new LinkedHashMap<String, Object>()), 200, TimeUnit.MILLISECONDS);

        Map<String, Object> shareBody = new LinkedHashMap<String, Object>();
        shareBody.put("isSkipShareCard", true);
        shareBody.put("type", 2);
        scheduler.schedule(() -> client.sendCommand("system_mysharecallback", shareBody), 400, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> client.sendCommand("role_getroleinfo", buildRoleInfoBody()), 600, TimeUnit.MILLISECONDS);
    }

    public void requestTeamInfo(String key) {
        XyzwWsClient client = getConnectedClient(key);
        logger.info("\u53d1\u9001\u9635\u5bb9\u4fe1\u606f\u8bf7\u6c42 token={}", key);
        client.sendCommand("presetteam_getinfo", new LinkedHashMap<String, Object>());
    }

    public void switchTeam(String key, int teamId) {
        XyzwWsClient client = getConnectedClient(key);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("teamId", teamId);
        logger.info("\u5207\u6362\u9635\u5bb9 token={} teamId={}", key, teamId);
        client.sendCommand("presetteam_saveteam", body);
        scheduler.schedule(() -> client.sendCommand("presetteam_getinfo", new LinkedHashMap<String, Object>()), 500, TimeUnit.MILLISECONDS);
    }

    public void requestTowerInfo(String key) {
        XyzwWsClient client = getConnectedClient(key);
        logger.info("\u53d1\u9001\u54b8\u5c06\u5854\u4fe1\u606f\u8bf7\u6c42 token={}", key);
        client.sendCommand("tower_getinfo", new LinkedHashMap<String, Object>());
    }

    public void startTowerChallenge(String key) {
        XyzwWsClient client = getConnectedClient(key);
        logger.info("\u53d1\u8d77\u54b8\u5c06\u5854\u6311\u6218 token={}", key);
        client.sendCommand("fight_starttower", new LinkedHashMap<String, Object>());
        scheduler.schedule(() -> client.sendCommand("tower_getinfo", new LinkedHashMap<String, Object>()), 700, TimeUnit.MILLISECONDS);
    }

    public void sendCommand(String key, String cmd, Map<String, Object> body) {
        XyzwWsClient client = getConnectedClient(key);
        client.sendCommand(cmd, body);
    }

    public Map<String, Object> getRoleInfo(String key) {
        return roleInfos.get(key);
    }

    public long getRoleInfoVersion(String key) {
        Long version = roleInfoVersions.get(key);
        return version == null ? 0L : version;
    }

    public Map<String, Object> getTeamInfo(String key) {
        return teamInfos.get(key);
    }

    public long getTeamInfoVersion(String key) {
        Long version = teamInfoVersions.get(key);
        return version == null ? 0L : version;
    }

    public Map<String, Object> getTowerInfo(String key) {
        return towerInfos.get(key);
    }

    public long getTowerInfoVersion(String key) {
        Long version = towerInfoVersions.get(key);
        return version == null ? 0L : version;
    }

    public Map<String, Object> waitForRoleInfo(String key, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            Map<String, Object> info = roleInfos.get(key);
            if (info != null) {
                return info;
            }
            sleepQuietly(50);
        }
        return null;
    }

    public Map<String, Object> waitForRoleInfoUpdated(String key, long previousVersion, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            long currentVersion = getRoleInfoVersion(key);
            if (currentVersion > previousVersion) {
                return roleInfos.get(key);
            }
            sleepQuietly(50);
        }
        return null;
    }

    public Map<String, Object> waitForTeamInfoUpdated(String key, long previousVersion, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            long currentVersion = getTeamInfoVersion(key);
            if (currentVersion > previousVersion) {
                return teamInfos.get(key);
            }
            sleepQuietly(50);
        }
        return null;
    }

    public Map<String, Object> waitForTowerInfoUpdated(String key, long previousVersion, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            long currentVersion = getTowerInfoVersion(key);
            if (currentVersion > previousVersion) {
                return towerInfos.get(key);
            }
            sleepQuietly(50);
        }
        return null;
    }

    public long getCommandVersion(String key, String cmd) {
        Long version = commandVersions.get(commandKey(key, cmd));
        return version == null ? 0L : version;
    }

    public Object waitForCommandUpdated(String key, String cmd, long previousVersion, long timeoutMs) {
        String cacheKey = commandKey(key, cmd);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            long version = getCommandVersion(key, cmd);
            if (version > previousVersion) {
                return commandBodies.get(cacheKey);
            }
            sleepQuietly(50);
        }
        return null;
    }

    public Long extractHangUpRemainingSeconds(Map<String, Object> roleInfo) {
        HangUpMetrics metrics = buildHangUpMetrics(roleInfo);
        return metrics == null ? null : metrics.remainingSeconds;
    }

    public Long extractHangUpElapsedSeconds(Map<String, Object> roleInfo) {
        HangUpMetrics metrics = buildHangUpMetrics(roleInfo);
        return metrics == null ? null : metrics.elapsedSeconds;
    }

    public Long extractHangUpTotalSeconds(Map<String, Object> roleInfo) {
        HangUpMetrics metrics = buildHangUpMetrics(roleInfo);
        return metrics == null ? null : metrics.totalSeconds;
    }

    public Long extractTowerId(Map<String, Object> roleInfo) {
        Map<String, Object> tower = readTower(roleInfo);
        if (tower == null) {
            return null;
        }
        return asLong(tower.get("id"));
    }

    public Long extractTowerId(Map<String, Object> roleInfo, Map<String, Object> towerInfo) {
        Long fromRole = extractTowerId(roleInfo);
        if (fromRole != null) {
            return fromRole;
        }
        Map<String, Object> info = unwrapTowerInfo(towerInfo);
        if (info == null) {
            return null;
        }
        Long towerId = asLong(info.get("towerId"));
        if (towerId != null) {
            return towerId;
        }
        return asLong(info.get("id"));
    }

    public Long extractTowerEnergy(Map<String, Object> roleInfo) {
        Map<String, Object> tower = readTower(roleInfo);
        if (tower == null) {
            return null;
        }
        return asLong(tower.get("energy"));
    }

    public Long extractTowerEnergy(Map<String, Object> roleInfo, Map<String, Object> towerInfo) {
        Long fromRole = extractTowerEnergy(roleInfo);
        if (fromRole != null) {
            return fromRole;
        }
        Map<String, Object> info = unwrapTowerInfo(towerInfo);
        if (info == null) {
            return null;
        }
        return asLong(info.get("energy"));
    }

    private HangUpMetrics buildHangUpMetrics(Map<String, Object> roleInfo) {
        if (roleInfo == null) {
            return null;
        }
        Object roleObj = roleInfo.get("role");
        if (!(roleObj instanceof Map)) {
            return null;
        }
        Object hangUpObj = ((Map<?, ?>) roleObj).get("hangUp");
        if (!(hangUpObj instanceof Map)) {
            return null;
        }

        Long hangUpTime = asLong(((Map<?, ?>) hangUpObj).get("hangUpTime"));
        Long lastTime = asLong(((Map<?, ?>) hangUpObj).get("lastTime"));
        if (hangUpTime == null || lastTime == null) {
            return null;
        }

        long nowSec = System.currentTimeMillis() / 1000;
        long elapsed = Math.max(0L, nowSec - lastTime);
        long remaining = Math.max(0L, hangUpTime - elapsed);
        long elapsedClamped = Math.max(0L, hangUpTime - remaining);
        return new HangUpMetrics(remaining, elapsedClamped, Math.max(0L, hangUpTime));
    }

    private Map<String, Object> readTower(Map<String, Object> roleInfo) {
        if (roleInfo == null) {
            return null;
        }
        Object roleObj = roleInfo.get("role");
        if (!(roleObj instanceof Map)) {
            return null;
        }
        Object towerObj = ((Map<?, ?>) roleObj).get("tower");
        if (!(towerObj instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tower = (Map<String, Object>) towerObj;
        return tower;
    }

    private XyzwWsClient getConnectedClient(String key) {
        XyzwWsClient client = clients.get(key);
        if (client == null || !client.isOpen()) {
            throw new IllegalStateException("WebSocket \u672a\u8fde\u63a5");
        }
        return client;
    }

    private void clearCachedState(String key) {
        roleInfos.remove(key);
        roleInfoVersions.remove(key);
        teamInfos.remove(key);
        teamInfoVersions.remove(key);
        towerInfos.remove(key);
        towerInfoVersions.remove(key);
        purgeCommandCache(key);
    }

    private void closeClientQuietly(XyzwWsClient client, String reason) {
        try {
            client.closeByClient(reason);
        } catch (Exception ex) {
            logger.debug("\u5173\u95ed WebSocket \u5931\u8d25", ex);
        }
    }

    private void handleClosed(String key, int code, String reason, boolean remote, boolean closedByClient, long aliveMs, String lastCmd) {
        XyzwWsClient existing = clients.get(key);
        if (existing != null && !existing.isOpen()) {
            clients.remove(key);
        }
        if (remote && !closedByClient && code == 1006 && aliveMs >= KICKED_MIN_ALIVE_MS) {
            markReconnectPaused(key, "suspected-kicked");
            logger.warn("\u68c0\u6d4b\u5230\u7591\u4f3c\u88ab\u9876\u53f7\uff0c\u5df2\u6682\u505c\u81ea\u52a8\u91cd\u8fde token={} aliveMs={} lastCmd={}", key, aliveMs, lastCmd);
        }
    }

    private void markReconnectPaused(String key, String reason) {
        reconnectPauseReasons.put(key, reason);
        reconnectPausedAtMs.put(key, System.currentTimeMillis());
    }

    private void clearReconnectPause(String key) {
        reconnectPauseReasons.remove(key);
        reconnectPausedAtMs.remove(key);
    }

    private void handlePacket(String key, String cmd, Object body) {
        if (cmd == null || cmd.isEmpty()) {
            return;
        }

        recordCommandBody(key, cmd, body);

        if (("role_getroleinforesp".equals(cmd) || "role_getroleinfo".equals(cmd)) && body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> roleInfo = (Map<String, Object>) body;
            if (!roleInfo.isEmpty()) {
                roleInfos.put(key, roleInfo);
                roleInfoVersions.put(key, System.currentTimeMillis());
            }
            logger.info("\u6536\u5230\u8eab\u4efd\u724c\u6570\u636e token={}", key);
            return;
        }

        if (("presetteam_getinforesp".equals(cmd)
            || "presetteam_getinfo".equals(cmd)
            || "presetteam_saveteamresp".equals(cmd)
            || "presetteam_setteamresp".equals(cmd)) && body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> teamInfo = (Map<String, Object>) body;
            if (containsTeamPayload(teamInfo)) {
                Map<String, Object> merged = new LinkedHashMap<String, Object>();
                Map<String, Object> old = teamInfos.get(key);
                if (old != null) {
                    merged.putAll(old);
                }
                merged.putAll(teamInfo);
                teamInfos.put(key, merged);
                teamInfoVersions.put(key, System.currentTimeMillis());
            }
            logger.info("\u6536\u5230\u9635\u5bb9\u6570\u636e token={}", key);
            return;
        }

        if (("tower_getinforesp".equals(cmd) || "tower_getinfo".equals(cmd)) && body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> towerInfo = (Map<String, Object>) body;
            if (!towerInfo.isEmpty()) {
                towerInfos.put(key, towerInfo);
                towerInfoVersions.put(key, System.currentTimeMillis());
            }
            logger.info("\u6536\u5230\u54b8\u5c06\u5854\u6570\u636e token={}", key);
            return;
        }

        logger.debug("\u6536\u5230 WebSocket \u6d88\u606f cmd={} token={}", cmd, key);
    }

    private void recordCommandBody(String key, String cmd, Object body) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return;
        }
        if (body == null) {
            return;
        }
        String cacheKey = commandKey(key, cmd);
        commandBodies.put(cacheKey, body);
        commandVersions.put(cacheKey, System.currentTimeMillis());
    }

    private String commandKey(String key, String cmd) {
        return key + "|" + cmd;
    }

    private void purgeCommandCache(String key) {
        String prefix = key + "|";
        List<String> keys = new ArrayList<String>(commandBodies.keySet());
        for (String item : keys) {
            if (item.startsWith(prefix)) {
                commandBodies.remove(item);
                commandVersions.remove(item);
            }
        }
    }

    private Map<String, Object> unwrapTowerInfo(Map<String, Object> towerInfo) {
        if (towerInfo == null || towerInfo.isEmpty()) {
            return null;
        }
        Object nested = towerInfo.get("tower");
        if (nested instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tower = (Map<String, Object>) nested;
            return tower;
        }
        return towerInfo;
    }

    private boolean containsTeamPayload(Map<String, Object> teamInfo) {
        if (teamInfo == null || teamInfo.isEmpty()) {
            return false;
        }
        if (teamInfo.containsKey("presetTeamInfo") || teamInfo.containsKey("useTeamId")) {
            return true;
        }
        for (String key : teamInfo.keySet()) {
            if (key != null && key.matches("^\\d+$")) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> buildBottleBody(int bottleType) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("bottleType", bottleType);
        return body;
    }

    private Map<String, Object> buildRoleInfoBody() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("clientVersion", "1.65.3-wx");
        body.put("inviteUid", 0);
        body.put("platform", "hortor");
        body.put("platformExt", "mix");
        body.put("scene", "");
        return body;
    }

    private Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class HangUpMetrics {
        private final long remainingSeconds;
        private final long elapsedSeconds;
        private final long totalSeconds;

        private HangUpMetrics(long remainingSeconds, long elapsedSeconds, long totalSeconds) {
            this.remainingSeconds = remainingSeconds;
            this.elapsedSeconds = elapsedSeconds;
            this.totalSeconds = totalSeconds;
        }
    }
}