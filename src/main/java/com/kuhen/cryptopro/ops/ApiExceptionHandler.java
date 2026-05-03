package com.kuhen.cryptopro.ops;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final OpsTelemetryService opsTelemetryService;

    public ApiExceptionHandler(OpsTelemetryService opsTelemetryService) {
        this.opsTelemetryService = opsTelemetryService;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex,
                                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", Instant.now().toString(),
                "path", request.getRequestURI(),
                "message", "Resource not found"
        ));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", Instant.now().toString(),
                "path", request.getRequestURI(),
                "message", "Resource not found"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex, HttpServletRequest request) {
        opsTelemetryService.recordError(new ErrorLogEntry(
                Instant.now(),
                request.getRequestURI(),
                ex.getMessage() == null ? "Unexpected error" : ex.getMessage(),
                ex.getClass().getSimpleName()
        ));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "timestamp", Instant.now().toString(),
                "path", request.getRequestURI(),
                "message", "Internal server error"
        ));
    }
}

