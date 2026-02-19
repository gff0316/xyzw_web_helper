package com.xyzw.webhelper.xyzw;

import com.xyzw.webhelper.xyzw.dto.XyzwBottleRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwCarRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwDailyTaskRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwTeamRequest;
import com.xyzw.webhelper.xyzw.dto.XyzwWsConnectRequest;
import com.xyzw.webhelper.xyzw.XyzwTaskLogService;
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
    private static final long DAILY_WAIT_TIMEOUT_MS = 5000;
    private static final long DAILY_STEP_MIN_DELAY_MS = 800;
    private static final long RECRUIT_STEP_DELAY_MS = 1200;

    private final XyzwWsManager wsManager;
    private final XyzwTaskLogService taskLogService;

    public XyzwWsController(XyzwWsManager wsManager, XyzwTaskLogService taskLogService) {
        this.wsManager = wsManager;
        this.taskLogService = taskLogService;
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody XyzwWsConnectRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
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
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        wsManager.disconnect(token);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/bottlehelper/restart")
    public ResponseEntity<Map<String, Object>> restartBottle(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }

        int bottleType = request.getBottleType() == null ? 0 : request.getBottleType();
        if (!ensureConnected(token)) {
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        logger.info("WebSocket 已连接，发送重启罐子指令。bottleType={}", bottleType);
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
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }

        logger.info("身份牌查询开始 refresh={} token={}", refresh, token);

        if (refresh) {
            if (!ensureConnected(token)) {
                logger.warn("身份牌查询失败：WebSocket 连接超时 token={}", token);
                return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
            }
            try {
                wsManager.requestRoleInfo(token);
            } catch (IllegalStateException ex) {
                logger.warn("身份牌查询失败：{}", ex.getMessage());
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
                    logger.warn("身份牌查询失败：{}", ex.getMessage());
                    return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
                }
            }
        }

        Map<String, Object> data = new HashMap<String, Object>();
        if (roleInfo != null) {
            data.put("roleInfo", roleInfo);
            logger.info("身份牌查询成功 token={}", token);
        } else {
            logger.warn("身份牌查询结果为空 token={}", token);
        }
        return ResponseEntity.ok(build(true, "success", data));
    }

    @GetMapping("/legion/info")
    public ResponseEntity<Map<String, Object>> legionInfo(
        @RequestParam("token") String token,
        @RequestParam(value = "refresh", required = false, defaultValue = "true") boolean refresh
    ) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("俱乐部信息查询失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        long beforeInfo = wsManager.getCommandVersion(token, "legion_getinforesp");
        long beforeInfoR = wsManager.getCommandVersion(token, "legion_getinforresp");
        if (refresh) {
            logger.info("发送俱乐部信息请求 token={}", token);
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
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("俱乐部签到失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }
        logger.info("发送俱乐部签到请求 token={}", token);
        wsManager.sendCommand(token, "legion_signin", new LinkedHashMap<String, Object>());
        return ResponseEntity.ok(build(true, "success", null));
    }

    @GetMapping("/clubcar/list")
    public ResponseEntity<Map<String, Object>> clubCarList(
        @RequestParam("token") String token,
        @RequestParam(value = "refresh", required = false, defaultValue = "true") boolean refresh
    ) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("俱乐部赛车查询失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        long before = wsManager.getCommandVersion(token, "car_getrolecarresp");
        long beforeAlt = wsManager.getCommandVersion(token, "car_getrolecar");
        if (refresh) {
            logger.info("发送俱乐部赛车查询 token={}", token);
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
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (carId == null) {
            return ResponseEntity.badRequest().body(build(false, "carId 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("刷新赛车失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("carId", String.valueOf(carId));
        logger.info("刷新赛车 token={} carId={}", token, carId);
        wsManager.sendCommand(token, "car_refresh", body);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/clubcar/send")
    public ResponseEntity<Map<String, Object>> sendClubCar(@RequestBody XyzwCarRequest request) {
        String token = request.getToken();
        Long carId = request.getCarId();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (carId == null) {
            return ResponseEntity.badRequest().body(build(false, "carId 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("发车失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("carId", String.valueOf(carId));
        body.put("helperId", request.getHelperId() == null ? 0 : request.getHelperId());
        body.put("text", request.getText() == null ? "" : request.getText());
        body.put("isUpgrade", request.getIsUpgrade() != null && request.getIsUpgrade());
        logger.info("发车 token={} carId={}", token, carId);
        wsManager.sendCommand(token, "car_send", body);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @PostMapping("/clubcar/claim")
    public ResponseEntity<Map<String, Object>> claimClubCar(@RequestBody XyzwCarRequest request) {
        String token = request.getToken();
        Long carId = request.getCarId();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (carId == null) {
            return ResponseEntity.badRequest().body(build(false, "carId 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("收车失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("carId", String.valueOf(carId));
        logger.info("收车 token={} carId={}", token, carId);
        wsManager.sendCommand(token, "car_claim", body);
        return ResponseEntity.ok(build(true, "success", null));
    }

    @GetMapping("/team/info")
    public ResponseEntity<Map<String, Object>> teamInfo(
        @RequestParam("token") String token,
        @RequestParam(value = "refresh", required = false, defaultValue = "true") boolean refresh
    ) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("阵容查询失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        long beforeVersion = wsManager.getTeamInfoVersion(token);
        if (refresh) {
            try {
                wsManager.requestTeamInfo(token);
            } catch (IllegalStateException ex) {
                logger.warn("阵容查询失败：{}", ex.getMessage());
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
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (teamId == null || teamId < 1 || teamId > 4) {
            return ResponseEntity.badRequest().body(build(false, "teamId 必须在 1 到 4 之间", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("切换阵容失败：WebSocket 连接超时 token={} teamId={}", token, teamId);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        long beforeVersion = wsManager.getTeamInfoVersion(token);
        try {
            wsManager.switchTeam(token, teamId);
        } catch (IllegalStateException ex) {
            logger.warn("切换阵容失败：{}", ex.getMessage());
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
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("咸将塔查询失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        long beforeTowerVersion = wsManager.getTowerInfoVersion(token);
        if (refresh) {
            try {
                wsManager.requestTowerInfo(token);
            } catch (IllegalStateException ex) {
                logger.warn("咸将塔查询失败：{}", ex.getMessage());
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
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("发起咸将塔挑战失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        long beforeTowerVersion = wsManager.getTowerInfoVersion(token);
        try {
            wsManager.startTowerChallenge(token);
        } catch (IllegalStateException ex) {
            logger.warn("发起咸将塔挑战失败：{}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }

        Map<String, Object> data = buildTowerData(token, beforeTowerVersion, 6000);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/hangup/extend")
    public ResponseEntity<Map<String, Object>> extendHangup(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("挂机加钟失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        long beforeVersion = wsManager.getRoleInfoVersion(token);
        try {
            wsManager.extendHangUp(token);
        } catch (IllegalStateException ex) {
            logger.warn("挂机加钟失败：{}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }

        Map<String, Object> data = buildHangupData(token, beforeVersion, 6000);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/hangup/claim")
    public ResponseEntity<Map<String, Object>> claimHangupReward(@RequestBody XyzwBottleRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("领取挂机奖励失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        long beforeVersion = wsManager.getRoleInfoVersion(token);
        try {
            wsManager.claimHangUpReward(token);
        } catch (IllegalStateException ex) {
            logger.warn("领取挂机奖励失败：{}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }

        Map<String, Object> data = buildHangupData(token, beforeVersion, 6000);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @GetMapping("/hangup/remaining")
    public ResponseEntity<Map<String, Object>> hangupRemaining(@RequestParam("token") String token) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        if (!ensureConnected(token)) {
            logger.warn("查询挂机剩余时间失败：WebSocket 连接超时 token={}", token);
            return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
        }

        long beforeVersion = wsManager.getRoleInfoVersion(token);
        try {
            wsManager.requestRoleInfo(token);
        } catch (IllegalStateException ex) {
            logger.warn("查询挂机剩余时间失败：{}", ex.getMessage());
            return ResponseEntity.badRequest().body(build(false, ex.getMessage(), null));
        }

        Map<String, Object> data = buildHangupData(token, beforeVersion, 6000);
        return ResponseEntity.ok(build(true, "success", data));
    }

    @PostMapping("/daily/run")
    public ResponseEntity<Map<String, Object>> runDailyTasks(@RequestBody XyzwDailyTaskRequest request) {
        String token = request.getToken();
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(build(false, "token 不能为空", null));
        }
        String requestedTaskName = normalizeText(request.getTaskName());
        boolean manualTask = requestedTaskName == null;
        String effectiveTaskName = manualTask ? "手工任务/当前账号" : requestedTaskName;
        wsManager.bindTaskLabel(token, effectiveTaskName);

        try {
            if (!ensureConnected(token)) {
                logger.warn("执行每日任务失败：WebSocket 连接超时 token={}", token);
                return ResponseEntity.badRequest().body(build(false, "WebSocket 连接失败", null));
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

            // 基础：分享、送金币、招募、点金
            sendDailyAndWait(
                token,
                executed,
                "system_mysharecallback",
                mapOf("isSkipShareCard", true, "type", 2),
                DAILY_WAIT_TIMEOUT_MS,
                250,
                "syncresp"
            );
            sendDailyAndWait(token, executed, "friend_batch", mapOf(), DAILY_WAIT_TIMEOUT_MS, 250, "friend_batchresp");
            sendDailyAndWait(
                token,
                executed,
                "hero_recruit",
                mapOf("recruitType", 3, "recruitNumber", 1),
                DAILY_WAIT_TIMEOUT_MS,
                RECRUIT_STEP_DELAY_MS,
                "hero_recruitresp"
            );
            if (payRecruit) {
                sendDailyAndWait(
                    token,
                    executed,
                    "hero_recruit",
                    mapOf("recruitType", 1, "recruitNumber", 1),
                    DAILY_WAIT_TIMEOUT_MS,
                    RECRUIT_STEP_DELAY_MS,
                    "hero_recruitresp"
                );
            }
            sendDaily(token, executed, "system_buygold", mapOf("buyNum", 1), 220);
            sendDaily(token, executed, "system_buygold", mapOf("buyNum", 1), 220);
            sendDaily(token, executed, "system_buygold", mapOf("buyNum", 1), 220);

            // 挂机
            if (claimHangUp) {
                sendDailyAndWait(
                    token,
                    executed,
                    "system_claimhangupreward",
                    mapOf(),
                    DAILY_WAIT_TIMEOUT_MS,
                    250,
                    "system_claimhanguprewardresp"
                );
                sendDailyAndWait(
                    token,
                    executed,
                    "system_mysharecallback",
                    mapOf("isSkipShareCard", true, "type", 2),
                    DAILY_WAIT_TIMEOUT_MS,
                    200,
                    "syncresp"
                );
                sendDailyAndWait(
                    token,
                    executed,
                    "system_mysharecallback",
                    mapOf("isSkipShareCard", true, "type", 2),
                    DAILY_WAIT_TIMEOUT_MS,
                    200,
                    "syncresp"
                );
                sendDailyAndWait(
                    token,
                    executed,
                    "system_mysharecallback",
                    mapOf("isSkipShareCard", true, "type", 2),
                    DAILY_WAIT_TIMEOUT_MS,
                    200,
                    "syncresp"
                );
                sendDailyAndWait(
                    token,
                    executed,
                    "system_mysharecallback",
                    mapOf("isSkipShareCard", true, "type", 2),
                    DAILY_WAIT_TIMEOUT_MS,
                    200,
                    "syncresp"
                );
            }

            // 罐子
            sendDailyAndWait(
                token,
                executed,
                "bottlehelper_stop",
                mapOf("bottleType", 0),
                DAILY_WAIT_TIMEOUT_MS,
                200,
                "bottlehelper_stopresp"
            );
            sendDailyAndWait(
                token,
                executed,
                "bottlehelper_start",
                mapOf("bottleType", 0),
                DAILY_WAIT_TIMEOUT_MS,
                250,
                "bottlehelper_startresp",
                "syncresp"
            );
            if (claimBottle) {
                sendDailyAndWaitOptional(
                    token,
                    executed,
                    "bottlehelper_claim",
                    mapOf("bottleType", 0),
                    DAILY_WAIT_TIMEOUT_MS,
                    250,
                    "bottlehelper_claimresp",
                    "syncresp"
                );
            }

            // 宝箱
            if (openBox) {
                sendDailyAndWait(
                    token,
                    executed,
                    "item_openbox",
                    mapOf("itemId", 2001, "number", 10),
                    DAILY_WAIT_TIMEOUT_MS,
                    250,
                    "item_openboxresp",
                    "syncresp"
                );
            }

            // 竞技场
            if (arenaEnable) {
                sendDaily(token, executed, "presetteam_saveteam", mapOf("teamId", arenaFormation), 350);
                sendDaily(token, executed, "arena_startarea", mapOf(), 350);
                long version = wsManager.getCommandVersion(token, "arena_getareatargetresp");
                sendDaily(token, executed, "arena_getareatarget", mapOf(), 300);
                Object arenaResp = wsManager.waitForCommandUpdated(token, "arena_getareatargetresp", version, 3000);
                Long targetId = pickArenaTargetId(arenaResp);
                if (targetId != null && targetId > 0) {
                    sendDaily(token, executed, "fight_startareaarena", mapOf("targetId", targetId), 700);
                } else {
                    logger.info("每日任务竞技场：未获取到目标，跳过战斗 token={}", token);
                }
            }

            // BOSS（简化版）
            if (bossTimes > 0) {
                sendDaily(token, executed, "presetteam_saveteam", mapOf("teamId", bossFormation), 350);
                for (int i = 0; i < bossTimes; i++) {
                    sendDaily(token, executed, "fight_startlegionboss", mapOf(), 900);
                }
            }

            // 邮件、黑市
            if (claimEmail) {
                sendDailyAndWait(
                    token,
                    executed,
                    "mail_claimallattachment",
                    mapOf(),
                    DAILY_WAIT_TIMEOUT_MS,
                    250,
                    "mail_claimallattachmentresp",
                    "syncresp"
                );
            }
            if (blackMarketPurchase) {
                sendDailyAndWait(
                    token,
                    executed,
                    "store_purchase",
                    mapOf("goodsId", 1),
                    DAILY_WAIT_TIMEOUT_MS,
                    300,
                    "store_buyresp",
                    "syncresp"
                );
            }

            // 任务奖励
            for (int taskId = 1; taskId <= 10; taskId++) {
                sendDailyAndWaitOptional(
                    token,
                    executed,
                    "task_claimdailypoint",
                    mapOf("taskId", taskId),
                    DAILY_WAIT_TIMEOUT_MS,
                    180,
                    "task_claimdailypointresp",
                    "syncresp"
                );
            }
            sendDailyAndWaitOptional(
                token,
                executed,
                "task_claimdailyreward",
                mapOf(),
                DAILY_WAIT_TIMEOUT_MS,
                250,
                "task_claimdailyrewardresp",
                "syncrewardresp",
                "syncresp"
            );
            sendDailyAndWaitOptional(
                token,
                executed,
                "task_claimweekreward",
                mapOf(),
                DAILY_WAIT_TIMEOUT_MS,
                250,
                "task_claimweekrewardresp",
                "syncrewardresp",
                "syncresp"
            );

            // 刷新角色信息，仅用于每日任务进度同步
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
            if (manualTask) {
                taskLogService.recordTaskResult(
                    token,
                    XyzwTaskLogService.TYPE_MANUAL_TASK,
                    "手工任务",
                    extractTaskTokenName(effectiveTaskName),
                    true,
                    "执行成功"
                );
            }
            return ResponseEntity.ok(build(true, "success", data));
        } catch (Exception ex) {
            logger.warn("执行每日任务失败 token={} msg={}", token, ex.getMessage(), ex);
            if (manualTask) {
                taskLogService.recordTaskResult(
                    token,
                    XyzwTaskLogService.TYPE_MANUAL_TASK,
                    "手工任务",
                    extractTaskTokenName(effectiveTaskName),
                    false,
                    ex.getMessage()
                );
            }
            return ResponseEntity.badRequest().body(build(false, "执行每日任务失败: " + ex.getMessage(), null));
        } finally {
            wsManager.clearTaskLabel(token);
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
            throw new IllegalStateException("不支持的编码 UTF-8", ex);
        }
    }

    private boolean ensureConnected(String token) {
        if (wsManager.isConnected(token)) {
            return true;
        }
        if (wsManager.isReconnectPaused(token)) {
            logger.warn("自动重连已暂停 token={} reason={}", token, wsManager.getReconnectPauseReason(token));
            return false;
        }
        String wsUrl = buildDefaultWsUrl(token);
        try {
            wsManager.connect(token, wsUrl);
        } catch (IllegalStateException ex) {
            logger.warn("自动重连被拒绝 token={} reason={}", token, ex.getMessage());
            return false;
        }
        boolean connected = waitForConnection(token, CONNECT_TIMEOUT_MS);
        if (!connected) {
            logger.warn("WebSocket 连接超时，疑似 token 失效/会话过期/参数错误 token={}", token);
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
        ensureConnectedForDaily(token, cmd);
        wsManager.sendCommand(token, cmd, body);
        executed.add(cmd);
        sleepDailyStep(delayMs);
    }

    private void sendDailyAndWait(
        String token,
        List<String> executed,
        String cmd,
        Map<String, Object> body,
        long timeoutMs,
        long delayMs,
        String... respCmds
    ) {
        ensureConnectedForDaily(token, cmd);

        Map<String, Long> beforeVersions = new LinkedHashMap<String, Long>();
        if (respCmds != null) {
            for (String respCmd : respCmds) {
                if (!isBlank(respCmd)) {
                    beforeVersions.put(respCmd, wsManager.getCommandVersion(token, respCmd));
                }
            }
        }

        wsManager.sendCommand(token, cmd, body);
        executed.add(cmd);

        if (!beforeVersions.isEmpty()) {
            long wait = timeoutMs <= 0 ? DAILY_WAIT_TIMEOUT_MS : timeoutMs;
            long start = System.currentTimeMillis();
            boolean arrived = false;
            String disconnectedMsg = null;
            while (System.currentTimeMillis() - start < wait) {
                if (!wsManager.isConnected(token)) {
                    if (wsManager.isReconnectPaused(token)) {
                        disconnectedMsg =
                            "WebSocket 已断开且自动重连暂停 cmd=" + cmd + " reason=" + wsManager.getReconnectPauseReason(token);
                    } else {
                        disconnectedMsg = "WebSocket 已断开 cmd=" + cmd;
                    }
                    break;
                }
                for (Map.Entry<String, Long> entry : beforeVersions.entrySet()) {
                    long current = wsManager.getCommandVersion(token, entry.getKey());
                    if (current > entry.getValue()) {
                        arrived = true;
                        break;
                    }
                }
                if (arrived) {
                    break;
                }
                sleepDaily(50);
            }
            if (!arrived) {
                if (disconnectedMsg != null) {
                    throw new IllegalStateException(disconnectedMsg);
                }
                throw new IllegalStateException(
                    "日常任务回包超时 cmd=" + cmd + " resp=" + joinRespNames(beforeVersions.keySet())
                );
            }
        }

        sleepDailyStep(delayMs);
    }

    private void sendDailyAndWaitOptional(
        String token,
        List<String> executed,
        String cmd,
        Map<String, Object> body,
        long timeoutMs,
        long delayMs,
        String... respCmds
    ) {
        try {
            sendDailyAndWait(token, executed, cmd, body, timeoutMs, delayMs, respCmds);
        } catch (IllegalStateException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.startsWith("日常任务回包超时")) {
                logger.info("每日任务可忽略步骤超时，继续执行 cmd={} token={} msg={}", cmd, token, message);
                return;
            }
            throw ex;
        }
    }

    private void ensureConnectedForDaily(String token, String cmd) {
        if (wsManager.isConnected(token)) {
            return;
        }
        if (wsManager.isReconnectPaused(token)) {
            throw new IllegalStateException(
                "WebSocket 已断开且自动重连暂停 cmd=" + cmd + " reason=" + wsManager.getReconnectPauseReason(token)
            );
        }
        throw new IllegalStateException("WebSocket 已断开 cmd=" + cmd);
    }

    private String joinRespNames(Iterable<String> names) {
        if (names == null) {
            return "<none>";
        }
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(name);
        }
        return builder.length() == 0 ? "<none>" : builder.toString();
    }

    private void sleepDaily(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("任务执行被中断");
        }
    }

    private void sleepDailyStep(long delayMs) {
        long actual = delayMs;
        if (actual < DAILY_STEP_MIN_DELAY_MS) {
            actual = DAILY_STEP_MIN_DELAY_MS;
        }
        sleepDaily(actual);
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

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String extractTaskTokenName(String taskName) {
        String text = normalizeText(taskName);
        if (text == null) {
            return "当前账号";
        }
        int slash = text.indexOf('/');
        if (slash < 0 || slash >= text.length() - 1) {
            return "当前账号";
        }
        String tokenName = text.substring(slash + 1).trim();
        return tokenName.isEmpty() ? "当前账号" : tokenName;
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
