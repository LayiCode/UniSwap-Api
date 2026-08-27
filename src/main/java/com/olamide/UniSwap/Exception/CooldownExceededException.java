package com.olamide.UniSwap.Exception;

import lombok.Getter;

// Thrown when a verification/reset code is requested again before the
// per-email cooldown has elapsed. Carries how many more seconds the caller
// must wait so the client can sync its resend-button countdown to the server.
@Getter
public class CooldownExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public CooldownExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
