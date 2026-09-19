package com.foodrisk.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.exception.NormalizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for invoking Google Gemini API using Spring Boot RestClient.
 *
 * Security & Privacy constraints:
 * - The Gemini API key is strictly server-side from GEMINI_API_KEY.
 * - NEVER logs or exposes the API key in exception messages or HTTP responses.
 * - Translates low-level provider errors into safe application-level NormalizationExceptions.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

    private final GeminiProperties properties;
    private final GeminiPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    private static final String GEMINI_API_KEY_HEADER = "x-goog-api-key";

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiClient(
            GeminiProperties properties,
            GeminiPromptBuilder promptBuilder,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        this(properties, promptBuilder, objectMapper, createDefaultRestClient(properties, restClientBuilder));
    }

    public GeminiClient(
            GeminiProperties properties,
            GeminiPromptBuilder promptBuilder,
            ObjectMapper objectMapper,
            RestClient restClient
    ) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static RestClient createDefaultRestClient(GeminiProperties properties, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = properties.getTimeoutSeconds() * 1000;
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        return restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    public String buildGenerateContentUrl() {
        return "%s/%s:generateContent".formatted(
                GEMINI_API_BASE,
                properties.getModel()
        );
    }

    public NormalizedFoodData normalize(String ingredientText, String nutritionText) {
        validateConfiguration();

        String prompt = promptBuilder.buildPrompt(ingredientText, nutritionText);
        String requestUrl = buildGenerateContentUrl();

        Map<String, Object> requestPayload = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", prompt))
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );

        log.info("Sending food normalization prompt to Gemini model '{}' (timeout: {}s)",
                properties.getModel(), properties.getTimeoutSeconds());

        try {
            String rawResponseBody = restClient.post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(GEMINI_API_KEY_HEADER, properties.getApiKey())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(requestPayload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        log.error("Gemini API error status: {}", code);
                        if (code == 401 || code == 403) {
                            throw new NormalizationException("AI_AUTHENTICATION_ERROR", "AI service authentication failed.");
                        } else if (code == 429) {
                            throw new NormalizationException("AI_RATE_LIMIT_EXCEEDED", "AI normalization service is currently busy. Please try again shortly.");
                        } else if (code == 400) {
                            throw new NormalizationException("AI_BAD_REQUEST", "Invalid request submitted to AI normalization service.");
                        } else {
                            throw new NormalizationException("AI_SERVICE_UNAVAILABLE", "Food normalization is temporarily unavailable. Please try again.");
                        }
                    })
                    .body(String.class);

            return parseGeminiResponse(rawResponseBody);

        } catch (NormalizationException ne) {
            throw ne;
        } catch (ResourceAccessException rae) {
            log.error("Gemini request connection timed out or failed: {}", rae.getMessage());
            throw new NormalizationException("AI_TIMEOUT", "Food normalization request timed out. Please try again.", rae);
        } catch (RestClientResponseException rcre) {
            int code = rcre.getStatusCode().value();
            log.error("Gemini RestClient exception with status {}", code);
            if (code == 401 || code == 403) {
                throw new NormalizationException("AI_AUTHENTICATION_ERROR", "AI service authentication failed.", rcre);
            } else if (code == 429) {
                throw new NormalizationException("AI_RATE_LIMIT_EXCEEDED", "AI normalization service is currently busy. Please try again shortly.", rcre);
            } else {
                throw new NormalizationException("AI_SERVICE_UNAVAILABLE", "Food normalization is temporarily unavailable. Please try again.", rcre);
            }
        } catch (Exception ex) {
            log.error("Unexpected error during Gemini normalization: {}", ex.getMessage());
            throw new NormalizationException("AI_SERVICE_UNAVAILABLE", "Food normalization is temporarily unavailable. Please try again.", ex);
        }
    }

    public NormalizedFoodData parseGeminiResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new NormalizationException("AI_EMPTY_RESPONSE", "Received empty response from AI normalization engine.");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new NormalizationException("AI_EMPTY_RESPONSE", "No response candidates returned by AI normalization engine.");
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode parts = firstCandidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new NormalizationException("AI_EMPTY_RESPONSE", "Candidate returned empty parts array.");
            }

            String contentText = parts.get(0).path("text").asText("");
            if (contentText.isBlank()) {
                throw new NormalizationException("AI_EMPTY_RESPONSE", "Empty text content in AI normalization response.");
            }

            // Strip Markdown code fencing if present
            String cleanedJson = stripMarkdownCodeFence(contentText);

            return objectMapper.readValue(cleanedJson, NormalizedFoodData.class);

        } catch (NormalizationException ne) {
            throw ne;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response payload into NormalizedFoodData: {}", e.getMessage());
            throw new NormalizationException("AI_MALFORMED_OUTPUT", "AI normalization service returned malformed food data structure.", e);
        }
    }

    private String stripMarkdownCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public String generateExplanation(String systemPrompt, String factualContext) {
        validateConfiguration();
        String prompt = systemPrompt + "\n\nFACTUAL CONTEXT:\n" + factualContext;
        String requestUrl = buildGenerateContentUrl();

        Map<String, Object> requestPayload = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", prompt))
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 500
                )
        );

        log.info("Requesting explanation from Gemini for factual context");
        try {
            String rawResponseBody = restClient.post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(GEMINI_API_KEY_HEADER, properties.getApiKey())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            return extractCandidateText(rawResponseBody);
        } catch (Exception ex) {
            log.warn("Gemini explanation generation call failed: {}", ex.getMessage());
            return null;
        }
    }

    public String extractCandidateText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return null;
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return null;
            }
            String text = parts.get(0).path("text").asText("");
            return text.isBlank() ? null : text.trim();
        } catch (Exception e) {
            log.warn("Failed to extract text from Gemini response: {}", e.getMessage());
            return null;
        }
    }

    private void validateConfiguration() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.error("Gemini API key is not configured in GEMINI_API_KEY environment variable.");
            throw new NormalizationException("AI_UNCONFIGURED", "AI normalization service is not configured on this server.");
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            log.error("Gemini model is not configured in GEMINI_MODEL environment variable.");
            throw new NormalizationException("AI_UNCONFIGURED", "AI normalization model is not configured on this server.");
        }
    }
}
