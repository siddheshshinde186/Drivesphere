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

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Global Exception Handler intercepting exceptions across controllers
 * and serializing standardized ErrorResponse payloads with strict null-safety.
 */
@Slf4j
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

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(@NonNull org.springframework.http.converter.HttpMessageNotReadableException ex, @NonNull HttpServletRequest request) {
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .code("MALFORMED_REQUEST")
                .message("Malformed request payload or invalid format.")
                .path(path)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(@NonNull ResourceNotFoundException ex, @NonNull HttpServletRequest request) {
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage() != null ? ex.getMessage() : "Resource not found")
                .path(path)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(IllegalStateException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleIllegalStateException(@NonNull IllegalStateException ex, @NonNull HttpServletRequest request) {
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict / Invalid State")
                .message(ex.getMessage() != null ? ex.getMessage() : "Operation conflict with current resource state")
                .path(path)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler({org.springframework.security.authentication.LockedException.class, org.springframework.security.authentication.DisabledException.class})
    @NonNull
    public ResponseEntity<ErrorResponse> handleAccountStatusException(@NonNull Exception ex, @NonNull HttpServletRequest request) {
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.FORBIDDEN.value())
                .error("Account Restricted")
                .message(ex.getMessage() != null ? ex.getMessage() : "Account access restricted")
                .path(path)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(DuplicateAccountException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleDuplicateAccountException(@NonNull DuplicateAccountException ex, @NonNull HttpServletRequest request) {
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .code(ex.getCode())
                .message(ex.getMessage())
                .path(path)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(@NonNull DataIntegrityViolationException ex, @NonNull HttpServletRequest request) {
        log.warn("Database constraint violation at path {}: {}", request.getRequestURI(), ex.getMessage());
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";

        String raw = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            raw += " " + ex.getCause().getMessage().toLowerCase();
        }

        HttpStatus status = HttpStatus.CONFLICT;
        String code = "DATA_CONFLICT";
        String message = "Operation could not be completed due to a database constraint violation.";

        boolean isUserRelated = path.contains("/auth") || path.contains("/users") || raw.contains("users");

        if (isUserRelated && (raw.contains("phone_number") || raw.contains("uk9q63snka3mdh91as4io72espi") || raw.contains("phone"))) {
            code = "PHONE_ALREADY_REGISTERED";
            message = "An account is already associated with this phone number. Please log in to your existing account.";
        } else if (isUserRelated && (raw.contains("email") || raw.contains("users.email") || raw.contains("uk_email"))) {
            code = "EMAIL_ALREADY_REGISTERED";
            message = "An account with this email address already exists. Please log in or recover your account.";
        } else if (isUserRelated && (raw.contains("duplicate") || raw.contains("unique"))) {
            code = "ACCOUNT_ALREADY_EXISTS";
            message = "An account with these details already exists. Please log in or recover your account.";
        } else if (raw.contains("cannot be null") || raw.contains("not-null") || raw.contains("null value in column")) {
            code = "REQUIRED_FIELD_MISSING";
            message = "A required database field is missing or cannot be null.";
            status = HttpStatus.BAD_REQUEST;
        } else if (raw.contains("foreign key") || raw.contains("constraint fails") || raw.contains("fk_")) {
            code = "FOREIGN_KEY_VIOLATION";
            message = "Operation refers to a missing or invalid related record.";
            status = HttpStatus.CONFLICT;
        } else if (raw.contains("duplicate") || raw.contains("unique constraint") || raw.contains("unique")) {
            code = "DUPLICATE_RECORD";
            message = "A record with these details already exists.";
            status = HttpStatus.CONFLICT;
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(path)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(@NonNull org.springframework.web.multipart.MaxUploadSizeExceededException ex, @NonNull HttpServletRequest request) {
        log.warn("Uploaded file exceeds maximum configured size limit: {}", ex.getMessage());
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .error("Payload Too Large")
                .code("PAYLOAD_TOO_LARGE")
                .message("File size exceeds 5MB limit. Please upload an image smaller than 5 MB.")
                .path(path)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleMultipartException(@NonNull org.springframework.web.multipart.MultipartException ex, @NonNull HttpServletRequest request) {
        log.warn("Multipart upload error at {}: {}", request.getRequestURI(), ex.getMessage());
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .code("MULTIPART_ERROR")
                .message(ex.getMessage() != null ? ex.getMessage() : "Invalid multipart request.")
                .path(path)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleGeneralException(@NonNull Exception ex, @NonNull HttpServletRequest request) {
        log.error("Unhandled exception at path {}: ", request.getRequestURI(), ex);
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .code("INTERNAL_SERVER_ERROR")
                .message("We couldn't complete your request right now. Please try again in a moment.")
                .path(path)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
