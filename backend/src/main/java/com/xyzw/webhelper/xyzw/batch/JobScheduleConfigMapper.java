package com.xyzw.webhelper.xyzw.batch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface JobScheduleConfigMapper {
    List<JobScheduleConfig> findAll();

    JobScheduleConfig findByJobKey(@Param("jobKey") String jobKey);

    int insert(JobScheduleConfig config);

    int updateByJobKey(
        @Param("jobKey") String jobKey,
        @Param("jobName") String jobName,
        @Param("cronExpr") String cronExpr,
        @Param("enabled") Boolean enabled,
        @Param("updatedBy") String updatedBy,
        @Param("updatedAt") LocalDateTime updatedAt
    );
}
