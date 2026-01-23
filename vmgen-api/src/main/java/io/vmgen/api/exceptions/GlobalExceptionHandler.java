package io.vmgen.api.exceptions;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthFailed(AuthenticationFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleExpired(SessionExpiredException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(SessionRevokedException.class)
    public ResponseEntity<Map<String, Object>> handleRevoked(SessionRevokedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "message", ex.getPublicMessage(),
                "retryAfter", ex.getResetSeconds()
        ));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", ex.getPublicMessage(),
                "details", ex.getDetails()
        ));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTemplateNotFound(TemplateNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(TemplateValidationException.class)
    public ResponseEntity<Map<String, Object>> handleTemplateValidation(TemplateValidationException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(DuplicateTemplateException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateTemplate(DuplicateTemplateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(ProxmoxConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleProxmoxConnection(ProxmoxConnectionException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(ProxmoxVmNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProxmoxVmNotFound(ProxmoxVmNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(ImmutableFieldUpdateException.class)
    public ResponseEntity<Map<String, Object>> handleImmutableField(ImmutableFieldUpdateException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getPublicMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBeanValidation() {
        return ResponseEntity.badRequest().body(Map.of(
                "message", "Validation failed"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Internal server error"));
    }
}


