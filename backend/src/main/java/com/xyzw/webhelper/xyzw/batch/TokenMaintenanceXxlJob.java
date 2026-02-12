package com.xyzw.webhelper.xyzw.batch;

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

    public String run(String triggerType, String triggerSource) {
        logger.info("trigger tokenMaintenanceRunner triggerType={} triggerSource={}", triggerType, triggerSource);
        maintenanceService.runAllTokens();
        return "token maintenance finished";
    }
}
