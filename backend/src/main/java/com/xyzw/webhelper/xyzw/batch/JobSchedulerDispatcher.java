package com.xyzw.webhelper.xyzw.batch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobSchedulerDispatcher {
    private final JobDispatchService dispatchService;

    public JobSchedulerDispatcher(JobDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${jobs.dispatcher.tick-ms:10000}")
    public void tick() {
        dispatchService.tick();
    }
}
