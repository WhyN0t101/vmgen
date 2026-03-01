package io.vmgen.api.exceptions;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthFailed(AuthenticationFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountLocked(AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(Map.of(
                        "message", ex.getPublicMessage(),
                        "retryAfter", ex.getRetryAfterSeconds()
                ));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "message", ex.getPublicMessage(),
                        "retryAfter", ex.getResetSeconds()
                ));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "message", ex.getPublicMessage(),
                        "details", ex.getDetails()
                ));
    }

    @ExceptionHandler(ParameterValidationException.class)
    public ResponseEntity<Map<String, Object>> handleParameterValidation(ParameterValidationException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "message", ex.getPublicMessage(),
                        "errors", ex.getErrors()
                ));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTemplateNotFound(TemplateNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(TemplateValidationException.class)
    public ResponseEntity<Map<String, Object>> handleTemplateValidation(TemplateValidationException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(DuplicateTemplateException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateTemplate(DuplicateTemplateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(ProxmoxConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleProxmoxConnection(ProxmoxConnectionException ex) {
        log.error("Proxmox connection error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(ProxmoxVmNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProxmoxVmNotFound(ProxmoxVmNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(ImmutableFieldUpdateException.class)
    public ResponseEntity<Map<String, Object>> handleImmutableField(ImmutableFieldUpdateException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"
                ))
                .toList();

        return ResponseEntity.badRequest()
                .body(Map.of(
                        "message", "Validation failed",
                        "errors", errors
                ));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMissingBody() {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Request body is required or malformed"));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Invalid request parameters"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        // Log full stack trace for debugging
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        // Return generic message to client
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Internal server error"));
    }
}