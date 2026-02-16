package com.xyzw.webhelper.xyzw.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class BatchDailyTaskStore {
    private static final Logger logger = LoggerFactory.getLogger(BatchDailyTaskStore.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path storagePath = Paths.get("backend", "storage", "batch_daily_tasks.json");

    public synchronized List<BatchDailyTask> load() {
        if (!Files.exists(storagePath)) {
            return new ArrayList<BatchDailyTask>();
        }
        try {
            byte[] bytes = Files.readAllBytes(storagePath);
            if (bytes.length == 0) {
                return new ArrayList<BatchDailyTask>();
            }
            return mapper.readValue(bytes, new TypeReference<List<BatchDailyTask>>() {});
        } catch (IOException ex) {
            logger.warn("读取批量日常任务失败，返回空列表", ex);
            return new ArrayList<BatchDailyTask>();
        }
    }

    public synchronized void save(Collection<BatchDailyTask> tasks) {
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), tasks);
        } catch (IOException ex) {
            logger.warn("写入批量日常任务失败", ex);
        }
    }
}