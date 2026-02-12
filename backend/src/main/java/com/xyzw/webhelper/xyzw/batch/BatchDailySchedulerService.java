package com.xyzw.webhelper.xyzw.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyzw.webhelper.xyzw.XyzwWsController;
import com.xyzw.webhelper.xyzw.dto.XyzwDailyTaskRequest;
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

    private final BatchDailyTaskStore store;
    private final XyzwWsController wsController;
    private final JobAuditService auditService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Long, BatchDailyTask> tasks = new ConcurrentHashMap<Long, BatchDailyTask>();
    private final Set<Long> runningTasks = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BatchDailySchedulerService(BatchDailyTaskStore store, XyzwWsController wsController, JobAuditService auditService) {
        this.store = store;
        this.wsController = wsController;
        this.auditService = auditService;
    }

    @PostConstruct
    public void init() {
        List<BatchDailyTask> saved = store.load();
        for (BatchDailyTask task : saved) {
            if (task.getId() != null) {
                tasks.put(task.getId(), task);
            }
        }
        logger.info("\u6279\u91cf\u65e5\u5e38\u4efb\u52a1\u52a0\u8f7d\u5b8c\u6210 count={}", tasks.size());
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
        submitTask(task, "\u624b\u52a8\u89e6\u53d1");
        return true;
    }

    public void runDueTasks() {
        runDueTasks("\u5b9a\u65f6\u89e6\u53d1");
    }

    public void runDueTasks(String trigger) {
        String nowTime = LocalTime.now().format(TIME_FMT);
        String today = LocalDate.now().format(DATE_FMT);
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
        }
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
        logger.info("\u6279\u91cf\u65e5\u5e38\u4efb\u52a1\u5f00\u59cb name={} trigger={} tokenCount={}", task.getName(), trigger, tokens.size());
        for (BatchDailyToken token : tokens) {
            String tokenName = token.getName() == null ? "\u672a\u547d\u540d\u8d26\u53f7" : token.getName();
            try {
                String tokenJson = buildTokenJson(token);
                XyzwDailyTaskRequest req = new XyzwDailyTaskRequest();
                req.setToken(tokenJson);
                req.setSettings(task.getSettings());
                Map<String, Object> body = wsController.runDailyTasks(req).getBody();
                if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                    success++;
                    logger.info("\u6279\u91cf\u65e5\u5e38\u4efb\u52a1\u5b8c\u6210 token={}", tokenName);
                } else {
                    fail++;
                    logger.warn("\u6279\u91cf\u65e5\u5e38\u4efb\u52a1\u5931\u8d25 token={}", tokenName);
                }
            } catch (Exception ex) {
                fail++;
                logger.warn("\u6279\u91cf\u65e5\u5e38\u4efb\u52a1\u5f02\u5e38 token={} msg={}", tokenName, ex.getMessage());
            }
            sleep(task.getTaskDelayMs());
        }
        task.setLastRunDate(LocalDate.now().format(DATE_FMT));
        task.setLastRunAt(nowString());
        task.setLastStatus(String.format("\u6210\u529f %d / \u5931\u8d25 %d", success, fail));
        task.setUpdatedAt(nowString());
        store.save(tasks.values());
        if (auditService != null && logId != null) {
            String status = fail == 0 ? "SUCCESS" : (success > 0 ? "PARTIAL" : "FAILED");
            String message = String.format("success=%d fail=%d", success, fail);
            String details = "taskId=" + task.getId() + ", trigger=" + trigger;
            auditService.finish(logId, status, message, details, System.currentTimeMillis() - startMs);
        }
        logger.info("\u6279\u91cf\u65e5\u5e38\u4efb\u52a1\u7ed3\u675f name={} success={} fail={}", task.getName(), success, fail);
    }

    private String buildTokenJson(BatchDailyToken token) {
        Map<String, Object> data = token.getTokenData();
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("tokenData \u4e3a\u7a7a");
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
            throw new IllegalStateException("\u751f\u6210 token JSON \u5931\u8d25");
        }
    }

    private void applyTaskRequest(BatchDailyTask task, BatchDailyTaskRequest request) {
        if (request == null) {
            return;
        }
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            task.setName(request.getName().trim());
        } else if (task.getName() == null) {
            task.setName("\u6279\u91cf\u65e5\u5e38\u4efb\u52a1");
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
        if (request.getSettings() != null) {
            task.setSettings(request.getSettings());
        }
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
