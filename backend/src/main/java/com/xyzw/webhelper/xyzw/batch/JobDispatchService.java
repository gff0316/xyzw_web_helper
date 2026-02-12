package com.xyzw.webhelper.xyzw.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobDispatchService {
    private static final Logger logger = LoggerFactory.getLogger(JobDispatchService.class);

    private final BatchDailyXxlJob batchDailyJob;
    private final TokenRefreshXxlJob tokenRefreshJob;
    private final TokenMaintenanceXxlJob tokenMaintenanceJob;
    private final JobConfigService configService;
    private final JobAuditService auditService;
    private final Map<String, LocalDateTime> lastFireTimes = new ConcurrentHashMap<String, LocalDateTime>();
    private final Set<String> runningJobs = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<String, String> jobNameMap = new LinkedHashMap<String, String>();

    @Value("${jobs.batch-daily.cron:0 10 12 * * *}")
    private String batchDailyCron;
    @Value("${jobs.batch-daily.enabled:true}")
    private Boolean batchDailyEnabled;
    @Value("${jobs.token-refresh.cron:0 30 3 */5 * *}")
    private String tokenRefreshCron;
    @Value("${jobs.token-refresh.enabled:true}")
    private Boolean tokenRefreshEnabled;
    @Value("${jobs.token-maintenance.cron:0 0 */5 * * *}")
    private String tokenMaintenanceCron;
    @Value("${jobs.token-maintenance.enabled:true}")
    private Boolean tokenMaintenanceEnabled;

    public JobDispatchService(
        BatchDailyXxlJob batchDailyJob,
        TokenRefreshXxlJob tokenRefreshJob,
        TokenMaintenanceXxlJob tokenMaintenanceJob,
        JobConfigService configService,
        JobAuditService auditService
    ) {
        this.batchDailyJob = batchDailyJob;
        this.tokenRefreshJob = tokenRefreshJob;
        this.tokenMaintenanceJob = tokenMaintenanceJob;
        this.configService = configService;
        this.auditService = auditService;
    }

    @PostConstruct
    public void init() {
        jobNameMap.put("batchDailyRunner", "\u6279\u91cf\u65e5\u5e38\u626b\u63cf");
        jobNameMap.put("tokenRefreshRunner", "Token\u5237\u65b0");
        jobNameMap.put("tokenMaintenanceRunner", "\u7f50\u5b50\u548c\u6302\u673a\u5956\u52b1");
        configService.ensureDefaults(defaultConfigs());

        syncSystemManagedConfig(
            "batchDailyRunner",
            resolveCron(batchDailyCron, "0 10 12 * * *", "batchDailyRunner"),
            batchDailyEnabled == null ? Boolean.TRUE : batchDailyEnabled
        );
        syncSystemManagedConfig(
            "tokenRefreshRunner",
            resolveCron(tokenRefreshCron, "0 30 3 */5 * *", "tokenRefreshRunner"),
            tokenRefreshEnabled == null ? Boolean.TRUE : tokenRefreshEnabled
        );
        syncSystemManagedConfig(
            "tokenMaintenanceRunner",
            resolveCron(tokenMaintenanceCron, "0 0 */5 * * *", "tokenMaintenanceRunner"),
            tokenMaintenanceEnabled == null ? Boolean.TRUE : tokenMaintenanceEnabled
        );
    }

    public List<JobScheduleConfig> listConfigs() {
        List<JobScheduleConfig> configs = configService.listAll();
        for (JobScheduleConfig cfg : configs) {
            if (cfg == null || cfg.getJobKey() == null) {
                continue;
            }
            String preferredName = jobNameMap.get(cfg.getJobKey());
            if (preferredName != null && !preferredName.equals(cfg.getJobName())) {
                cfg.setJobName(preferredName);
            }
        }
        return configs;
    }

    public JobScheduleConfig updateConfig(String jobKey, String cronExpr, Boolean enabled, String updatedBy) {
        String jobName = jobNameMap.get(jobKey);
        if (jobName == null) {
            throw new IllegalArgumentException("unsupported jobKey");
        }
        return configService.upsert(jobKey, jobName, cronExpr, enabled, updatedBy);
    }

    public String runOnce(String jobKey, String triggerType, String triggerSource) {
        String jobName = jobNameMap.get(jobKey);
        if (jobName == null) {
            throw new IllegalArgumentException("unsupported jobKey");
        }
        if (!runningJobs.add(jobKey)) {
            Long logId = auditService.start(jobKey, jobName, triggerType, triggerSource, "skipped: already running");
            auditService.finish(logId, "SKIPPED", "job is already running", null, 0L);
            return "skipped: already running";
        }
        long start = System.currentTimeMillis();
        Long logId = auditService.start(jobKey, jobName, triggerType, triggerSource, null);
        try {
            String message = runJob(jobKey, triggerType, triggerSource);
            long duration = System.currentTimeMillis() - start;
            auditService.finish(logId, "SUCCESS", message, null, duration);
            return message;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            auditService.finish(logId, "FAILED", ex.getMessage(), stackSummary(ex), duration);
            throw ex;
        } finally {
            runningJobs.remove(jobKey);
        }
    }

    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (JobScheduleConfig config : configService.listEnabled()) {
            if (config == null || config.getJobKey() == null || config.getCronExpr() == null) {
                continue;
            }
            String jobKey = config.getJobKey();
            String cron = config.getCronExpr().trim();
            CronExpression expression;
            try {
                expression = CronExpression.parse(cron);
            } catch (Exception ex) {
                logger.warn("skip invalid cron jobKey={} cron={}", jobKey, cron);
                continue;
            }
            LocalDateTime anchor = lastFireTimes.get(jobKey);
            if (anchor == null) {
                anchor = now.minusSeconds(1);
            }
            LocalDateTime next = expression.next(anchor);
            if (next == null || next.isAfter(now)) {
                continue;
            }
            try {
                runOnce(jobKey, "SCHEDULED", "dispatcher");
            } catch (Exception ex) {
                logger.warn("job run failed jobKey={} msg={}", jobKey, ex.getMessage());
            } finally {
                lastFireTimes.put(jobKey, next);
            }
        }
    }

    public List<JobExecutionLog> listRecentLogs(String jobKey, int limit) {
        return auditService.listRecent(jobKey, limit);
    }

    private String runJob(String jobKey, String triggerType, String triggerSource) {
        if ("batchDailyRunner".equals(jobKey)) {
            return batchDailyJob.run(triggerType, triggerSource);
        }
        if ("tokenRefreshRunner".equals(jobKey)) {
            return tokenRefreshJob.run(triggerType, triggerSource);
        }
        if ("tokenMaintenanceRunner".equals(jobKey)) {
            return tokenMaintenanceJob.run(triggerType, triggerSource);
        }
        throw new IllegalArgumentException("unsupported jobKey");
    }

    private Map<String, JobScheduleConfig> defaultConfigs() {
        Map<String, JobScheduleConfig> map = new LinkedHashMap<String, JobScheduleConfig>();

        JobScheduleConfig batch = new JobScheduleConfig();
        batch.setJobName(jobNameMap.get("batchDailyRunner"));
        batch.setCronExpr(resolveCron(batchDailyCron, "0 10 12 * * *", "batchDailyRunner"));
        batch.setEnabled(batchDailyEnabled == null ? Boolean.TRUE : batchDailyEnabled);
        map.put("batchDailyRunner", batch);

        JobScheduleConfig refresh = new JobScheduleConfig();
        refresh.setJobName(jobNameMap.get("tokenRefreshRunner"));
        refresh.setCronExpr(resolveCron(tokenRefreshCron, "0 30 3 */5 * *", "tokenRefreshRunner"));
        refresh.setEnabled(tokenRefreshEnabled == null ? Boolean.TRUE : tokenRefreshEnabled);
        map.put("tokenRefreshRunner", refresh);

        JobScheduleConfig maintenance = new JobScheduleConfig();
        maintenance.setJobName(jobNameMap.get("tokenMaintenanceRunner"));
        maintenance.setCronExpr(resolveCron(tokenMaintenanceCron, "0 0 */5 * * *", "tokenMaintenanceRunner"));
        maintenance.setEnabled(tokenMaintenanceEnabled == null ? Boolean.TRUE : tokenMaintenanceEnabled);
        map.put("tokenMaintenanceRunner", maintenance);

        return Collections.unmodifiableMap(map);
    }

    private String resolveCron(String configured, String fallback, String jobKey) {
        String cron = configured == null ? "" : configured.trim();
        if (cron.isEmpty()) {
            return fallback;
        }
        try {
            CronExpression.parse(cron);
            return cron;
        } catch (Exception ex) {
            logger.warn("invalid default cron from config jobKey={} cron={} fallback={}", jobKey, cron, fallback);
            return fallback;
        }
    }

    private void syncSystemManagedConfig(String jobKey, String targetCron, Boolean targetEnabled) {
        JobScheduleConfig current = configService.get(jobKey);
        if (current == null) {
            return;
        }
        String updatedBy = current.getUpdatedBy();
        boolean systemManaged = updatedBy == null || "system".equalsIgnoreCase(updatedBy.trim());
        if (!systemManaged) {
            return;
        }
        String targetName = jobNameMap.get(jobKey);
        String currentCron = current.getCronExpr() == null ? "" : current.getCronExpr().trim();
        String nextCron = targetCron == null ? "" : targetCron.trim();
        Boolean nextEnabled = targetEnabled == null ? current.getEnabled() : targetEnabled;
        boolean changed =
            (targetName != null && !targetName.equals(current.getJobName())) ||
            !nextCron.equals(currentCron) ||
            (nextEnabled != null && !nextEnabled.equals(current.getEnabled()));
        if (!changed) {
            return;
        }
        configService.upsert(jobKey, targetName, nextCron, nextEnabled, "system");
    }

    private String stackSummary(Exception ex) {
        if (ex == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage());
        StackTraceElement[] trace = ex.getStackTrace();
        int count = trace == null ? 0 : Math.min(trace.length, 3);
        for (int i = 0; i < count; i++) {
            builder.append("\n at ").append(trace[i].toString());
        }
        return builder.toString();
    }
}
