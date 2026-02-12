package com.xyzw.webhelper.xyzw.batch;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobAuditService {
    private final JobExecutionLogMapper logMapper;

    public JobAuditService(JobExecutionLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public Long start(String jobKey, String jobName, String triggerType, String triggerSource, String details) {
        JobExecutionLog log = new JobExecutionLog();
        LocalDateTime now = LocalDateTime.now();
        log.setJobKey(jobKey);
        log.setJobName(jobName);
        log.setTriggerType(triggerType);
        log.setTriggerSource(triggerSource);
        log.setStatus("RUNNING");
        log.setMessage("started");
        log.setDetails(details);
        log.setStartTime(now);
        log.setCreatedAt(now);
        logMapper.insert(log);
        return log.getId();
    }

    public void finish(Long id, String status, String message, String details, long durationMs) {
        if (id == null) {
            return;
        }
        logMapper.finish(id, status, message, details, durationMs, LocalDateTime.now());
    }

    public List<JobExecutionLog> listRecent(String jobKey, int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        return logMapper.listRecent(jobKey, safeLimit);
    }
}
