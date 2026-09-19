package com.foodrisk.risk;

import com.foodrisk.dto.NormalizedIngredient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates individual normalized ingredients and additives against verified rules.
 *
 * Core Principles:
 * - Decouples RegulatoryStatus from IngredientRiskLevel (PERMITTED != NO_CONCERN; BANNED is only applied via verified sources).
 * - Normalizes equivalent additive notations (INS 330, INS330, E330 -> INS 330).
 * - Honors M6 uncertainty flags without converting uncertain items to confirmed hazards.
 * - Deduplicates repeated ingredients in summary counts while preserving raw OCR entries for traceability.
 * - ZERO final product score and ZERO Good/Bad/Worst classifications.
 */
@Component
public class IngredientRiskEngine {

    private static final Logger log = LoggerFactory.getLogger(IngredientRiskEngine.class);

    private final RiskRuleRepository ruleRepository;

    public IngredientRiskEngine(RiskRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public IngredientRiskAnalysisResult evaluate(UUID sessionId, List<NormalizedIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            IngredientRiskSummary emptySummary = new IngredientRiskSummary(0, 0, 0, 0, 0, 0, 0, 0, 0);
            return new IngredientRiskAnalysisResult(sessionId, emptySummary, List.of(), Instant.now());
        }

        List<IngredientRiskItem> evaluatedItems = new ArrayList<>();
        Set<String> uniqueCanonicalKeys = new HashSet<>();

        int highAttentionCount = 0;
        int moderateAttentionCount = 0;
        int lowAttentionCount = 0;
        int noConcernCount = 0;
        int unknownCount = 0;
        int additivesCount = 0;
        int uncertainCount = 0;
        int identifiedCount = 0;

        for (NormalizedIngredient ing : ingredients) {
            IngredientRiskItem item = evaluateSingleIngredient(ing);
            evaluatedItems.add(item);

            if (ing.uncertain()) {
                uncertainCount++;
            }

            if (item.normalizedName() != null) {
                identifiedCount++;
            }

            if (item.additiveCode() != null) {
                additivesCount++;
            }

            // Deduplication for summary metrics to avoid double counting repeated ingredients
            String deduplicationKey = item.normalizedName() != null
                    ? item.normalizedName().toUpperCase(Locale.ROOT)
                    : (item.originalIngredient() != null ? item.originalIngredient().toUpperCase(Locale.ROOT) : UUID.randomUUID().toString());

            if (uniqueCanonicalKeys.add(deduplicationKey)) {
                switch (item.riskLevel()) {
                    case HIGH_ATTENTION -> highAttentionCount++;
                    case MODERATE_ATTENTION -> moderateAttentionCount++;
                    case LOW_ATTENTION -> lowAttentionCount++;
                    case NO_CONCERN, NO_SPECIFIC_CONCERN, POSITIVE -> noConcernCount++;
                    case UNKNOWN -> unknownCount++;
                }
            }
        }

        IngredientRiskSummary summary = new IngredientRiskSummary(
                evaluatedItems.size(),
                identifiedCount,
                uncertainCount,
                additivesCount,
                highAttentionCount,
                moderateAttentionCount,
                lowAttentionCount,
                noConcernCount,
                unknownCount
        );

        log.info("Evaluated {} ingredients for session {} (High: {}, Mod: {}, Low: {}, NoConcern: {}, Unknown: {})",
                evaluatedItems.size(), sessionId, highAttentionCount, moderateAttentionCount, lowAttentionCount, noConcernCount, unknownCount);

        return new IngredientRiskAnalysisResult(sessionId, summary, evaluatedItems, Instant.now());
    }

    private IngredientRiskItem evaluateSingleIngredient(NormalizedIngredient ing) {
        String originalText = ing.rawText() != null && !ing.rawText().isBlank()
                ? ing.rawText().trim()
                : (ing.name() != null ? ing.name().trim() : "Unknown");

        // 1. Blank or invalid name check
        String name = ing.name() != null ? ing.name().trim() : "";
        if (name.isBlank() || name.equalsIgnoreCase("unknown")) {
            return new IngredientRiskItem(
                    originalText,
                    null,
                    IngredientRiskLevel.UNKNOWN,
                    List.of("Ingredient identity could not be verified from available label evidence."),
                    List.of(),
                    EvidenceStatus.INSUFFICIENT,
                    null,
                    null,
                    RegulatoryStatus.UNKNOWN,
                    "This ingredient could not be verified against the configured knowledge base."
            );
        }

        // 2. If explicitly marked uncertain by OCR / Normalization, respect uncertainty
        if (ing.uncertain()) {
            return new IngredientRiskItem(
                    originalText,
                    null,
                    IngredientRiskLevel.UNKNOWN,
                    List.of("Ingredient could not be reliably identified from available label OCR. Please verify the original package."),
                    List.of(),
                    EvidenceStatus.INSUFFICIENT,
                    null,
                    null,
                    RegulatoryStatus.UNKNOWN,
                    "Ingredient could not be reliably identified from available label OCR."
            );
        }

        // 3. Check for Additive rule (via code, synonym, or name)
        Optional<AdditiveRuleDefinition> additiveMatch = Optional.empty();
        if (ing.additiveCode() != null && !ing.additiveCode().isBlank()) {
            additiveMatch = ruleRepository.findAdditiveByCodeOrSynonym(ing.additiveCode());
        }
        if (additiveMatch.isEmpty()) {
            additiveMatch = ruleRepository.findAdditiveByCodeOrSynonym(name);
        }
        if (additiveMatch.isEmpty() && ing.rawText() != null) {
            additiveMatch = ruleRepository.findAdditiveByCodeOrSynonym(ing.rawText());
        }

        if (additiveMatch.isPresent()) {
            AdditiveRuleDefinition rule = additiveMatch.get();
            return new IngredientRiskItem(
                    originalText,
                    rule.name(),
                    rule.riskLevel(),
                    rule.reasons(),
                    rule.sourceIds(),
                    EvidenceStatus.SUPPORTED,
                    rule.code(),
                    rule.functionalClass(),
                    rule.regulatoryStatus(),
                    rule.jurisdiction(),
                    rule.jurisdictionalStatus()
            );
        }

        // 4. Check for Ingredient rule in verified knowledge base
        Optional<IngredientRuleDefinition> ingredientMatch = ruleRepository.findIngredientRule(name);
        if (ingredientMatch.isEmpty() && ing.rawText() != null) {
            ingredientMatch = ruleRepository.findIngredientRule(ing.rawText());
        }

        if (ingredientMatch.isPresent()) {
            IngredientRuleDefinition rule = ingredientMatch.get();
            return new IngredientRiskItem(
                    originalText,
                    rule.canonicalName(),
                    rule.riskLevel(),
                    rule.reasons(),
                    rule.sourceIds(),
                    EvidenceStatus.SUPPORTED,
                    null,
                    null,
                    rule.regulatoryStatus()
            );
        }

        // 5. Unrecognized / Unverified ingredient: must resolve to UNKNOWN (never automatically safe or permitted)
        return new IngredientRiskItem(
                originalText,
                null,
                IngredientRiskLevel.UNKNOWN,
                List.of("Ingredient could not be verified against the configured knowledge base."),
                List.of(),
                EvidenceStatus.INSUFFICIENT,
                null,
                null,
                RegulatoryStatus.UNKNOWN,
                "This ingredient could not be verified against the configured knowledge base."
        );
    }
}
