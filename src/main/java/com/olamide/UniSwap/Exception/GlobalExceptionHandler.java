package com.olamide.UniSwap.Exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;

// Catches exceptions thrown anywhere in the Controller/Service layers and
// turns them into a consistent ApiErrorResponse JSON shape, instead of Spring's
// default whitelabel error page or an unstructured stack trace. Client-side
// input problems map to their proper 4xx status — never a misleading 500.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Thrown by @Valid when a DTO fails its jakarta.validation annotations
    // (e.g. @NotBlank title, @Positive price). Collects every field error,
    // not just the first one, so the client can show them all at once.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Validation Failed", "One or more fields are invalid", details);
    }

    // Thrown deliberately throughout our Services (e.g. 404 "Product not
    // found", 409 "Email already registered", 403 "You do not own this
    // listing"). Respects whatever status/message the throwing code chose.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        // getReason() is nullable for `new ResponseStatusException(status)` —
        // fall back to the status phrase so "message" is never null.
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, status.getReasonPhrase(), message, null);
    }

    // A verification/reset code was requested again before the per-email
    // cooldown elapsed. Returns 429 with retryAfterSeconds so the client can
    // start its countdown from the server's remaining-wait truth.
    @ExceptionHandler(CooldownExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleCooldown(CooldownExceededException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                ex.getMessage(), null, ex.getRetryAfterSeconds());
    }

    // Spring Security throws this when the filter chain rejects a request
    // that lacks a valid token or permission.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to perform this action", null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password", null);
    }

    // Fired when a DB unique constraint wins a registration race (two
    // concurrent signups with the same email) — surface as 409 Conflict,
    // not a 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return build(HttpStatus.CONFLICT, "Conflict", "That username or email is already in use", null);
    }

    // Two requests updated the same product row concurrently and one lost
    // the optimistic-lock version check.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "Conflict",
                "This listing was modified by another request. Please reload and try again.", null);
    }

    // A grab-bag of client mistakes that previously fell through to the 500
    // catch-all: non-numeric path ids, malformed JSON bodies, missing query
    // params, unsupported content types.
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid request", null);
    }

    // Upload exceeds the 5MB multipart cap.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "Payload Too Large",
                "Image exceeds the maximum allowed size (5 MB)", null);
    }

    // Unknown route / missing resource → clean 404 JSON.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Not Found", "Resource not found", null);
    }

    // Last-resort catch-all. Deliberately does NOT expose ex.getMessage() or
    // a stack trace to the client — that can leak internal details (SQL,
    // class names, file paths). Log it server-side instead.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        log.error("Unhandled exception processing {}", request.getRequestURI(), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Something went wrong. Please try again later.", null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String error, String message, List<String> details) {
        return build(status, error, message, details, null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String error, String message, List<String> details, Integer retryAfterSeconds) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .details(details)
                .retryAfterSeconds(retryAfterSeconds)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
