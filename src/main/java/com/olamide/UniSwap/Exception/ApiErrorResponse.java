package com.olamide.UniSwap.Exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// Consistent JSON shape for every error the API returns, whether it's a
// single message (e.g. "Email already registered") or a list of field-level
// validation failures.
@Getter
@Builder
@AllArgsConstructor
public class ApiErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private List<String> details;
}