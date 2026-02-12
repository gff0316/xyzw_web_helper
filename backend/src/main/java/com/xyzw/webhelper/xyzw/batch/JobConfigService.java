package com.xyzw.webhelper.xyzw.batch;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JobConfigService {
    private final JobScheduleConfigMapper configMapper;

    public JobConfigService(JobScheduleConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public synchronized void ensureDefaults(Map<String, JobScheduleConfig> defaults) {
        List<JobScheduleConfig> current = configMapper.findAll();
        Map<String, JobScheduleConfig> currentMap = new LinkedHashMap<String, JobScheduleConfig>();
        for (JobScheduleConfig item : current) {
            if (item != null && item.getJobKey() != null) {
                currentMap.put(item.getJobKey(), item);
            }
        }
        for (Map.Entry<String, JobScheduleConfig> entry : defaults.entrySet()) {
            String jobKey = entry.getKey();
            if (jobKey == null || currentMap.containsKey(jobKey)) {
                continue;
            }
            JobScheduleConfig cfg = entry.getValue();
            LocalDateTime now = LocalDateTime.now();
            cfg.setJobKey(jobKey);
            cfg.setCreatedAt(now);
            cfg.setUpdatedAt(now);
            cfg.setUpdatedBy("system");
            configMapper.insert(cfg);
        }
    }

    public List<JobScheduleConfig> listAll() {
        return configMapper.findAll();
    }

    public JobScheduleConfig get(String jobKey) {
        return configMapper.findByJobKey(jobKey);
    }

    public JobScheduleConfig upsert(String jobKey, String jobName, String cronExpr, Boolean enabled, String updatedBy) {
        if (jobKey == null || jobKey.trim().isEmpty()) {
            throw new IllegalArgumentException("jobKey required");
        }
        String safeCron = cronExpr == null ? "" : cronExpr.trim();
        if (safeCron.isEmpty()) {
            throw new IllegalArgumentException("cronExpr required");
        }
        try {
            CronExpression.parse(safeCron);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid cron expression");
        }
        LocalDateTime now = LocalDateTime.now();
        JobScheduleConfig existing = configMapper.findByJobKey(jobKey);
        if (existing == null) {
            JobScheduleConfig cfg = new JobScheduleConfig();
            cfg.setJobKey(jobKey);
            cfg.setJobName(jobName == null ? jobKey : jobName);
            cfg.setCronExpr(safeCron);
            cfg.setEnabled(enabled == null ? Boolean.TRUE : enabled);
            cfg.setUpdatedBy(updatedBy == null ? "system" : updatedBy);
            cfg.setCreatedAt(now);
            cfg.setUpdatedAt(now);
            configMapper.insert(cfg);
            return cfg;
        }
        configMapper.updateByJobKey(
            jobKey,
            jobName == null ? existing.getJobName() : jobName,
            safeCron,
            enabled == null ? existing.getEnabled() : enabled,
            updatedBy == null ? "system" : updatedBy,
            now
        );
        return configMapper.findByJobKey(jobKey);
    }

    public List<JobScheduleConfig> listEnabled() {
        List<JobScheduleConfig> all = configMapper.findAll();
        List<JobScheduleConfig> out = new ArrayList<JobScheduleConfig>();
        for (JobScheduleConfig item : all) {
            if (item != null && Boolean.TRUE.equals(item.getEnabled())) {
                out.add(item);
            }
        }
        return out;
    }
}
