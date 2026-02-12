package com.xyzw.webhelper.xyzw.batch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface JobExecutionLogMapper {
    int insert(JobExecutionLog log);

    int finish(
        @Param("id") Long id,
        @Param("status") String status,
        @Param("message") String message,
        @Param("details") String details,
        @Param("durationMs") Long durationMs,
        @Param("endTime") LocalDateTime endTime
    );

    List<JobExecutionLog> listRecent(@Param("jobKey") String jobKey, @Param("limit") int limit);
}
