package com.xyzw.webhelper.auth;

import com.xyzw.webhelper.auth.dto.CaptchaResponse;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {
    private static final long TTL_SECONDS = 300;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<String, CaptchaEntry>();

    public CaptchaResponse generate() {
        int a = random.nextInt(9) + 1;
        int b = random.nextInt(9) + 1;
        String id = UUID.randomUUID().toString();
        store.put(id, new CaptchaEntry(a + b, Instant.now().getEpochSecond()));
        return new CaptchaResponse(id, String.format("%d + %d = ?", a, b));
    }

    public boolean verify(String id, Integer answer) {
        if (id == null || id.trim().isEmpty() || answer == null) {
            return false;
        }
        CaptchaEntry entry = store.remove(id);
        if (entry == null) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (now - entry.createdAt > TTL_SECONDS) {
            return false;
        }
        return entry.answer == answer.intValue();
    }

    private static final class CaptchaEntry {
        private final int answer;
        private final long createdAt;

        private CaptchaEntry(int answer, long createdAt) {
            this.answer = answer;
            this.createdAt = createdAt;
        }
    }
}
