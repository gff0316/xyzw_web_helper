package com.xyzw.webhelper.xyzw;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyzw.webhelper.xyzw.ws.XyzwTokenPayloadDecoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class XyzwTaskLogService {
    public static final int TYPE_SCHEDULED_TASK = 0;
    public static final int TYPE_MANUAL_TASK = 1;
    public static final int TYPE_COMMAND = 2;

    private static final int MAX_LOG_SIZE = 3000;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
        new TypeReference<LinkedHashMap<String, Object>>() {
        };
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis());
    private final Deque<XyzwTaskLogEntry> entries = new ConcurrentLinkedDeque<XyzwTaskLogEntry>();

    public void recordTaskResult(
        String token,
        int type,
        String taskType,
        String taskTokenName,
        boolean success,
        String message
    ) {
        TokenIdentity identity = resolveIdentity(token);
        String normalizedTaskType = normalizeText(taskType);
        if (normalizedTaskType == null) {
            normalizedTaskType = "批量任务";
        }

        XyzwTaskLogEntry entry = new XyzwTaskLogEntry();
        entry.setId(sequence.incrementAndGet());
        entry.setType(normalizeTaskType(type));
        entry.setLevel(success ? "SUCCESS" : "ERROR");
        entry.setPhase("TASK");
        entry.setTokenKey(identity.tokenKey);
        entry.setRoleId(identity.roleId);
        entry.setTaskType(normalizedTaskType);
        entry.setTaskTokenName(normalizeText(taskTokenName));
        entry.setMessage(normalizeText(message) == null ? (success ? "执行成功" : "执行失败") : normalizeText(message));
        entry.setCreatedAt(LocalDateTime.now());
        append(entry);
    }

    public void recordCommandSend(
        String token,
        String taskLabel,
        String cmd,
        String cmdName,
        String bodySummary
    ) {
        TaskLabelParts task = splitTaskLabel(taskLabel);
        if (task == null) {
            return;
        }
        TokenIdentity identity = resolveIdentity(token);
        XyzwTaskLogEntry entry = new XyzwTaskLogEntry();
        entry.setId(sequence.incrementAndGet());
        entry.setType(TYPE_COMMAND);
        entry.setLevel("INFO");
        entry.setPhase("SEND");
        entry.setTokenKey(identity.tokenKey);
        entry.setRoleId(identity.roleId);
        entry.setTaskType(task.taskType);
        entry.setTaskTokenName(task.tokenName);
        entry.setCmd(normalizeText(cmd));
        entry.setCmdName(normalizeText(cmdName));
        entry.setMessage(buildCommandSendMessage(bodySummary));
        entry.setCreatedAt(LocalDateTime.now());
        append(entry);
    }

    public void recordCommandResponse(
        String token,
        String taskLabel,
        String cmd,
        String cmdName,
        boolean success,
        String message
    ) {
        TaskLabelParts task = splitTaskLabel(taskLabel);
        if (task == null) {
            return;
        }
        TokenIdentity identity = resolveIdentity(token);
        XyzwTaskLogEntry entry = new XyzwTaskLogEntry();
        entry.setId(sequence.incrementAndGet());
        entry.setType(TYPE_COMMAND);
        entry.setLevel(success ? "SUCCESS" : "WARNING");
        entry.setPhase("RESP");
        entry.setTokenKey(identity.tokenKey);
        entry.setRoleId(identity.roleId);
        entry.setTaskType(task.taskType);
        entry.setTaskTokenName(task.tokenName);
        entry.setCmd(normalizeText(cmd));
        entry.setCmdName(normalizeText(cmdName));
        entry.setMessage(normalizeText(message) == null ? "收到回包" : normalizeText(message));
        entry.setCreatedAt(LocalDateTime.now());
        append(entry);
    }

    public List<XyzwTaskLogEntry> listRecent(Set<String> tokenKeys, Integer type, int limit) {
        int max = limit <= 0 ? 100 : Math.min(limit, 500);
        boolean hasTokenFilter = tokenKeys != null;
        List<XyzwTaskLogEntry> result = new ArrayList<XyzwTaskLogEntry>();
        for (XyzwTaskLogEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (type != null && !type.equals(entry.getType())) {
                continue;
            }
            if (hasTokenFilter) {
                String tokenKey = entry.getTokenKey();
                if (tokenKey == null || !tokenKeys.contains(tokenKey)) {
                    continue;
                }
            }
            if (!hasTokenFilter && entry.getTokenKey() == null) {
                continue;
            }
            result.add(entry);
            if (result.size() >= max) {
                break;
            }
        }
        return result;
    }

    public String buildTokenKey(String rawToken) {
        return resolveIdentity(rawToken).tokenKey;
    }

    public Long extractRoleId(String rawToken) {
        return resolveIdentity(rawToken).roleId;
    }

    private void append(XyzwTaskLogEntry entry) {
        entries.addFirst(entry);
        while (entries.size() > MAX_LOG_SIZE) {
            entries.pollLast();
        }
    }

    private TokenIdentity resolveIdentity(String rawToken) {
        Map<String, Object> payload = parseTokenPayload(rawToken);
        String roleToken = normalizeText(asString(payload.get("roleToken")));
        Long roleId = asLong(payload.get("roleId"));

        String tokenKey;
        if (roleToken != null || roleId != null) {
            String roleIdText = roleId == null ? "0" : String.valueOf(roleId);
            tokenKey = roleIdText + "|" + shortHash(roleToken == null ? "" : roleToken);
        } else {
            tokenKey = "raw|" + shortHash(rawToken == null ? "" : rawToken.trim());
        }
        return new TokenIdentity(tokenKey, roleId);
    }

    private Map<String, Object> parseTokenPayload(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        String token = rawToken.trim();
        Map<String, Object> payload = parseJsonToken(token);
        if (!containsRoleToken(payload)) {
            payload = XyzwTokenPayloadDecoder.decodeFromBase64Token(token);
        }
        if (!containsRoleToken(payload)) {
            return Collections.emptyMap();
        }
        return payload;
    }

    private Map<String, Object> parseJsonToken(String token) {
        if (token == null || token.isEmpty() || token.charAt(0) != '{') {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = JSON.readValue(token, MAP_TYPE);
            if (parsed == null) {
                return Collections.emptyMap();
            }
            return parsed;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private TaskLabelParts splitTaskLabel(String taskLabel) {
        String label = normalizeText(taskLabel);
        if (label == null) {
            return null;
        }
        int slash = label.indexOf('/');
        if (slash < 0) {
            return new TaskLabelParts(label, null);
        }
        String taskType = normalizeText(label.substring(0, slash));
        String tokenName = normalizeText(label.substring(slash + 1));
        return new TaskLabelParts(taskType == null ? label : taskType, tokenName);
    }

    private String buildCommandSendMessage(String bodySummary) {
        String text = normalizeText(bodySummary);
        if (text == null || "{}".equals(text)) {
            return "发送指令";
        }
        return "发送指令 body=" + text;
    }

    private boolean containsRoleToken(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        String roleToken = asString(payload.get("roleToken"));
        return roleToken != null && !roleToken.trim().isEmpty();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private int normalizeTaskType(int type) {
        if (type == TYPE_SCHEDULED_TASK || type == TYPE_MANUAL_TASK) {
            return type;
        }
        return TYPE_MANUAL_TASK;
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            String hex = builder.toString();
            return hex.length() <= 12 ? hex : hex.substring(0, 12);
        } catch (Exception ex) {
            return String.valueOf(value.hashCode());
        }
    }

    private static final class TaskLabelParts {
        private final String taskType;
        private final String tokenName;

        private TaskLabelParts(String taskType, String tokenName) {
            this.taskType = taskType;
            this.tokenName = tokenName;
        }
    }

    private static final class TokenIdentity {
        private final String tokenKey;
        private final Long roleId;

        private TokenIdentity(String tokenKey, Long roleId) {
            this.tokenKey = tokenKey;
            this.roleId = roleId;
        }
    }
}
