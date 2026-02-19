package com.xyzw.webhelper.xyzw.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyzw.webhelper.auth.AuthUser;
import com.xyzw.webhelper.auth.AuthUserMapper;
import com.xyzw.webhelper.xyzw.XyzwWsController;
import com.xyzw.webhelper.xyzw.XyzwUserToken;
import com.xyzw.webhelper.xyzw.XyzwUserTokenMapper;
import com.xyzw.webhelper.xyzw.XyzwTaskLogService;
import com.xyzw.webhelper.xyzw.dto.XyzwDailyTaskRequest;
import com.xyzw.webhelper.xyzw.ws.XyzwTokenPayloadDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BatchDailySchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(BatchDailySchedulerService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final BatchDailyTaskStore store;
    private final XyzwWsController wsController;
    private final JobAuditService auditService;
    private final AuthUserMapper authUserMapper;
    private final XyzwUserTokenMapper userTokenMapper;
    private final XyzwTaskLogService taskLogService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Long, BatchDailyTask> tasks = new ConcurrentHashMap<Long, BatchDailyTask>();
    private final Set<Long> runningTasks = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BatchDailySchedulerService(
        BatchDailyTaskStore store,
        XyzwWsController wsController,
        JobAuditService auditService,
        AuthUserMapper authUserMapper,
        XyzwUserTokenMapper userTokenMapper,
        XyzwTaskLogService taskLogService
    ) {
        this.store = store;
        this.wsController = wsController;
        this.auditService = auditService;
        this.authUserMapper = authUserMapper;
        this.userTokenMapper = userTokenMapper;
        this.taskLogService = taskLogService;
    }

    @PostConstruct
    public void init() {
        List<BatchDailyTask> saved = store.load();
        for (BatchDailyTask task : saved) {
            if (task.getId() != null) {
                tasks.put(task.getId(), task);
            }
        }
        logger.info("批量日常任务加载完成 count={}", tasks.size());
    }

    public List<BatchDailyTaskResponse> listTasks(Long userId) {
        List<BatchDailyTaskResponse> list = new ArrayList<BatchDailyTaskResponse>();
        for (BatchDailyTask task : tasks.values()) {
            if (userId.equals(task.getUserId())) {
                list.add(toResponse(task));
            }
        }
        list.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        return list;
    }

    public BatchDailyTaskResponse createTask(Long userId, BatchDailyTaskRequest request) {
        BatchDailyTask task = new BatchDailyTask();
        task.setUserId(userId);
        applyTaskRequest(task, request);
        task.setId(System.currentTimeMillis());
        task.setCreatedAt(nowString());
        task.setUpdatedAt(nowString());
        tasks.put(task.getId(), task);
        store.save(tasks.values());
        return toResponse(task);
    }

    public BatchDailyTaskResponse updateTask(Long userId, Long id, BatchDailyTaskRequest request) {
        BatchDailyTask task = tasks.get(id);
        if (task == null || !userId.equals(task.getUserId())) {
            return null;
        }
        applyTaskRequest(task, request);
        task.setUpdatedAt(nowString());
        tasks.put(task.getId(), task);
        store.save(tasks.values());
        return toResponse(task);
    }

    public boolean deleteTask(Long userId, Long id) {
        BatchDailyTask task = tasks.get(id);
        if (task == null || !userId.equals(task.getUserId())) {
            return false;
        }
        tasks.remove(id);
        store.save(tasks.values());
        return true;
    }

    public boolean runTaskNow(Long userId, Long id) {
        BatchDailyTask task = tasks.get(id);
        if (task == null || !userId.equals(task.getUserId())) {
            return false;
        }
        submitTask(task, "手动触发");
        return true;
    }

    public int runDueTasks() {
        return runDueTasks("定时触发");
    }

    public int runDueTasks(String trigger) {
        String nowTime = LocalTime.now().format(TIME_FMT);
        String today = LocalDate.now().format(DATE_FMT);
        int submitted = 0;
        for (BatchDailyTask task : tasks.values()) {
            if (!task.isEnabled()) {
                continue;
            }
            if (task.getTime() == null || !task.getTime().equals(nowTime)) {
                continue;
            }
            if (today.equals(task.getLastRunDate())) {
                continue;
            }
            submitTask(task, trigger);
            submitted++;
        }
        return submitted;
    }

    public int runAllEnabledTasks(String trigger) {
        int submitted = 0;
        for (BatchDailyTask task : tasks.values()) {
            if (!task.isEnabled()) {
                continue;
            }
            submitTask(task, trigger);
            submitted++;
        }
        return submitted;
    }

    public int runFallbackFromUserTokens(String username, String trigger) {
        if (isBlank(username)) {
            return 0;
        }
        AuthUser user = authUserMapper.findByUsername(username.trim());
        if (user == null || user.getId() == null) {
            logger.warn("批量日常任务回退失败：用户不存在 username={}", username);
            return 0;
        }
        List<XyzwUserToken> userTokens = userTokenMapper.findByUserId(user.getId());
        if (userTokens == null || userTokens.isEmpty()) {
            logger.warn("批量日常任务回退失败：用户无可用 token username={} userId={}", username, user.getId());
            return 0;
        }

        List<BatchDailyToken> tokens = new ArrayList<BatchDailyToken>();
        for (XyzwUserToken userToken : userTokens) {
            if (userToken == null || isBlank(userToken.getToken())) {
                continue;
            }
            Map<String, Object> tokenData = parseTokenData(userToken.getToken());
            if (!containsRoleToken(tokenData)) {
                logger.warn("批量日常任务回退跳过无效 token id={} name={}", userToken.getId(), userToken.getName());
                continue;
            }
            BatchDailyToken token = new BatchDailyToken();
            token.setName(isBlank(userToken.getName()) ? "token-" + userToken.getId() : userToken.getName().trim());
            token.setTokenData(tokenData);
            Long sessId = asLong(tokenData.get("sessId"));
            Long connId = asLong(tokenData.get("connId"));
            if (sessId != null && sessId > 0) {
                token.setSessId(sessId);
            }
            if (connId != null && connId > 0) {
                token.setConnId(connId);
            }
            tokens.add(token);
        }
        if (tokens.isEmpty()) {
            logger.warn("批量日常任务回退失败：user_tokens 均不可解析 username={} userId={}", username, user.getId());
            return 0;
        }

        BatchDailyTask task = new BatchDailyTask();
        task.setId(nextFallbackTaskId());
        task.setUserId(user.getId());
        task.setName("批量日常任务(自动回退)");
        task.setEnabled(true);
        task.setTaskDelayMs(500);
        task.setTokens(tokens);
        task.setSettings(null);

        submitTask(task, trigger + ":fallback");
        logger.info("批量日常任务回退提交成功 username={} userId={} tokenCount={}", username, user.getId(), tokens.size());
        return tokens.size();
    }

    private void submitTask(BatchDailyTask task, String trigger) {
        if (!runningTasks.add(task.getId())) {
            return;
        }
        executor.submit(() -> {
            try {
                executeTask(task, trigger);
            } finally {
                runningTasks.remove(task.getId());
            }
        });
    }

    private void executeTask(BatchDailyTask task, String trigger) {
        long startMs = System.currentTimeMillis();
        Long logId = null;
        if (auditService != null) {
            logId = auditService.start(
                "batchDailyTask",
                task.getName() == null ? "批量日常任务" : task.getName(),
                "TASK",
                trigger,
                "taskId=" + task.getId() + ", userId=" + task.getUserId()
            );
        }
        List<BatchDailyToken> tokens = task.getTokens() == null ? new ArrayList<BatchDailyToken>() : task.getTokens();
        int success = 0;
        int fail = 0;
        logger.info("批量日常任务开始 name={} trigger={} tokenCount={}", task.getName(), trigger, tokens.size());
        for (BatchDailyToken token : tokens) {
            String tokenName = token.getName() == null ? "未命名账号" : token.getName();
            String tokenJson = null;
            try {
                tokenJson = buildTokenJson(token);
                XyzwDailyTaskRequest req = new XyzwDailyTaskRequest();
                req.setToken(tokenJson);
                req.setTaskName((task.getName() == null ? "批量日常任务" : task.getName()) + "/" + tokenName);
                req.setSettings(task.getSettings());
                Map<String, Object> body = wsController.runDailyTasks(req).getBody();
                if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                    success++;
                    logger.info("批量日常任务完成 token={}", tokenName);
                    taskLogService.recordTaskResult(
                        tokenJson,
                        XyzwTaskLogService.TYPE_SCHEDULED_TASK,
                        task.getName(),
                        tokenName,
                        true,
                        "执行成功"
                    );
                } else {
                    fail++;
                    logger.warn("批量日常任务失败 token={}", tokenName);
                    taskLogService.recordTaskResult(
                        tokenJson,
                        XyzwTaskLogService.TYPE_SCHEDULED_TASK,
                        task.getName(),
                        tokenName,
                        false,
                        "执行失败"
                    );
                }
            } catch (Exception ex) {
                fail++;
                logger.warn("批量日常任务异常 token={} msg={}", tokenName, ex.getMessage());
                taskLogService.recordTaskResult(
                    resolveTokenForLog(tokenJson, token),
                    XyzwTaskLogService.TYPE_SCHEDULED_TASK,
                    task.getName(),
                    tokenName,
                    false,
                    ex.getMessage()
                );
            }
            sleep(task.getTaskDelayMs());
        }
        task.setLastRunDate(LocalDate.now().format(DATE_FMT));
        task.setLastRunAt(nowString());
        task.setLastStatus(String.format("成功 %d / 失败 %d", success, fail));
        task.setUpdatedAt(nowString());
        store.save(tasks.values());
        if (auditService != null && logId != null) {
            String status = fail == 0 ? "SUCCESS" : (success > 0 ? "PARTIAL" : "FAILED");
            String message = String.format("success=%d fail=%d", success, fail);
            String details = "taskId=" + task.getId() + ", trigger=" + trigger;
            auditService.finish(logId, status, message, details, System.currentTimeMillis() - startMs);
        }
        logger.info("批量日常任务结束 name={} success={} fail={}", task.getName(), success, fail);
    }

    private String buildTokenJson(BatchDailyToken token) {
        Map<String, Object> data = token.getTokenData();
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("tokenData 为空");
        }
        Map<String, Object> copy = new java.util.LinkedHashMap<String, Object>(data);
        long sessId = token.getSessId() == null ? generateSessId() : token.getSessId();
        long connId = token.getConnId() == null ? generateConnId() : token.getConnId();
        token.setSessId(sessId);
        token.setConnId(connId);
        copy.put("sessId", sessId);
        copy.put("connId", connId);
        copy.put("isRestore", 0);
        try {
            return mapper.writeValueAsString(copy);
        } catch (Exception ex) {
            throw new IllegalStateException("生成 token JSON 失败");
        }
    }

    private void applyTaskRequest(BatchDailyTask task, BatchDailyTaskRequest request) {
        if (request == null) {
            return;
        }
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            task.setName(request.getName().trim());
        } else if (task.getName() == null) {
            task.setName("批量日常任务");
        }
        if (request.getTime() != null) {
            task.setTime(normalizeTime(request.getTime()));
        }
        if (request.getEnabled() != null) {
            task.setEnabled(request.getEnabled());
        } else if (task.getId() == null) {
            task.setEnabled(true);
        }
        if (request.getTaskDelayMs() != null) {
            task.setTaskDelayMs(normalizeDelay(request.getTaskDelayMs()));
        } else if (task.getTaskDelayMs() == null) {
            task.setTaskDelayMs(500);
        }
        if (request.getTokens() != null) {
            List<BatchDailyToken> tokens = new ArrayList<BatchDailyToken>();
            for (BatchDailyToken token : request.getTokens()) {
                if (token != null && token.getTokenData() != null) {
                    if (token.getSessId() == null) {
                        token.setSessId(generateSessId());
                    }
                    if (token.getConnId() == null) {
                        token.setConnId(generateConnId());
                    }
                    tokens.add(token);
                }
            }
            task.setTokens(tokens);
        }
        task.setSettings(null);
    }

    private String resolveTokenForLog(String tokenJson, BatchDailyToken token) {
        if (!isBlank(tokenJson)) {
            return tokenJson;
        }
        if (token == null || token.getTokenData() == null || token.getTokenData().isEmpty()) {
            return "";
        }
        try {
            return mapper.writeValueAsString(token.getTokenData());
        } catch (Exception ex) {
            return "";
        }
    }

    private Long nextFallbackTaskId() {
        return -1L * System.currentTimeMillis() * 100 - ThreadLocalRandom.current().nextInt(100);
    }

    private Map<String, Object> parseTokenData(String rawToken) {
        if (isBlank(rawToken)) {
            return Collections.emptyMap();
        }
        String token = rawToken.trim();
        Map<String, Object> payload = parseJsonToken(token);
        if (!containsRoleToken(payload)) {
            payload = XyzwTokenPayloadDecoder.decodeFromBase64Token(token);
        }
        if (!containsRoleToken(payload)) {
            return Collections.emptyMap();
        }
        return new java.util.LinkedHashMap<String, Object>(payload);
    }

    private Map<String, Object> parseJsonToken(String token) {
        if (isBlank(token) || token.charAt(0) != '{') {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = mapper.readValue(token, MAP_TYPE);
            return parsed == null ? Collections.<String, Object>emptyMap() : parsed;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private boolean containsRoleToken(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        Object value = payload.get("roleToken");
        return value instanceof String && !((String) value).trim().isEmpty();
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private BatchDailyTaskResponse toResponse(BatchDailyTask task) {
        BatchDailyTaskResponse resp = new BatchDailyTaskResponse();
        resp.setId(task.getId());
        resp.setName(task.getName());
        resp.setTime(task.getTime());
        resp.setEnabled(task.isEnabled());
        resp.setTaskDelayMs(task.getTaskDelayMs());
        List<BatchDailyToken> tokens = task.getTokens();
        int count = tokens == null ? 0 : tokens.size();
        resp.setTokenCount(count);
        List<String> names = new ArrayList<String>();
        if (tokens != null) {
            for (BatchDailyToken token : tokens) {
                if (token.getName() != null) {
                    names.add(token.getName());
                }
            }
        }
        resp.setTokenNames(names);
        resp.setLastRunAt(task.getLastRunAt());
        resp.setLastStatus(task.getLastStatus());
        return resp;
    }

    private String normalizeTime(String time) {
        try {
            return LocalTime.parse(time, TIME_FMT).format(TIME_FMT);
        } catch (Exception ex) {
            return LocalTime.now().format(TIME_FMT);
        }
    }

    private int normalizeDelay(Integer delay) {
        if (delay == null || delay < 0) {
            return 500;
        }
        if (delay > 10000) {
            return 10000;
        }
        return delay;
    }

    private String nowString() {
        return LocalDateTime.now().format(DATETIME_FMT);
    }

    private long generateSessId() {
        long now = System.currentTimeMillis();
        return now * 100 + ThreadLocalRandom.current().nextInt(100);
    }

    private long generateConnId() {
        long now = System.currentTimeMillis();
        return now + ThreadLocalRandom.current().nextInt(10);
    }

    private void sleep(Integer delayMs) {
        int wait = normalizeDelay(delayMs);
        if (wait <= 0) {
            return;
        }
        try {
            Thread.sleep(wait);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
