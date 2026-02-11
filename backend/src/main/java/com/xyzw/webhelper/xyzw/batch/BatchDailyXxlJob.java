package com.xyzw.webhelper.xyzw.batch;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
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

    @XxlJob("batchDailyRunner")
    public ReturnT<String> run(String param) {
        logger.info("xxl-job \u89e6\u53d1 batchDailyRunner param={}", param);
        schedulerService.runDueTasks("xxl-job");
        return ReturnT.SUCCESS;
    }
}