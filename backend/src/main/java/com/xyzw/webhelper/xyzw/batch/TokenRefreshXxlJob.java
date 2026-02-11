package com.xyzw.webhelper.xyzw.batch;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
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

    @XxlJob("tokenRefreshRunner")
    public ReturnT<String> run(String param) {
        logger.info("xxl-job \u89e6\u53d1 tokenRefreshRunner param={}", param);
        int refreshed = profileService.refreshAllTokens();
        logger.info("xxl-job tokenRefreshRunner \u5b8c\u6210 refreshed={}", refreshed);
        return ReturnT.SUCCESS;
    }
}