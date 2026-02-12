package com.xyzw.webhelper.xyzw;

import com.xyzw.webhelper.xyzw.dto.XyzwBottleRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwCarRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwDailyTaskRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwTeamRequest;
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/xyzw/ws")
public class XyzwWsController {
    private static final Logger logger = LoggerFactory.getLogger(XyzwWsController.class);
    private static final long CONNECT_TIMEOUT_MS = 6000;

    private final XyzwWsManager wsManager;

    public XyzwWsController(XyzwWsManager wsManager) {
        this.wsManager = wsManager;
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody XyzwWsConnectRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }

        String wsUrl = request.getWsUrl();
        if (isBlank(wsUrl)) {
            wsUrl = buildDefaultWsUrl(token);
        }

        wsManager.connectManually(token, wsUrl);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("wsUrl", wsUrl);
        data.put("status", "connecting");
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@RequestBody XyzwWsConnectRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        wsManager.disconnect(token);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/bottlehelper/restart")
    public ResponseEntity<Map<String, Object>> restartBottle(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }

        int bottleType = request.getBottleType() == null ? 0 : request.getBottleType();
        if (!ensureConnected(token)) {
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        logger.info("WebSocket 宸茶繛鎺ワ紝鍙戦€侀噸鍚綈瀛愭寚浠ゃ€俠ottleType={}", bottleType);
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
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }

        logger.info("韬唤鐗屾煡璇㈠紑濮?refresh={} token={}", refresh, token);

        if (refresh) {
            if (!ensureConnected(token)) {
                logger.warn("韬唤鐗屾煡璇㈠け璐ワ細WebSocket 杩炴帴瓒呮椂 token={}", token);
                return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
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

    @GetMapping("/legion/info")
    public ResponseEntity<Map<String, Object>> legionInfo(
        @RequestParam("token") String token,
        @RequestParam(value = "refresh", required = false, defaultValue = "true") boolean refresh
    ) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("淇变箰閮ㄤ俊鎭煡璇㈠け璐ワ細WebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        long beforeInfo = wsManager.getCommandVersion(token, "legion_getinforesp");
        long beforeInfoR = wsManager.getCommandVersion(token, "legion_getinforresp");
        if (refresh) {
            logger.info("鍙戦€佷勘涔愰儴淇℃伅璇锋眰 token={}", token);
            wsManager.sendCommand(token, "legion_getinfo", new LinkedHashMap<String, Object>());
        }

        Object body = wsManager.waitForCommandUpdated(token, "legion_getinforesp", beforeInfo, 5000);
        if (body == null) {
            body = wsManager.waitForCommandUpdated(token, "legion_getinforresp", beforeInfoR, 3000);
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("legionInfo", body);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/legion/signin")
    public ResponseEntity<Map<String, Object>> legionSignIn(@RequestBody XyzwWsConnectRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("淇变箰閮ㄧ鍒板け璐ワ細WebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }
        logger.info("鍙戦€佷勘涔愰儴绛惧埌璇锋眰 token={}", token);
        wsManager.sendCommand(token, "legion_signin", new LinkedHashMap<String, Object>());
        return ResponseEntity.ok(build(true, "success", null));
    }

    @GetMapping("/clubcar/list")
    public ResponseEntity<Map<String, Object>> clubCarList(
        @RequestParam("token") String token,
        @RequestParam(value = "refresh", required = false, defaultValue = "true") boolean refresh
    ) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("淇变箰閮ㄨ禌杞︽煡璇㈠け璐ワ細WebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        long before = wsManager.getCommandVersion(token, "car_getrolecarresp");
        long beforeAlt = wsManager.getCommandVersion(token, "car_getrolecar");
        if (refresh) {
            logger.info("鍙戦€佷勘涔愰儴璧涜溅鏌ヨ token={}", token);
            wsManager.sendCommand(token, "car_getrolecar", new LinkedHashMap<String, Object>());
        }
        Object body = wsManager.waitForCommandUpdated(token, "car_getrolecarresp", before, 5000);
        if (body == null) {
            body = wsManager.waitForCommandUpdated(token, "car_getrolecar", beforeAlt, 3000);
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("cars", body);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/clubcar/refresh")
    public ResponseEntity<Map<String, Object>> refreshClubCar(@RequestBody XyzwCarRequest request) {
        String token = request.getToken();
        Long carId = request.getCarId();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (carId == null) {
            return ResponseEntity.badRequest().body(build(false, "carId 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("鍒锋柊璧涜溅澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("carId", String.valueOf(carId));
        logger.info("鍒锋柊璧涜溅 token={} carId={}", token, carId);
        wsManager.sendCommand(token, "car_refresh", body);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/clubcar/send")
    public ResponseEntity<Map<String, Object>> sendClubCar(@RequestBody XyzwCarRequest request) {
        String token = request.getToken();
        Long carId = request.getCarId();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (carId == null) {
            return ResponseEntity.badRequest().body(build(false, "carId 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("鍙戣溅澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("carId", String.valueOf(carId));
        body.put("helperId", request.getHelperId() == null ? 0 : request.getHelperId());
        body.put("text", request.getText() == null ? "" : request.getText());
        body.put("isUpgrade", request.getIsUpgrade() != null && request.getIsUpgrade());
        logger.info("鍙戣溅 token={} carId={}", token, carId);
        wsManager.sendCommand(token, "car_send", body);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/clubcar/claim")
    public ResponseEntity<Map<String, Object>> claimClubCar(@RequestBody XyzwCarRequest request) {
        String token = request.getToken();
        Long carId = request.getCarId();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (carId == null) {
            return ResponseEntity.badRequest().body(build(false, "carId 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("鏀惰溅澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("carId", String.valueOf(carId));
        logger.info("鏀惰溅 token={} carId={}", token, carId);
        wsManager.sendCommand(token, "car_claim", body);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @GetMapping("/team/info")
    public ResponseEntity<Map<String, Object>> teamInfo(
        @RequestParam("token") String token,
        @RequestParam(value = "refresh", required = false, defaultValue = "true") boolean refresh
    ) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("闃靛鏌ヨ澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        long beforeVersion = wsManager.getTeamInfoVersion(token);
        if (refresh) {
            try {
                wsManager.requestTeamInfo(token);
            } catch (IllegalStateException ex) {
                logger.warn("闃靛鏌ヨ澶辫触锛歿}", ex.getMessage());
                return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
            }
        }

        Map<String, Object> data = buildTeamData(token, beforeVersion, refresh ? 5000 : 0);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/team/switch")
    public ResponseEntity<Map<String, Object>> switchTeam(@RequestBody XyzwTeamRequest request) {
        String token = request.getToken();
        Integer teamId = request.getTeamId();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (teamId == null || teamId < 1 || teamId > 4) {
            return ResponseEntity.badRequest().body(build(false, "teamId 蹇呴』鍦?1 鍒?4 涔嬮棿", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("鍒囨崲闃靛澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={} teamId={}", token, teamId);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        long beforeVersion = wsManager.getTeamInfoVersion(token);
        try {
            wsManager.switchTeam(token, teamId);
        } catch (IllegalStateException ex) {
            logger.warn("鍒囨崲闃靛澶辫触锛歿}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }

        Map<String, Object> data = buildTeamData(token, beforeVersion, 5000);
        data.put("requestedTeamId", teamId);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @GetMapping("/tower/info")
    public ResponseEntity<Map<String, Object>> towerInfo(
        @RequestParam("token") String token,
        @RequestParam(value = "refresh", required = false, defaultValue = "true") boolean refresh
    ) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("鍜稿皢濉旀煡璇㈠け璐ワ細WebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        long beforeTowerVersion = wsManager.getTowerInfoVersion(token);
        if (refresh) {
            try {
                wsManager.requestTowerInfo(token);
            } catch (IllegalStateException ex) {
                logger.warn("鍜稿皢濉旀煡璇㈠け璐ワ細{}", ex.getMessage());
                return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
            }
        }

        Map<String, Object> data = buildTowerData(token, beforeTowerVersion, refresh ? 5000 : 0);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/tower/challenge")
    public ResponseEntity<Map<String, Object>> towerChallenge(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("鍙戣捣鍜稿皢濉旀寫鎴樺け璐ワ細WebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        long beforeTowerVersion = wsManager.getTowerInfoVersion(token);
        try {
            wsManager.startTowerChallenge(token);
        } catch (IllegalStateException ex) {
            logger.warn("鍙戣捣鍜稿皢濉旀寫鎴樺け璐ワ細{}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }

        Map<String, Object> data = buildTowerData(token, beforeTowerVersion, 6000);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/hangup/extend")
    public ResponseEntity<Map<String, Object>> extendHangup(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("鎸傛満鍔犻挓澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        long beforeVersion = wsManager.getRoleInfoVersion(token);
        try {
            wsManager.extendHangUp(token);
        } catch (IllegalStateException ex) {
            logger.warn("鎸傛満鍔犻挓澶辫触锛歿}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }

        Map<String, Object> data = buildHangupData(token, beforeVersion, 6000);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/hangup/claim")
    public ResponseEntity<Map<String, Object>> claimHangupReward(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("棰嗗彇鎸傛満濂栧姳澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        long beforeVersion = wsManager.getRoleInfoVersion(token);
        try {
            wsManager.claimHangUpReward(token);
        } catch (IllegalStateException ex) {
            logger.warn("棰嗗彇鎸傛満濂栧姳澶辫触锛歿}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }

        Map<String, Object> data = buildHangupData(token, beforeVersion, 6000);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @GetMapping("/hangup/remaining")
    public ResponseEntity<Map<String, Object>> hangupRemaining(@RequestParam("token") String token) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("鏌ヨ鎸傛満鍓╀綑鏃堕棿澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        long beforeVersion = wsManager.getRoleInfoVersion(token);
        try {
            wsManager.requestRoleInfo(token);
        } catch (IllegalStateException ex) {
            logger.warn("鏌ヨ鎸傛満鍓╀綑鏃堕棿澶辫触锛歿}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }

        Map<String, Object> data = buildHangupData(token, beforeVersion, 6000);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/daily/run")
    public ResponseEntity<Map<String, Object>> runDailyTasks(@RequestBody XyzwDailyTaskRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 涓嶈兘涓虹┖", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("鎵ц姣忔棩浠诲姟澶辫触锛歐ebSocket 杩炴帴瓒呮椂 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 杩炴帴澶辫触", null));
        }

        XyzwDailyTaskRequest.DailySettings settings = request.getSettings();
        boolean claimBottle = settings == null || settings.getClaimBottle() == null || settings.getClaimBottle();
        boolean payRecruit = settings != null && Boolean.TRUE.equals(settings.getPayRecruit());
        boolean openBox = settings == null || settings.getOpenBox() == null || settings.getOpenBox();
        boolean arenaEnable = settings != null && settings.getArenaEnable() != null && settings.getArenaEnable();
        boolean claimHangUp = settings == null || settings.getClaimHangUp() == null || settings.getClaimHangUp();
        boolean claimEmail = settings == null || settings.getClaimEmail() == null || settings.getClaimEmail();
        boolean blackMarketPurchase = settings != null && Boolean.TRUE.equals(settings.getBlackMarketPurchase());
        int arenaFormation = normalizeTeamId(settings == null ? null : settings.getArenaFormation());
        int bossFormation = normalizeTeamId(settings == null ? null : settings.getBossFormation());
        int bossTimes = normalizeBossTimes(settings == null ? null : settings.getBossTimes());

        List<String> executed = new ArrayList<String>();
        long roleVersionBefore = wsManager.getRoleInfoVersion(token);

        try {
            // 鍩虹锛氬垎浜€侀€侀噾甯併€佹嫑鍕熴€佺偣閲?            sendDaily(token, executed, "system_mysharecallback", mapOf("isSkipShareCard", true, "type", 2), 250);
            sendDaily(token, executed, "friend_batch", mapOf(), 250);
            sendDaily(token, executed, "hero_recruit", mapOf("recruitType", 3, "recruitNumber", 1), 300);
            if (payRecruit) {
                sendDaily(token, executed, "hero_recruit", mapOf("recruitType", 1, "recruitNumber", 1), 300);
            }
            sendDaily(token, executed, "system_buygold", mapOf("buyNum", 1), 220);
            sendDaily(token, executed, "system_buygold", mapOf("buyNum", 1), 220);
            sendDaily(token, executed, "system_buygold", mapOf("buyNum", 1), 220);

            // 鎸傛満
            if (claimHangUp) {
                sendDaily(token, executed, "system_claimhangupreward", mapOf(), 250);
                sendDaily(token, executed, "system_mysharecallback", mapOf("isSkipShareCard", true, "type", 2), 200);
                sendDaily(token, executed, "system_mysharecallback", mapOf("isSkipShareCard", true, "type", 2), 200);
                sendDaily(token, executed, "system_mysharecallback", mapOf("isSkipShareCard", true, "type", 2), 200);
                sendDaily(token, executed, "system_mysharecallback", mapOf("isSkipShareCard", true, "type", 2), 200);
            }

            // 缃愬瓙
            sendDaily(token, executed, "bottlehelper_stop", mapOf("bottleType", 0), 200);
            sendDaily(token, executed, "bottlehelper_start", mapOf("bottleType", 0), 250);
            if (claimBottle) {
                sendDaily(token, executed, "bottlehelper_claim", mapOf("bottleType", 0), 250);
            }

            // 瀹濈
            if (openBox) {
                sendDaily(token, executed, "item_openbox", mapOf("itemId", 2001, "number", 10), 250);
            }

            // 绔炴妧鍦?            if (arenaEnable) {
                sendDaily(token, executed, "presetteam_saveteam", mapOf("teamId", arenaFormation), 350);
                sendDaily(token, executed, "arena_startarea", mapOf(), 350);
                long version = wsManager.getCommandVersion(token, "arena_getareatargetresp");
                sendDaily(token, executed, "arena_getareatarget", mapOf(), 300);
                Object arenaResp = wsManager.waitForCommandUpdated(token, "arena_getareatargetresp", version, 3000);
                Long targetId = pickArenaTargetId(arenaResp);
                if (targetId != null && targetId > 0) {
                    sendDaily(token, executed, "fight_startareaarena", mapOf("targetId", targetId), 700);
                } else {
                    logger.info("姣忔棩浠诲姟绔炴妧鍦猴細鏈幏鍙栧埌鐩爣锛岃烦杩囨垬鏂?token={}", token);
                }
            }

            // BOSS锛堢畝鍖栫増锛?            if (bossTimes > 0) {
                sendDaily(token, executed, "presetteam_saveteam", mapOf("teamId", bossFormation), 350);
                for (int i = 0; i < bossTimes; i++) {
                    sendDaily(token, executed, "fight_startlegionboss", mapOf(), 900);
                }
            }

            // 閭欢銆侀粦甯?            if (claimEmail) {
                sendDaily(token, executed, "mail_claimallattachment", mapOf(), 250);
            }
            if (blackMarketPurchase) {
                sendDaily(token, executed, "store_purchase", mapOf("goodsId", 1), 300);
            }

            // 浠诲姟濂栧姳
            for (int taskId = 1; taskId <= 10; taskId++) {
                sendDaily(token, executed, "task_claimdailypoint", mapOf("taskId", taskId), 180);
            }
            sendDaily(token, executed, "task_claimdailyreward", mapOf(), 250);
            sendDaily(token, executed, "task_claimweekreward", mapOf(), 250);

            // 鍒锋柊瑙掕壊淇℃伅锛屼粎鐢ㄤ簬姣忔棩浠诲姟杩涘害鍚屾
            wsManager.requestRoleInfo(token);
            Map<String, Object> updatedRole = wsManager.waitForRoleInfoUpdated(token, roleVersionBefore, 5000);
            if (updatedRole == null) {
                updatedRole = wsManager.getRoleInfo(token);
            }

            Map<String, Object> data = new HashMap<String, Object>();
            data.put("executedCount", executed.size());
            data.put("executedCommands", executed);
            Integer dailyPoint = extractDailyPoint(updatedRole);
            if (dailyPoint != null) {
                data.put("dailyPoint", dailyPoint);
            }
            return ResponseEntity.ok(build(true, "success", data));
        } catch (Exception ex) {
            logger.warn("鎵ц姣忔棩浠诲姟澶辫触 token={} msg={}", token, ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(build(false, "鎵ц姣忔棩浠诲姟澶辫触: " + ex.getMessage(), null));
        }
    }

    private Map<String, Object> buildHangupData(String token, long beforeVersion, long timeoutMs) {
        Map<String, Object> updated = wsManager.waitForRoleInfoUpdated(token, beforeVersion, timeoutMs);
        Map<String, Object> roleInfo = updated != null ? updated : wsManager.getRoleInfo(token);

        Map<String, Object> data = new HashMap<String, Object>();
        Long remainingSeconds = wsManager.extractHangUpRemainingSeconds(roleInfo);
        Long elapsedSeconds = wsManager.extractHangUpElapsedSeconds(roleInfo);
        Long totalSeconds = wsManager.extractHangUpTotalSeconds(roleInfo);
        if (remainingSeconds != null) {
            data.put("remainingSeconds", remainingSeconds);
        }
        if (elapsedSeconds != null) {
            data.put("elapsedSeconds", elapsedSeconds);
        }
        if (totalSeconds != null) {
            data.put("totalSeconds", totalSeconds);
        }
        return data;
    }

    private Map<String, Object> buildTeamData(String token, long beforeVersion, long timeoutMs) {
        Map<String, Object> updated = timeoutMs > 0
            ? wsManager.waitForTeamInfoUpdated(token, beforeVersion, timeoutMs)
            : null;
        Map<String, Object> teamInfo = updated != null ? updated : wsManager.getTeamInfo(token);

        Map<String, Object> data = new HashMap<String, Object>();
        if (teamInfo != null) {
            data.put("teamInfo", teamInfo);
            Integer currentTeamId = extractUseTeamId(teamInfo);
            if (currentTeamId != null) {
                data.put("currentTeamId", currentTeamId);
            }
        }
        return data;
    }

    private Map<String, Object> buildTowerData(
        String token,
        long beforeTowerVersion,
        long timeoutMs
    ) {
        Map<String, Object> towerUpdated = timeoutMs > 0
            ? wsManager.waitForTowerInfoUpdated(token, beforeTowerVersion, timeoutMs)
            : null;
        Map<String, Object> roleInfo = wsManager.getRoleInfo(token);
        Map<String, Object> towerInfo = towerUpdated != null ? towerUpdated : wsManager.getTowerInfo(token);

        Map<String, Object> data = new HashMap<String, Object>();
        Long towerId = wsManager.extractTowerId(roleInfo, towerInfo);
        Long energy = wsManager.extractTowerEnergy(roleInfo, towerInfo);

        data.put("towerId", towerId == null ? 0L : towerId);
        data.put("energy", energy == null ? 0L : energy);
        data.put("floor", formatTowerFloor(towerId));
        return data;
    }

    private String buildDefaultWsUrl(String token) {
        try {
            return "wss://xxz-xyzw.hortorgames.com/agent?p=" +
                URLEncoder.encode(token, "UTF-8") +
                "&e=x&lang=chinese";
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException("涓嶆敮鎸佺殑缂栫爜 UTF-8", ex);
        }
    }

    private boolean ensureConnected(String token) {
        if (wsManager.isConnected(token)) {
            return true;
        }
        if (wsManager.isReconnectPaused(token)) {
            logger.warn("鑷姩閲嶈繛宸叉殏鍋?token={} reason={}", token, wsManager.getReconnectPauseReason(token));
            return false;
        }
        String wsUrl = buildDefaultWsUrl(token);
        try {
            wsManager.connect(token, wsUrl);
        } catch (IllegalStateException ex) {
            logger.warn("鑷姩閲嶈繛琚嫆缁?token={} reason={}", token, ex.getMessage());
            return false;
        }
        boolean connected = waitForConnection(token, CONNECT_TIMEOUT_MS);
        if (!connected) {
            logger.warn("WebSocket 杩炴帴瓒呮椂锛岀枒浼?token 澶辨晥/浼氳瘽杩囨湡/鍙傛暟閿欒 token={}", token);
        }
        return connected;
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

    private Integer extractUseTeamId(Map<String, Object> teamInfo) {
        Integer direct = asInt(teamInfo.get("useTeamId"));
        if (direct != null) {
            return direct;
        }
        Object nested = findByKey(teamInfo, "useTeamId");
        return asInt(nested);
    }

    private Object findByKey(Object node, String key) {
        if (node instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) node;
            if (map.containsKey(key)) {
                return map.get(key);
            }
            for (Object value : map.values()) {
                Object found = findByKey(value, key);
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) {
                Object found = findByKey(item, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Integer asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String formatTowerFloor(Long towerId) {
        if (towerId == null || towerId < 0) {
            return "0 - 0";
        }
        long floor = (towerId / 10) + 1;
        long layer = (towerId % 10) + 1;
        return floor + " - " + layer;
    }

    private void sendDaily(String token, List<String> executed, String cmd, Map<String, Object> body, long delayMs) {
        wsManager.sendCommand(token, cmd, body);
        executed.add(cmd);
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("\u4efb\u52a1\u6267\u884c\u88ab\u4e2d\u65ad");
            }
        }
    }

    private Integer extractDailyPoint(Map<String, Object> roleInfo) {
        if (roleInfo == null) {
            return null;
        }
        Object roleObj = roleInfo.get("role");
        if (!(roleObj instanceof Map)) {
            return null;
        }
        Object dailyTaskObj = ((Map<?, ?>) roleObj).get("dailyTask");
        if (!(dailyTaskObj instanceof Map)) {
            return null;
        }
        Object pointObj = ((Map<?, ?>) dailyTaskObj).get("dailyPoint");
        if (pointObj instanceof Number) {
            return ((Number) pointObj).intValue();
        }
        if (pointObj instanceof String) {
            try {
                return Integer.parseInt((String) pointObj);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Long pickArenaTargetId(Object arenaResp) {
        if (!(arenaResp instanceof Map)) {
            return null;
        }
        Object body = ((Map<?, ?>) arenaResp).get("body");
        Object source = body != null ? body : arenaResp;
        return findTargetId(source);
    }

    private Long findTargetId(Object node) {
        if (node instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) node;
            Long roleId = asLong(map.get("roleId"));
            if (roleId != null) {
                return roleId;
            }
            Long targetId = asLong(map.get("targetId"));
            if (targetId != null) {
                return targetId;
            }
            Long id = asLong(map.get("id"));
            if (id != null) {
                return id;
            }
            for (Object value : map.values()) {
                Long found = findTargetId(value);
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) {
                Long found = findTargetId(item);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

    private int normalizeTeamId(Integer teamId) {
        if (teamId == null || teamId < 1 || teamId > 4) {
            return 1;
        }
        return teamId;
    }

    private int normalizeBossTimes(Integer times) {
        if (times == null || times < 0) {
            return 0;
        }
        if (times > 4) {
            return 4;
        }
        return times;
    }

    private Map<String, Object> mapOf() {
        return new LinkedHashMap<String, Object>();
    }

    private Map<String, Object> mapOf(String key1, Object value1) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key1, value1);
        return map;
    }

    private Map<String, Object> mapOf(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}