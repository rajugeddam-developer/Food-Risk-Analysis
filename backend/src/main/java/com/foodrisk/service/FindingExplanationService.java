package com.foodrisk.service;

import com.foodrisk.dto.ExplainFindingRequest;
import com.foodrisk.dto.ExplainFindingResponse;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.gemini.GeminiClient;
import com.foodrisk.nutrition.NutritionFinding;
import com.foodrisk.risk.IngredientRiskItem;
import com.foodrisk.scoring.FoodRiskAssessment;
import com.foodrisk.scoring.FoodRiskAssessmentService;
import com.foodrisk.service.context.AnalysisContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service providing user-triggered, grounded educational explanations ("Know More").
 *
 * Strict Security & Factual Integrity:
 * - Validates session existence and user ownership.
 * - Server validates requested findings against verified session assessment data.
 * - Client cannot pass unverified claims or force classifications.
 * - Strict Gemini prompt guardrails: no medical advice, no score changes, preserve classifications.
 * - Fallback to deterministic summaries on Gemini failure or invalid output.
 */
@Service
public class FindingExplanationService {

    private static final Logger log = LoggerFactory.getLogger(FindingExplanationService.class);

    private final AnalysisSessionService sessionService;
    private final AnalysisContextStore contextStore;
    private final FoodRiskAssessmentService assessmentService;
    private final GeminiClient geminiClient;

    public FindingExplanationService(
            AnalysisSessionService sessionService,
            AnalysisContextStore contextStore,
            FoodRiskAssessmentService assessmentService,
            GeminiClient geminiClient
    ) {
        this.sessionService = sessionService;
        this.contextStore = contextStore;
        this.assessmentService = assessmentService;
        this.geminiClient = geminiClient;
    }

    public ExplainFindingResponse explainFinding(UUID sessionId, ExplainFindingRequest request, Authentication authentication) {
        if (request == null || request.itemName() == null || request.itemName().isBlank()) {
            throw new IllegalArgumentException("Item name to explain cannot be blank.");
        }

        // 1. Session verification & ownership check
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);
        if (session != null && session.getUser() != null) {
            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
                throw new AccessDeniedException("Authentication required to access this analysis session.");
            }
            if (!session.getUser().getEmail().equalsIgnoreCase(authentication.getName())) {
                throw new AccessDeniedException("You do not have permission to access this session's findings.");
            }
        }

        // 2. Load verified analysis
        FoodRiskAssessment assessment = contextStore.getFoodRiskAssessment(sessionId)
                .orElseGet(() -> assessmentService.getAssessment(sessionId));

        String query = request.itemName().trim();

        // 3. Locate finding in verified items or nutrition findings
        VerifiedFindingContext finding = locateVerifiedFinding(assessment, query);
        if (finding == null) {
            throw new IllegalArgumentException("Finding '" + query + "' was not detected in this verified session analysis.");
        }

        // 4. Construct server-side factual payload for Gemini
        String systemPrompt = """
                You are an objective food science educator. Explain the following verified food finding in 2 to 3 concise, clear sentences for a consumer.
                STRICT SCIENTIFIC GUARDRAILS:
                1. Rely ONLY on the verified factual context provided below.
                2. NEVER diagnose disease, offer clinical or medical advice, or recommend treatment.
                3. PRESERVE the exact assessment level and regulatory status; never contradict or upgrade/downgrade them.
                4. NEVER invent health risks, animal/human studies, bans, or sources not mentioned in the context.
                5. NEVER discuss, alter, or reference the numerical Food Awareness Score.
                6. If the assessment is UNKNOWN or evidence is INSUFFICIENT, state clearly that it could not be verified against the configured knowledge base.
                7. Keep the response under 80 words in plain, accessible English. Avoid sensational or alarmist language.
                """;

        String factualContext = String.format("""
                Item / Finding: %s
                Assessment Level: %s
                Regulatory Status: %s
                Evidence Status: %s
                Deterministic Reason: %s
                Observed Context: %s
                Authoritative Sources: %s
                """,
                finding.canonicalName(),
                finding.assessmentLevel(),
                finding.regulatoryStatus(),
                finding.evidenceStatus(),
                finding.reason(),
                finding.observedContext(),
                finding.sources().isEmpty() ? "Not specified" : String.join(", ", finding.sources())
        );

        // 5. Try calling Gemini
        String aiResponse = null;
        try {
            aiResponse = geminiClient.generateExplanation(systemPrompt, factualContext);
        } catch (Exception ex) {
            log.warn("Gemini call for finding '{}' failed: {}", finding.canonicalName(), ex.getMessage());
        }

        // 6. Validate AI response
        if (aiResponse != null && isValidExplanation(aiResponse, finding)) {
            return new ExplainFindingResponse(
                    finding.canonicalName(),
                    aiResponse.trim(),
                    finding.sources(),
                    true,
                    Instant.now()
            );
        }

        // 7. Deterministic Fallback if Gemini failed or output was invalid
        String fallback = deriveDeterministicExplanation(finding);
        return new ExplainFindingResponse(
                finding.canonicalName(),
                fallback,
                finding.sources(),
                false,
                Instant.now()
        );
    }

    private VerifiedFindingContext locateVerifiedFinding(FoodRiskAssessment assessment, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        // A. Search evaluated ingredient/additive items
        if (assessment.items() != null) {
            for (IngredientRiskItem item : assessment.items()) {
                String name = item.normalizedName() != null ? item.normalizedName() : item.originalIngredient();
                if (name != null && name.toLowerCase(Locale.ROOT).equals(lowerQuery)) {
                    return toFindingContext(item);
                }
                if (item.originalIngredient() != null && item.originalIngredient().toLowerCase(Locale.ROOT).equals(lowerQuery)) {
                    return toFindingContext(item);
                }
                if (item.additiveCode() != null) {
                    String code = item.additiveCode();
                    if (code.equalsIgnoreCase(lowerQuery) || code.replaceAll("\\s+", "").equalsIgnoreCase(lowerQuery.replaceAll("\\s+", ""))) {
                        return toFindingContext(item);
                    }
                }
            }
            // Secondary match: substring contains
            for (IngredientRiskItem item : assessment.items()) {
                String name = item.normalizedName() != null ? item.normalizedName() : item.originalIngredient();
                if (name != null && (name.toLowerCase(Locale.ROOT).contains(lowerQuery) || lowerQuery.contains(name.toLowerCase(Locale.ROOT)))) {
                    return toFindingContext(item);
                }
            }
        }

        // B. Search nutrition findings
        if (assessment.nutritionSummary() != null && assessment.nutritionSummary().findings() != null) {
            for (NutritionFinding finding : assessment.nutritionSummary().findings()) {
                String nutrientKey = finding.nutrient() != null ? finding.nutrient().name().toLowerCase(Locale.ROOT) : "";
                String nutrientReadable = nutrientKey.replace('_', ' ');
                if (nutrientKey.equals(lowerQuery) || nutrientReadable.equals(lowerQuery)
                        || lowerQuery.contains(nutrientReadable) || nutrientReadable.contains(lowerQuery)) {
                    return toFindingContext(finding);
                }
            }
        }

        return null;
    }

    private VerifiedFindingContext toFindingContext(IngredientRiskItem item) {
        String name = item.normalizedName() != null ? item.normalizedName() : item.originalIngredient();
        List<String> sources = item.sourceIds() != null ? item.sourceIds() : List.of();
        String reason = item.reasons() != null && !item.reasons().isEmpty() ? String.join("; ", item.reasons()) : (item.summary() != null ? item.summary() : "Declared ingredient.");
        String context = item.functionalClass() != null ? ("Functional class: " + item.functionalClass()) : "Present in declared ingredient list.";
        return new VerifiedFindingContext(
                name,
                item.riskLevel() != null ? item.riskLevel().name() : "UNKNOWN",
                item.regulatoryStatus() != null ? item.regulatoryStatus().name() : "UNKNOWN",
                item.evidenceStatus() != null ? item.evidenceStatus().name() : "INSUFFICIENT",
                reason,
                context,
                sources,
                item.summary()
        );
    }

    private VerifiedFindingContext toFindingContext(NutritionFinding finding) {
        String name = finding.nutrient() != null ? finding.nutrient().name().replace('_', ' ') : "Nutrient";
        List<String> sources = finding.sourceIds() != null ? finding.sourceIds() : List.of("WHO", "FSSAI");
        String context = String.format("Observed value: %s %s (Basis: %s)",
                finding.observedValue(), finding.observedUnit(), finding.normalizedBasis());
        return new VerifiedFindingContext(
                name,
                finding.status() != null ? finding.status().name() : "EVALUATED",
                "MONITORED_STANDARD",
                "STRONG",
                finding.reason(),
                context,
                sources,
                finding.reason()
        );
    }

    private boolean isValidExplanation(String text, VerifiedFindingContext finding) {
        if (text.isBlank() || text.length() > 650) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        // Guard against hallucinated medical diagnosis
        if (lower.contains("cure") || lower.contains("diagnos") || lower.contains("treats") || lower.contains("disease")) {
            return false;
        }
        // If safe/no concern, guard against alarming terms
        if (finding.assessmentLevel().contains("NO_CONCERN") || finding.assessmentLevel().contains("POSITIVE")) {
            if (lower.contains("toxic") || lower.contains("carcinogen") || lower.contains("poison") || lower.contains("banned")) {
                return false;
            }
        }
        return true;
    }

    private String deriveDeterministicExplanation(VerifiedFindingContext finding) {
        if (finding.summary() != null && !finding.summary().isBlank()) {
            return finding.summary();
        }
        if (finding.reason() != null && !finding.reason().isBlank()) {
            return finding.reason();
        }
        if ("UNKNOWN".equalsIgnoreCase(finding.assessmentLevel())) {
            return "This ingredient could not be verified against the configured knowledge base.";
        }
        return String.format("%s is assessed as %s based on authoritative references.",
                finding.canonicalName(), finding.assessmentLevel());
    }

    private record VerifiedFindingContext(
            String canonicalName,
            String assessmentLevel,
            String regulatoryStatus,
            String evidenceStatus,
            String reason,
            String observedContext,
            List<String> sources,
            String summary
    ) {}
}
