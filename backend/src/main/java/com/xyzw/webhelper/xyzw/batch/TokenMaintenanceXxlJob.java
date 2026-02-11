package com.xyzw.webhelper.xyzw.batch;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TokenMaintenanceXxlJob {
    private static final Logger logger = LoggerFactory.getLogger(TokenMaintenanceXxlJob.class);
    private final XyzwTokenMaintenanceService maintenanceService;

    public TokenMaintenanceXxlJob(XyzwTokenMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @XxlJob("tokenMaintenanceRunner")
    public ReturnT<String> run(String param) {
        logger.info("xxl-job \u89e6\u53d1 tokenMaintenanceRunner param={}", param);
        maintenanceService.runAllTokens();
        return ReturnT.SUCCESS;
    }
}