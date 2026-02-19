package com.xyzw.webhelper.xyzw.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BatchDailyXxlJob {
    private static final Logger logger = LoggerFactory.getLogger(BatchDailyXxlJob.class);
    private final BatchDailySchedulerService schedulerService;

    public BatchDailyXxlJob(BatchDailySchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public String run(String triggerType, String triggerSource) {
        logger.info("trigger batchDailyRunner triggerType={} triggerSource={}", triggerType, triggerSource);
        String trigger = triggerType + ":" + triggerSource;
        if ("MANUAL".equalsIgnoreCase(triggerType)) {
            int submitted = schedulerService.runAllEnabledTasks(trigger);
            if (submitted <= 0) {
                int fallbackTokenCount = schedulerService.runFallbackFromUserTokens(triggerSource, trigger);
                if (fallbackTokenCount <= 0) {
                    logger.warn("batchDailyRunner 手动触发未提交任何任务，可能未配置或全部禁用 triggerSource={}", triggerSource);
                    return "skipped: no enabled batch daily tasks";
                }
                return "batch fallback submitted from user_tokens: " + fallbackTokenCount;
            }
            return "batch all enabled tasks submitted: " + submitted;
        }
        int submitted = schedulerService.runDueTasks(trigger);
        if (submitted <= 0) {
            return "skipped: no due batch daily tasks";
        }
        return "batch due tasks submitted: " + submitted;
    }
}
