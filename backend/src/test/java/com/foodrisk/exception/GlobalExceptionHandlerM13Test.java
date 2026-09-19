package com.foodrisk.exception;

import com.foodrisk.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M13 Reliability Tests: Global Exception Handler.
 * Verifies standard error responses, HTTP status mappings, correlation ID preservation,
 * and strict suppression of internal stack traces, DB credentials, and raw SQL.
 */
class GlobalExceptionHandlerM13Test {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/analysis/test-session/analyze");
        MDC.clear();
    }

    @Test
    @DisplayName("Invalid UUID path parameter returns 400 INVALID_REQUEST")
    void testInvalidUuidParameter() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerM13Test.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "not-a-uuid", String.class, "sessionId", parameter, null);

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.INVALID_REQUEST.name());
        assertThat(response.getBody().message()).contains("Invalid format for parameter 'sessionId'");
    }

    @Test
    @DisplayName("Session not found returns 404 ANALYSIS_NOT_FOUND")
    void testSessionNotFound() {
        SessionNotFoundException ex = new SessionNotFoundException("Analysis session not found: 12345");

        ResponseEntity<ErrorResponse> response = handler.handleSessionNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.ANALYSIS_NOT_FOUND.name());
        assertThat(response.getBody().message()).contains("12345");
    }

    @Test
    @DisplayName("Session expired returns 410 ANALYSIS_EXPIRED")
    void testSessionExpired() {
        SessionExpiredException ex = new SessionExpiredException("Analysis session has expired. Please initiate a new scan.");

        ResponseEntity<ErrorResponse> response = handler.handleSessionExpired(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.ANALYSIS_EXPIRED.name());
        assertThat(response.getBody().message()).contains("expired");
    }

    @Test
    @DisplayName("Invalid image returns 400 INVALID_IMAGE")
    void testInvalidImage() {
        InvalidImageException ex = new InvalidImageException("Uploaded file is not a valid food label image");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidImage(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.INVALID_IMAGE.name());
    }

    @Test
    @DisplayName("Unsupported image format returns 400 UNSUPPORTED_IMAGE_FORMAT")
    void testUnsupportedImageFormat() {
        InvalidImageException ex = new InvalidImageException("Unsupported MIME type: 'image/gif'. Allowed formats: JPEG, PNG, WEBP.");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidImage(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.UNSUPPORTED_IMAGE_FORMAT.name());
    }

    @Test
    @DisplayName("Image size limit exceeded returns 413 IMAGE_TOO_LARGE")
    void testImageSizeLimitExceeded() {
        ImageSizeLimitExceededException ex = new ImageSizeLimitExceededException("Image size 15MB exceeds 10MB limit");

        ResponseEntity<ErrorResponse> response = handler.handleImageSizeExceeded(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.IMAGE_TOO_LARGE.name());
    }

    @Test
    @DisplayName("MaxUploadSizeExceeded returns 413 IMAGE_TOO_LARGE")
    void testMaxUploadSizeExceeded() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        ResponseEntity<ErrorResponse> response = handler.handleMaxUploadSizeExceeded(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.IMAGE_TOO_LARGE.name());
        assertThat(response.getBody().message()).contains("10MB");
    }

    @Test
    @DisplayName("OCR low confidence returns 400 OCR_LOW_CONFIDENCE with safe message")
    void testOcrLowConfidence() {
        OcrProcessingException ex = new OcrProcessingException(
                ErrorCategory.OCR_LOW_CONFIDENCE,
                "We couldn't read the label clearly. Please capture a sharper image with the ingredient list fully visible."
        );

        ResponseEntity<ErrorResponse> response = handler.handleOcrProcessingException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.OCR_LOW_CONFIDENCE.name());
        assertThat(response.getBody().message()).contains("sharper image");
    }

    @Test
    @DisplayName("Gemini timeout returns 504 AI_SERVICE_TIMEOUT")
    void testGeminiTimeout() {
        NormalizationException ex = new NormalizationException("AI_TIMEOUT", "Normalization timed out");

        ResponseEntity<ErrorResponse> response = handler.handleNormalizationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.AI_SERVICE_TIMEOUT.name());
        assertThat(response.getBody().message()).doesNotContain("API_KEY");
    }

    @Test
    @DisplayName("Gemini unavailable returns 503 AI_SERVICE_UNAVAILABLE")
    void testGeminiUnavailable() {
        NormalizationException ex = new NormalizationException("AI_SERVICE_UNAVAILABLE", "Gemini endpoint unreachable");

        ResponseEntity<ErrorResponse> response = handler.handleNormalizationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.AI_SERVICE_UNAVAILABLE.name());
    }

    @Test
    @DisplayName("Gemini malformed response returns 502 AI_RESPONSE_INVALID")
    void testGeminiMalformedResponse() {
        NormalizationException ex = new NormalizationException("AI_MALFORMED_OUTPUT", "Invalid JSON syntax from LLM");

        ResponseEntity<ErrorResponse> response = handler.handleNormalizationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.AI_RESPONSE_INVALID.name());
    }

    @Test
    @DisplayName("Analysis timeout returns 504 ANALYSIS_TIMEOUT")
    void testAnalysisTimeout() {
        AnalysisTimeoutException ex = new AnalysisTimeoutException("Analysis timed out after 60 seconds.");

        ResponseEntity<ErrorResponse> response = handler.handleAnalysisTimeout(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.ANALYSIS_TIMEOUT.name());
    }

    @Test
    @DisplayName("Database failure returns 503 DATABASE_UNAVAILABLE and suppresses SQL / credentials")
    void testDatabaseFailureHidesCredentialsAndSql() {
        SQLException sqlEx = new SQLException("Connection to jdbc:postgresql://localhost:5432/food_risk with user 'postgres' and password 'secret' failed: relation 'food_analysis_session' does not exist");
        DataAccessResourceFailureException ex = new DataAccessResourceFailureException("DB down", sqlEx);

        ResponseEntity<ErrorResponse> response = handler.handleDatabaseException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.DATABASE_UNAVAILABLE.name());
        // Verify zero leakage of credentials, host, or SQL
        assertThat(response.getBody().message()).doesNotContain("postgresql");
        assertThat(response.getBody().message()).doesNotContain("localhost");
        assertThat(response.getBody().message()).doesNotContain("secret");
        assertThat(response.getBody().message()).doesNotContain("relation");
        assertThat(response.getBody().message()).isEqualTo("The database service is temporarily unavailable. Please try again shortly.");
    }

    @Test
    @DisplayName("Generic internal exception returns 500 INTERNAL_ERROR and suppresses stack traces")
    void testGenericExceptionHidesStackTraces() {
        NullPointerException ex = new NullPointerException("Null reference at com.foodrisk.internal.Class.method(Class.java:42)");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCategory.INTERNAL_ERROR.name());
        assertThat(response.getBody().message()).isEqualTo("An unexpected internal error occurred. Please try again later.");
        assertThat(response.getBody().message()).doesNotContain("NullPointerException");
        assertThat(response.getBody().message()).doesNotContain("Class.java");
    }

    @Test
    @DisplayName("X-Request-ID header is preserved in ErrorResponse")
    void testRequestIdPreservedFromHeader() {
        String testCorrelationId = "req-custom-correlation-9876";
        request.addHeader("X-Request-ID", testCorrelationId);

        ResponseEntity<ErrorResponse> response = handler.handleSessionNotFound(
                new SessionNotFoundException("Not found"), request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().requestId()).isEqualTo(testCorrelationId);
    }

    @Test
    @DisplayName("MDC requestId is used in ErrorResponse if present")
    void testRequestIdFromMdc() {
        String mdcCorrelationId = "req-mdc-1234";
        MDC.put("requestId", mdcCorrelationId);
        try {
            ResponseEntity<ErrorResponse> response = handler.handleSessionNotFound(
                    new SessionNotFoundException("Not found"), request);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().requestId()).isEqualTo(mdcCorrelationId);
        } finally {
            MDC.clear();
        }
    }

    @SuppressWarnings("unused")
    private void dummyMethod(String sessionId) {
        // Reflection target for MethodParameter test
    }
}
