package com.foodrisk.exception;

import com.foodrisk.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Milestone M13: Centralized Global Exception Handler.
 *
 * Provides standardized, secure error envelopes (ErrorResponse) across all controllers:
 * - Sanitized user-facing messages
 * - Structured machine-readable ErrorCategory codes
 * - Request correlation tracking (requestId from MDC / X-Request-ID)
 * - Strict security: zero leakage of stack traces, database URLs, credentials, or internal details
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Registration conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ErrorCategory.INVALID_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, ErrorCategory.INVALID_REQUEST, "Invalid email or password.", request, null);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ErrorCategory.INVALID_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Request validation failed on {}: {}", request.getRequestURI(), errors);
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCategory.INVALID_REQUEST, "Validation failed", request, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String paramName = ex.getName();
        Object value = ex.getValue();
        String message = String.format("Invalid format for parameter '%s' with value '%s'.", paramName, value);
        log.warn("Parameter type mismatch on {}: {}", request.getRequestURI(), message);
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCategory.INVALID_REQUEST, message, request, null);
    }

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidImage(InvalidImageException ex, HttpServletRequest request) {
        log.warn("Invalid image upload: {}", ex.getMessage());
        ErrorCategory category = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unsupported")
                ? ErrorCategory.UNSUPPORTED_IMAGE_FORMAT
                : ErrorCategory.INVALID_IMAGE;
        return buildResponse(HttpStatus.BAD_REQUEST, category, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ProductMismatchException.class)
    public ResponseEntity<ErrorResponse> handleProductMismatch(ProductMismatchException ex, HttpServletRequest request) {
        log.warn("Product mismatch detected on {}: {} (details: {})", request.getRequestURI(), ex.getMessage(), ex.getMismatchReason());
        Map<String, String> details = null;
        if (ex.getMismatchReason() != null) {
            details = Map.of("mismatchReason", ex.getMismatchReason());
        }
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCategory.PRODUCT_MISMATCH, ex.getMessage(), request, details);
    }

    @ExceptionHandler(ImageSizeLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleImageSizeExceeded(ImageSizeLimitExceededException ex, HttpServletRequest request) {
        log.warn("Image size limit exceeded: {}", ex.getMessage());
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCategory.IMAGE_TOO_LARGE, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Multipart upload exceeded limit: {}", ex.getMessage());
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCategory.IMAGE_TOO_LARGE,
                "Uploaded image exceeds the maximum permitted size limit of 10MB.", request, null);
    }

    @ExceptionHandler(OcrProcessingException.class)
    public ResponseEntity<ErrorResponse> handleOcrProcessingException(OcrProcessingException ex, HttpServletRequest request) {
        log.warn("OCR processing failed [{}]: {}", ex.getCategory(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getCategory(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException ex, HttpServletRequest request) {
        log.info("Session not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ErrorCategory.ANALYSIS_NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSessionExpired(SessionExpiredException ex, HttpServletRequest request) {
        log.info("Session expired: {}", ex.getMessage());
        return buildResponse(HttpStatus.GONE, ErrorCategory.ANALYSIS_EXPIRED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(SessionNotReadyException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotReady(SessionNotReadyException ex, HttpServletRequest request) {
        log.warn("Session step conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ErrorCategory.ANALYSIS_FAILED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(NormalizationException.class)
    public ResponseEntity<ErrorResponse> handleNormalizationException(NormalizationException ex, HttpServletRequest request) {
        log.error("AI normalization failure [{}]: {}", ex.getErrorCode(), ex.getMessage());

        HttpStatus status;
        ErrorCategory category;
        String message;

        switch (ex.getErrorCode() != null ? ex.getErrorCode() : "") {
            case "AI_TIMEOUT" -> {
                status = HttpStatus.GATEWAY_TIMEOUT;
                category = ErrorCategory.AI_SERVICE_TIMEOUT;
                message = "Food normalization request timed out. Please try again.";
            }
            case "AI_SERVICE_UNAVAILABLE", "AI_UNCONFIGURED" -> {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                category = ErrorCategory.AI_SERVICE_UNAVAILABLE;
                message = "Food normalization is temporarily unavailable. Please try again shortly.";
            }
            case "AI_RATE_LIMIT_EXCEEDED" -> {
                status = HttpStatus.TOO_MANY_REQUESTS;
                category = ErrorCategory.RATE_LIMIT_EXCEEDED;
                message = "AI normalization service is currently busy. Please try again shortly.";
            }
            case "AI_MALFORMED_OUTPUT", "AI_INVALID_NUTRITION", "AI_EMPTY_RESPONSE" -> {
                status = HttpStatus.BAD_GATEWAY;
                category = ErrorCategory.AI_RESPONSE_INVALID;
                message = "AI normalization returned an invalid food data structure. Please try capturing clearer packaging photos.";
            }
            default -> {
                status = HttpStatus.BAD_GATEWAY;
                category = ErrorCategory.AI_RESPONSE_INVALID;
                message = ex.getMessage();
            }
        }

        return buildResponse(status, category, message, request, null);
    }

    @ExceptionHandler(AnalysisTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisTimeout(AnalysisTimeoutException ex, HttpServletRequest request) {
        log.error("Analysis pipeline timeout: {}", ex.getMessage());
        return buildResponse(HttpStatus.GATEWAY_TIMEOUT, ErrorCategory.ANALYSIS_TIMEOUT, ex.getMessage(), request, null);
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class, SQLException.class})
    public ResponseEntity<ErrorResponse> handleDatabaseException(Exception ex, HttpServletRequest request) {
        // Security guarantee: Never expose JDBC URL, database credentials, hostnames, or SQL queries to client
        log.error("Database connectivity error on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ErrorCategory.DATABASE_UNAVAILABLE,
                "The database service is temporarily unavailable. Please try again shortly.", request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCategory.INVALID_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        Throwable root = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(ex);
        if (root instanceof ProductMismatchException pme) {
            return handleProductMismatch(pme, request);
        }
        if (root instanceof InvalidImageException iie) {
            return handleInvalidImage(iie, request);
        }

        // Security guarantee: Suppress stack traces, internal class names, and JVM internals
        log.error("Unhandled server error on [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCategory.INTERNAL_ERROR,
                "An unexpected internal error occurred. Please try again later.", request, null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            ErrorCategory category,
            String message,
            HttpServletRequest request,
            Map<String, String> errors
    ) {
        String requestId = MDC.get("requestId");
        if ((requestId == null || requestId.isBlank()) && request != null) {
            requestId = request.getHeader("X-Request-ID");
        }
        String path = request != null ? request.getRequestURI() : null;

        ErrorResponse envelope = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.name(),
                category != null ? category.name() : status.name(),
                message,
                path,
                requestId,
                errors
        );

        return ResponseEntity.status(status).body(envelope);
    }
}
