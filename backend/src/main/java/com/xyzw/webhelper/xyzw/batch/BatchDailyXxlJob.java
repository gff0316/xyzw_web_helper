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
        schedulerService.runDueTasks(triggerType + ":" + triggerSource);
        return "batch due tasks scan finished";
    }
}
