package com.olamide.UniSwap.Service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Sliding-window rate limiter for the public login endpoint. Brute-forcing a
// password is cheap for the attacker but expensive for us (every attempt runs
// a full BCrypt comparison), so throttle per client IP. In-memory is fine at
// campus scale; swap for a distributed store (Redis) if the app ever scales
// horizontally.
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 15 * 60;

    private final Map<String, List<Long>> attempts = new ConcurrentHashMap<>();

    // Records an attempt and returns true if it's still within budget for the
    // window, false if the caller has hit the cap and should be rejected.
    public boolean isAllowed(String key) {
        long now = Instant.now().getEpochSecond();
        long cutoff = now - WINDOW_SECONDS;

        List<Long> timestamps = attempts.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        timestamps.removeIf(t -> t < cutoff);

        if (timestamps.size() >= MAX_ATTEMPTS) {
            return false;
        }
        timestamps.add(now);
        return true;
    }
}
