package com.restaurant.restaurantpos.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntityNotFoundException(jakarta.persistence.EntityNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Resource not found");
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccessException(org.springframework.dao.DataAccessException ex) {
        logger.error("Database infrastructure failure: errorClass={}", ex.getClass().getSimpleName());
        Map<String, String> error = new HashMap<>();
        error.put("error", "Service Unavailable");
        error.put("message", "Database service temporarily unavailable");
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(RevocationStorageException.class)
    public ResponseEntity<Map<String, String>> handleRevocationStorageException(RevocationStorageException ex) {
        logger.error("Token revocation persistence failure: errorClass={}", ex.getClass().getSimpleName());
        Map<String, String> error = new HashMap<>();
        error.put("message", "Revocation store temporarily unavailable. Please try again.");
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(DuplicateSessionException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateSessionException(DuplicateSessionException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmailException(DuplicateEmailException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(KitchenWorkflowDisabledException.class)
    public ResponseEntity<Map<String, String>> handleKitchenWorkflowDisabledException(KitchenWorkflowDisabledException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(OrderWorkflowConflictException.class)
    public ResponseEntity<Map<String, String>> handleOrderWorkflowConflictException(OrderWorkflowConflictException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, String>> handleDisabledException(DisabledException ex) {
        logger.warn("Authentication rejected due to deactivated account: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Account is deactivated");
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleAuthenticationException(Exception ex) {
        Throwable cause = ex.getCause();
        if (ex instanceof DisabledException || cause instanceof DisabledException) {
            String msg = (ex instanceof DisabledException) ? ex.getMessage() : cause.getMessage();
            logger.warn("Authentication rejected due to deactivated account: {}", msg);
            Map<String, String> error = new HashMap<>();
            error.put("message", msg != null ? msg : "Account is deactivated");
            return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
        }

        logger.warn("Authentication failed: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("message", "Invalid email or password");
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, String> error = new HashMap<>();
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "Access denied";
        }
        error.put("message", msg);
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "An error occurred";
        String lowerMessage = message.toLowerCase();

        Map<String, String> error = new HashMap<>();
        error.put("message", message);

        if (lowerMessage.contains("not found")) {
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }
        if (lowerMessage.contains("already exists") || lowerMessage.contains("already has an active order") || lowerMessage.contains("table is not available") || lowerMessage.contains("only be modified while status is new")) {
            return new ResponseEntity<>(error, HttpStatus.CONFLICT);
        }
        if (lowerMessage.contains("invalid") || lowerMessage.contains("require") || lowerMessage.contains("cannot") || lowerMessage.contains("must") || lowerMessage.contains("exceed") || lowerMessage.contains("zero") || lowerMessage.contains("inactive") || lowerMessage.contains("ready") || lowerMessage.contains("only be completed") || lowerMessage.contains("not available")) {
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        logger.error("Unhandled runtime exception: {}", ex.getMessage(), ex);
        Map<String, String> genericError = new HashMap<>();
        genericError.put("message", "An unexpected error occurred. Please contact system administrator.");
        return new ResponseEntity<>(genericError, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        logger.error("Unhandled general exception: {}", ex.getMessage(), ex);
        Map<String, String> genericError = new HashMap<>();
        genericError.put("message", "An unexpected error occurred. Please contact system administrator.");
        return new ResponseEntity<>(genericError, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        if (ex.getBindingResult().getFieldError() != null) {
            errors.put("message", ex.getBindingResult().getFieldError().getDefaultMessage());
        } else {
            errors.put("message", "Validation failed");
        }
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
}
