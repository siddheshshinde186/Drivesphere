package com.drivesphere.exception;

import com.drivesphere.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Global Exception Handler intercepting exceptions across controllers
 * and serializing standardized ErrorResponse payloads with strict null-safety.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleApiException(@NonNull ApiException ex, @NonNull HttpServletRequest request) {
        HttpStatus status = ex.getStatus() != null ? ex.getStatus() : HttpStatus.BAD_REQUEST;
        String message = ex.getMessage() != null ? ex.getMessage() : "API error occurred";
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleValidationExceptions(@NonNull MethodArgumentNotValidException ex, @NonNull HttpServletRequest request) {
        List<String> details = new ArrayList<>();
        if (ex.getBindingResult() != null) {
            for (FieldError error : ex.getBindingResult().getFieldErrors()) {
                if (error != null) {
                    String field = error.getField();
                    String defaultMessage = error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
                    details.add(field + ": " + defaultMessage);
                }
            }
        }

        String path = request.getRequestURI() != null ? request.getRequestURI() : "";

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input arguments provided")
                .details(details)
                .path(path)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(BadCredentialsException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(@NonNull BadCredentialsException ex, @NonNull HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid email or password credentials";
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(@NonNull AccessDeniedException ex, @NonNull HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "You do not have permission to access this resource";
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(@NonNull IllegalArgumentException ex, @NonNull HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid argument provided";
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleGeneralException(@NonNull Exception ex, @NonNull HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred";
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
