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

    private static final List<String> CANDIDATE_MODELS = List.of(
            "gemini-1.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-pro"
    );

    public String buildGenerateContentUrl() {
        return buildGenerateContentUrl(properties.getModel());
    }

    public String buildGenerateContentUrl(String modelName) {
        String effectiveModel = (modelName != null && !modelName.isBlank()) ? modelName.trim() : "gemini-1.5-flash";
        return "%s/%s:generateContent".formatted(
                GEMINI_API_BASE,
                effectiveModel
        );
    }

    public List<String> getModelsToTry() {
        List<String> list = new java.util.ArrayList<>();
        if (properties.getModel() != null && !properties.getModel().isBlank()) {
            list.add(properties.getModel().trim());
        }
        for (String fallback : CANDIDATE_MODELS) {
            if (!list.contains(fallback)) {
                list.add(fallback);
            }
        }
        return list;
    }

    public record ImagePayload(byte[] bytes, String contentType) {}

    public NormalizedFoodData normalize(String ingredientText, String nutritionText) {
        return normalizeWithImages(ingredientText, nutritionText, java.util.List.of());
    }

    public NormalizedFoodData normalizeWithImages(String ingredientText, String nutritionText, java.util.List<ImagePayload> images) {
        validateConfiguration();

        boolean hasImages = images != null && !images.isEmpty();
        String prompt = promptBuilder.buildPrompt(ingredientText, nutritionText, hasImages);

        java.util.List<Map<String, Object>> parts = new java.util.ArrayList<>();
        parts.add(Map.of("text", prompt));

        if (hasImages) {
            for (int imgIdx = 0; imgIdx < images.size(); imgIdx++) {
                ImagePayload img = images.get(imgIdx);
                if (img != null && img.bytes() != null && img.bytes().length > 0) {
                    String label = imgIdx == 0
                            ? "Attached Image 1 (Uploaded Ingredients Label Photo):"
                            : "Attached Image 2 (Uploaded Nutrition Facts Label Photo):";
                    parts.add(Map.of("text", label));

                    String mime = img.contentType() != null && !img.contentType().isBlank()
                            ? img.contentType().trim()
                            : "image/jpeg";
                    String base64 = java.util.Base64.getEncoder().encodeToString(img.bytes());
                    parts.add(Map.of(
                            "inlineData", Map.of(
                                    "mimeType", mime,
                                    "data", base64
                            )
                    ));
                }
            }
        }

        Map<String, Object> requestPayload = Map.of(
                "contents", java.util.List.of(
                        Map.of(
                                "role", "user",
                                "parts", parts
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );

        List<String> modelsToTry = getModelsToTry();
        NormalizationException lastException = null;

        for (int i = 0; i < modelsToTry.size(); i++) {
            String model = modelsToTry.get(i);
            String requestUrl = buildGenerateContentUrl(model);

            log.info("Sending food normalization prompt to Gemini model '{}' (attempt {}/{}, images: {}, timeout: {}s)",
                    model, i + 1, modelsToTry.size(), (hasImages ? images.size() : 0), properties.getTimeoutSeconds());

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
                            log.warn("Gemini API error status {} for model '{}'", code, model);
                            if (code == 401 || code == 403) {
                                throw new NormalizationException("AI_AUTHENTICATION_ERROR", "AI service authentication failed.");
                            } else if (code == 429) {
                                throw new NormalizationException("AI_RATE_LIMIT_EXCEEDED", "AI normalization service is currently busy.");
                            } else if (code == 400) {
                                throw new NormalizationException("AI_BAD_REQUEST", "Invalid request submitted to AI normalization service.");
                            } else {
                                throw new NormalizationException("AI_SERVICE_UNAVAILABLE", "Gemini model " + model + " unavailable (HTTP " + code + ").");
                            }
                        })
                        .body(String.class);

                return parseGeminiResponse(rawResponseBody);

            } catch (NormalizationException ne) {
                if ("AI_AUTHENTICATION_ERROR".equals(ne.getErrorCode())) {
                    throw ne;
                }
                log.warn("Normalization attempt with model '{}' failed: {}. Checking next fallback model...", model, ne.getMessage());
                lastException = ne;
            } catch (ResourceAccessException rae) {
                log.warn("Gemini connection timed out for model '{}': {}. Checking next fallback model...", model, rae.getMessage());
                lastException = new NormalizationException("AI_TIMEOUT", "Food normalization request timed out on " + model, rae);
            } catch (RestClientResponseException rcre) {
                int code = rcre.getStatusCode().value();
                log.warn("Gemini RestClient exception ({}) for model '{}'. Checking next fallback model...", code, model);
                if (code == 401 || code == 403) {
                    throw new NormalizationException("AI_AUTHENTICATION_ERROR", "AI service authentication failed.", rcre);
                }
                lastException = new NormalizationException("AI_SERVICE_UNAVAILABLE", "Food normalization failed on " + model, rcre);
            } catch (Exception ex) {
                log.warn("Unexpected error during Gemini normalization on model '{}': {}", model, ex.getMessage());
                lastException = new NormalizationException("AI_SERVICE_UNAVAILABLE", "Food normalization failed on " + model, ex);
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new NormalizationException("AI_SERVICE_UNAVAILABLE", "All candidate Gemini models were unavailable.");
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
            log.info("Gemini raw normalized response: {}", cleanedJson);

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

        for (String model : getModelsToTry()) {
            try {
                String requestUrl = buildGenerateContentUrl(model);
                String rawResponseBody = restClient.post()
                        .uri(requestUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(GEMINI_API_KEY_HEADER, properties.getApiKey())
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .body(requestPayload)
                        .retrieve()
                        .body(String.class);

                String text = extractCandidateText(rawResponseBody);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            } catch (Exception ex) {
                log.warn("Gemini explanation generation with model '{}' failed: {}", model, ex.getMessage());
            }
        }
        return null;
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
