package com.olamide.UniSwap.Service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTest {

    @Test
    void cooldownIsZeroBeforeAnySend() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        assertEquals(0, limiter.cooldownRemainingSeconds("test@.."));
    }

    @Test
    void cooldownBlocksImmediateResend() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = "test@student.lautech.edu.ng";

        limiter.recordCodeSent(key);
        long remaining = limiter.cooldownRemainingSeconds(key);

        // Must still be inside the 30s window (0 < remaining <= 30).
        assertTrue(remaining > 0 && remaining <= limiter.getCooldownSeconds());
    }

    @Test
    void cooldownExpiresAfterWindow() throws InterruptedException {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = "test@student.lautech.edu.ng";

        limiter.recordCodeSent(key);
        Thread.sleep(1100);
        long remaining = limiter.cooldownRemainingSeconds(key);

        // One second passed, so at least 1s less than the full window remains.
        assertTrue(remaining <= limiter.getCooldownSeconds() - 1);
    }
}
