package com.xyzw.webhelper.xyzw.batch;

import com.xyzw.webhelper.xyzw.XyzwProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TokenRefreshXxlJob {
    private static final Logger logger = LoggerFactory.getLogger(TokenRefreshXxlJob.class);
    private final XyzwProfileService profileService;

    public TokenRefreshXxlJob(XyzwProfileService profileService) {
        this.profileService = profileService;
    }

    public String run(String triggerType, String triggerSource) {
        logger.info("trigger tokenRefreshRunner triggerType={} triggerSource={}", triggerType, triggerSource);
        int refreshed = profileService.refreshAllTokens();
        logger.info("tokenRefreshRunner done refreshed={}", refreshed);
        return "refreshed=" + refreshed;
    }
}
