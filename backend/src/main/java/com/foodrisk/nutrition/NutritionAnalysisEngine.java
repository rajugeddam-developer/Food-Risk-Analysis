package com.foodrisk.nutrition;

import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedNutrition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic engine evaluating normalized nutritional facts against authoritative WHO and FSSAI reference standards.
 *
 * Core Principles:
 * - High decimal precision via BigDecimal arithmetic.
 * - Strict basis distinction (PER_100G, PER_100ML, PER_SERVING). Per-serving data is never compared to per-100g without explicit servingSizeGrams.
 * - Missing != Zero: Missing nutrients are classified as NOT_DECLARED, never manufactured as zeros.
 * - Conservative, evidence-based positive and attention indicator extraction.
 */
@Component
public class NutritionAnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(NutritionAnalysisEngine.class);

    private final NutritionRuleRepository ruleRepository;

    public NutritionAnalysisEngine(NutritionRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public NutritionAnalysisResult evaluate(UUID sessionId, NormalizedFoodData foodData) {
        if (foodData == null || foodData.nutrition() == null) {
            return buildInsufficientResult(sessionId, foodData != null ? foodData.servingSizeGrams() : null);
        }

        NormalizedNutrition nutrition = foodData.nutrition();
        Double servingSizeGrams = foodData.servingSizeGrams();
        NutritionBasis declaredBasis = resolveDeclaredBasis(nutrition.basis());

        List<NutritionFinding> findings = new ArrayList<>();
        List<String> missingNutrients = new ArrayList<>();
        List<String> attentionIndicators = new ArrayList<>();
        List<String> positiveIndicators = new ArrayList<>();
        Set<String> referenceSources = new HashSet<>();

        // 1. Evaluate individual nutrients
        evaluateNutrient(NutrientType.ENERGY, nutrition.energyKcal(), "kcal", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);
        evaluateNutrient(NutrientType.TOTAL_FAT, nutrition.totalFatG(), "g", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);
        evaluateNutrient(NutrientType.SATURATED_FAT, nutrition.saturatedFatG(), "g", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);
        evaluateNutrient(NutrientType.TRANS_FAT, nutrition.transFatG(), "g", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);
        evaluateNutrient(NutrientType.CARBOHYDRATES, nutrition.carbohydrateG(), "g", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);
        evaluateNutrient(NutrientType.TOTAL_SUGARS, nutrition.totalSugarsG(), "g", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);
        evaluateNutrient(NutrientType.ADDED_SUGARS, nutrition.addedSugarsG(), "g", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);
        evaluateNutrient(NutrientType.PROTEIN, nutrition.proteinG(), "g", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);
        evaluateNutrient(NutrientType.FIBRE, nutrition.fiberG(), "g", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);
        evaluateNutrient(NutrientType.SODIUM, nutrition.sodiumMg(), "mg", declaredBasis, servingSizeGrams, findings, missingNutrients, attentionIndicators, positiveIndicators, referenceSources);

        // 2. Determine overall data completeness
        DataCompleteness completeness = calculateCompleteness(nutrition);

        log.info("Evaluated nutrition facts for session {}: completeness={}, findings={}, attentionFlags={}, positiveFlags={}",
                sessionId, completeness, findings.size(), attentionIndicators.size(), positiveIndicators.size());

        List<String> sortedSources = new ArrayList<>(referenceSources);
        Collections.sort(sortedSources);

        return new NutritionAnalysisResult(
                sessionId,
                completeness,
                declaredBasis,
                servingSizeGrams,
                findings,
                positiveIndicators,
                attentionIndicators,
                missingNutrients,
                sortedSources,
                ruleRepository.getReferenceVersion(),
                Instant.now()
        );
    }

    private void evaluateNutrient(
            NutrientType nutrient,
            Double rawValue,
            String unit,
            NutritionBasis declaredBasis,
            Double servingSizeGrams,
            List<NutritionFinding> findings,
            List<String> missingNutrients,
            List<String> attentionIndicators,
            List<String> positiveIndicators,
            Set<String> referenceSources
    ) {
        // Check for missing vs detected vs zero
        if (rawValue == null) {
            missingNutrients.add(nutrient.name());
            findings.add(new NutritionFinding(
                    nutrient,
                    null,
                    unit,
                    declaredBasis,
                    servingSizeGrams,
                    null,
                    NutritionBasis.UNKNOWN,
                    null,
                    unit,
                    NutritionBasis.PER_100G,
                    ReferenceType.DIETARY_GUIDELINE,
                    NutrientValueState.NOT_DECLARED,
                    ComparisonStatus.INSUFFICIENT_DATA,
                    NutritionSeverity.UNKNOWN,
                    "Nutrient was not declared on the package label.",
                    List.of()
            ));
            return;
        }

        BigDecimal observedValue = BigDecimal.valueOf(rawValue).setScale(2, RoundingMode.HALF_UP);
        NutrientValueState valueState = rawValue == 0.0 ? NutrientValueState.EXPLICIT_ZERO : NutrientValueState.DETECTED_VALUE;

        // Basis Normalization
        BigDecimal normalizedValue = null;
        NutritionBasis normalizedBasis = NutritionBasis.UNKNOWN;
        boolean canNormalize = false;

        if (declaredBasis == NutritionBasis.PER_100G || declaredBasis == NutritionBasis.PER_100ML) {
            normalizedValue = observedValue;
            normalizedBasis = declaredBasis;
            canNormalize = true;
        } else if (declaredBasis == NutritionBasis.PER_SERVING || declaredBasis == NutritionBasis.PER_PACKAGE) {
            if (servingSizeGrams != null && servingSizeGrams > 0) {
                normalizedValue = observedValue
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(servingSizeGrams), 2, RoundingMode.HALF_UP);
                normalizedBasis = NutritionBasis.PER_100G;
                canNormalize = true;
            }
        }

        List<NutritionRuleDefinition> rules = ruleRepository.getRulesForNutrient(nutrient);

        if (rules.isEmpty()) {
            findings.add(new NutritionFinding(
                    nutrient,
                    observedValue,
                    unit,
                    declaredBasis,
                    servingSizeGrams,
                    normalizedValue,
                    normalizedBasis,
                    null,
                    unit,
                    NutritionBasis.PER_100G,
                    ReferenceType.NUTRITION_REFERENCE,
                    valueState,
                    ComparisonStatus.NO_REFERENCE,
                    NutritionSeverity.LOW,
                    "Observed value extracted. No specific reference limit configured.",
                    List.of()
            ));
            return;
        }

        if (!canNormalize) {
            NutritionRuleDefinition sampleRule = rules.get(0);
            referenceSources.add(sampleRule.sourceId());
            findings.add(new NutritionFinding(
                    nutrient,
                    observedValue,
                    unit,
                    declaredBasis,
                    servingSizeGrams,
                    null,
                    NutritionBasis.UNKNOWN,
                    sampleRule.threshold(),
                    sampleRule.unit(),
                    sampleRule.basis(),
                    sampleRule.referenceType(),
                    valueState,
                    ComparisonStatus.INSUFFICIENT_DATA,
                    NutritionSeverity.UNKNOWN,
                    "Serving size in grams was not declared, preventing mathematical normalization to the 100g reference basis.",
                    List.of(sampleRule.sourceId())
            ));
            return;
        }

        // Compare normalized value against configured reference rules
        NutritionRuleDefinition matchedRule = null;
        for (NutritionRuleDefinition rule : rules) {
            referenceSources.add(rule.sourceId());
            if (rule.threshold() != null && normalizedValue.compareTo(rule.threshold()) >= 0) {
                matchedRule = rule;
                break; // Because rules are sorted descending by threshold, first match is highest severity
            }
        }

        if (matchedRule != null) {
            // Threshold exceeded
            if (nutrient == NutrientType.FIBRE || nutrient == NutrientType.PROTEIN) {
                // Beneficial / positive nutrient
                findings.add(new NutritionFinding(
                        nutrient,
                        observedValue,
                        unit,
                        declaredBasis,
                        servingSizeGrams,
                        normalizedValue,
                        normalizedBasis,
                        matchedRule.threshold(),
                        matchedRule.unit(),
                        matchedRule.basis(),
                        matchedRule.referenceType(),
                        valueState,
                        ComparisonStatus.WITHIN_REFERENCE,
                        NutritionSeverity.LOW,
                        matchedRule.notes(),
                        List.of(matchedRule.sourceId())
                ));
                if (nutrient == NutrientType.FIBRE) {
                    positiveIndicators.add("Good source of dietary fibre (declared " + normalizedValue + "g per 100g).");
                } else {
                    positiveIndicators.add("Meaningful dietary protein contribution (declared " + normalizedValue + "g per 100g).");
                }
            } else {
                // Risk nutrient exceeded
                findings.add(new NutritionFinding(
                        nutrient,
                        observedValue,
                        unit,
                        declaredBasis,
                        servingSizeGrams,
                        normalizedValue,
                        normalizedBasis,
                        matchedRule.threshold(),
                        matchedRule.unit(),
                        matchedRule.basis(),
                        matchedRule.referenceType(),
                        valueState,
                        ComparisonStatus.ABOVE_REFERENCE,
                        matchedRule.severity(),
                        matchedRule.notes(),
                        List.of(matchedRule.sourceId())
                ));

                if (matchedRule.severity() == NutritionSeverity.HIGH || matchedRule.severity() == NutritionSeverity.MODERATE) {
                    String indicator = formatAttentionIndicator(nutrient, normalizedValue, matchedRule);
                    attentionIndicators.add(indicator);
                }
            }
        } else {
            // Below all attention thresholds (Compliant / Within Reference)
            NutritionRuleDefinition lowestRule = rules.get(rules.size() - 1);
            findings.add(new NutritionFinding(
                    nutrient,
                    observedValue,
                    unit,
                    declaredBasis,
                    servingSizeGrams,
                    normalizedValue,
                    normalizedBasis,
                    lowestRule.threshold(),
                    lowestRule.unit(),
                    lowestRule.basis(),
                    lowestRule.referenceType(),
                    valueState,
                    ComparisonStatus.WITHIN_REFERENCE,
                    NutritionSeverity.LOW,
                    "Nutrient level is within standard dietary reference guidance.",
                    List.of(lowestRule.sourceId())
            ));

            // Check for positive low-sodium or low-sugar indicators
            if (nutrient == NutrientType.SODIUM && normalizedValue.compareTo(BigDecimal.valueOf(120)) <= 0 && rawValue > 0) {
                positiveIndicators.add("Low sodium content (declared " + normalizedValue + "mg per 100g).");
            } else if (nutrient == NutrientType.TOTAL_SUGARS && normalizedValue.compareTo(BigDecimal.valueOf(5)) <= 0 && rawValue > 0) {
                positiveIndicators.add("Low total sugar content (declared " + normalizedValue + "g per 100g).");
            }
        }
    }

    private String formatAttentionIndicator(NutrientType nutrient, BigDecimal normalizedValue, NutritionRuleDefinition rule) {
        return switch (nutrient) {
            case TOTAL_SUGARS -> "Elevated sugar content (" + normalizedValue + "g/100g) exceeds configured product classification threshold (" + rule.threshold() + "g); dietary context: WHO free sugar guidelines.";
            case ADDED_SUGARS -> "Added sugars (" + normalizedValue + "g/100g) exceed configured product classification threshold (" + rule.threshold() + "g); dietary context: WHO free sugar reduction advice.";
            case SATURATED_FAT -> "High saturated fat (" + normalizedValue + "g/100g) exceeds configured product classification threshold (" + rule.threshold() + "g); dietary context: WHO saturated fat guidance.";
            case TRANS_FAT -> "Declared industrial trans fatty acids (" + normalizedValue + "g/100g) exceed statutory regulatory limit.";
            case SODIUM -> "Elevated sodium (" + normalizedValue + "mg/100g) exceeds configured product classification threshold (" + rule.threshold() + "mg); dietary context: WHO sodium moderation benchmark.";
            default -> nutrient.name() + " (" + normalizedValue + " " + rule.unit() + ") exceeds reference threshold.";
        };
    }

    private NutritionBasis resolveDeclaredBasis(String rawBasis) {
        if (rawBasis == null || rawBasis.isBlank()) return NutritionBasis.UNKNOWN;
        String lower = rawBasis.toLowerCase(Locale.ROOT);
        if (lower.contains("100 ml") || lower.contains("100ml")) return NutritionBasis.PER_100ML;
        if (lower.contains("100 g") || lower.contains("100g")) return NutritionBasis.PER_100G;
        if (lower.contains("serving") || lower.contains("portion")) return NutritionBasis.PER_SERVING;
        if (lower.contains("pack") || lower.contains("container")) return NutritionBasis.PER_PACKAGE;
        return NutritionBasis.UNKNOWN;
    }

    private DataCompleteness calculateCompleteness(NormalizedNutrition n) {
        int corePresent = 0;
        if (n.energyKcal() != null) corePresent++;
        if (n.totalFatG() != null) corePresent++;
        if (n.saturatedFatG() != null) corePresent++;
        if (n.carbohydrateG() != null) corePresent++;
        if (n.totalSugarsG() != null || n.addedSugarsG() != null) corePresent++;
        if (n.proteinG() != null) corePresent++;
        if (n.sodiumMg() != null) corePresent++;

        if (corePresent >= 6) return DataCompleteness.COMPLETE;
        if (corePresent >= 2) return DataCompleteness.PARTIAL;
        return DataCompleteness.INSUFFICIENT;
    }

    private NutritionAnalysisResult buildInsufficientResult(UUID sessionId, Double servingSizeGrams) {
        return new NutritionAnalysisResult(
                sessionId,
                DataCompleteness.INSUFFICIENT,
                NutritionBasis.UNKNOWN,
                servingSizeGrams,
                List.of(),
                List.of(),
                List.of("No nutritional table was detected or extracted from the packaging images."),
                List.of("ALL_CORE_NUTRIENTS"),
                List.of(),
                ruleRepository.getReferenceVersion(),
                Instant.now()
        );
    }
}
