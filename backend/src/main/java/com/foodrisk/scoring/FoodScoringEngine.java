package com.foodrisk.scoring;

import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.ClassificationReasonCode;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.nutrition.ComparisonStatus;
import com.foodrisk.nutrition.DataCompleteness;
import com.foodrisk.nutrition.NutrientType;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.nutrition.NutritionFinding;
import com.foodrisk.nutrition.NutritionSeverity;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.risk.IngredientRiskItem;
import com.foodrisk.risk.IngredientRiskLevel;
import com.foodrisk.risk.RegulatoryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic engine synthesizing M7 (classification), M8 (ingredient risk), and M9 (nutrition standards)
 * into a transparent Food Awareness Score (0–100) and actionable consumer guidance.
 *
 * Core Guarantees:
 * - Deterministic arithmetic from scoring-rules.json.
 * - Anti-double-counting composite overlap rules (prevents penalizing sugar, trans fat, and salt twice).
 * - Immediate priority handling for Pet Food and Non-Food categories (null score, NOT_INTENDED_FOR_HUMAN_CONSUMPTION).
 * - Non-medical, educational awareness language.
 */
@Component
public class FoodScoringEngine {

    private static final Logger log = LoggerFactory.getLogger(FoodScoringEngine.class);

    private final ScoringRuleRepository ruleRepository;
    private final AgeGuidanceEngine ageGuidanceEngine;

    @org.springframework.beans.factory.annotation.Autowired
    public FoodScoringEngine(ScoringRuleRepository ruleRepository, AgeGuidanceEngine ageGuidanceEngine) {
        this.ruleRepository = ruleRepository;
        this.ageGuidanceEngine = ageGuidanceEngine != null ? ageGuidanceEngine : new AgeGuidanceEngine(new JsonAgeGuidanceRepository());
    }

    public FoodScoringEngine(ScoringRuleRepository ruleRepository) {
        this(ruleRepository, new AgeGuidanceEngine(new JsonAgeGuidanceRepository()));
    }

    public FoodRiskAssessment assess(
            UUID sessionId,
            FoodClassificationResult classification,
            IngredientRiskAnalysisResult ingredientRisk,
            NutritionAnalysisResult nutrition
    ) {
        ScoringRuleDefinition rules = ruleRepository.getScoringRules();
        List<String> limitations = new ArrayList<>();
        limitations.add("The Food Awareness Score is an educational evaluation derived from declared packaging evidence and does not constitute medical advice or official WHO/FSSAI regulatory endorsement.");

        // 1. Category Priority Check: Non-food and Pet Food
        if (classification != null && (classification.category() == FoodCategory.PET_FOOD || classification.category() == FoodCategory.NON_FOOD)) {
            return buildNonHumanAssessment(sessionId, classification, ingredientRisk, nutrition, rules, limitations);
        }

        // 2. Insufficient / Unrated Data Check
        boolean isNutritionInsufficient = nutrition == null || nutrition.dataCompleteness() == DataCompleteness.INSUFFICIENT;
        boolean isIngredientInsufficient = ingredientRisk == null || ingredientRisk.items().isEmpty();

        if (isNutritionInsufficient && isIngredientInsufficient) {
            return buildInsufficientAssessment(sessionId, classification, ingredientRisk, nutrition, rules, limitations);
        }

        // Scientific honesty check: If nutrition table is missing/insufficient and no severe additive hazard is identified,
        // product is UNRATED (score = null, status = INSUFFICIENT_DATA). It is deceptive to assign baseline 70 (GOOD_CHOICE).
        boolean hasSevereHazard = hasSevereIngredientHazard(ingredientRisk);
        if (isNutritionInsufficient && !hasSevereHazard) {
            return buildUnratedAssessment(sessionId, classification, ingredientRisk, nutrition, rules, limitations);
        }

        // 3. Deterministic Scoring Synthesis
        List<ScoreImpact> breakdown = new ArrayList<>();
        List<String> keyConcerns = new ArrayList<>();
        List<String> positiveIndicators = new ArrayList<>();
        List<String> awarenessGuidance = new ArrayList<>();
        Set<String> sources = new HashSet<>();
        sources.add("FSSAI");
        sources.add("WHO");

        int baseline = rules.baselineScore();
        breakdown.add(new ScoreImpact("BASELINE", baseline, "Starting baseline score for packaged food product.", "SYSTEM"));

        // Sets tracking items handled by composite overlap to prevent double counting
        Set<String> handledIngredients = new HashSet<>();
        Set<NutrientType> handledNutrients = new HashSet<>();

        // 4. Composite Overlap Rules (Anti-Double-Counting)
        applyCompositeOverlapRules(rules, ingredientRisk, nutrition, breakdown, keyConcerns, handledIngredients, handledNutrients);

        // 5. Additive & Non-overlapping Ingredient Deductions
        applyIngredientDeductions(rules, ingredientRisk, breakdown, keyConcerns, handledIngredients, sources);

        // 6. Non-overlapping Nutrition Deductions
        applyNutritionDeductions(rules, nutrition, breakdown, keyConcerns, handledNutrients, sources);

        // 7. Positive Bonuses
        int totalBonuses = applyPositiveBonuses(rules, nutrition, breakdown, positiveIndicators);

        // 8. Total Score Calculation
        int totalDeductions = 0;
        for (ScoreImpact impact : breakdown) {
            if (impact.impact() < 0) {
                totalDeductions += impact.impact();
            }
        }

        // Cap negative deductions to maxTotalPenalty (e.g. -100)
        totalDeductions = Math.max(-rules.maxTotalPenalty(), totalDeductions);

        int calculatedScore = baseline + totalDeductions + totalBonuses;
        int finalScore = Math.max(rules.minScore(), Math.min(rules.maxScore(), calculatedScore));

        // Status Determination
        OverallFoodStatus overallStatus;
        if (finalScore >= rules.statusThresholds().goodChoiceMin()) {
            overallStatus = OverallFoodStatus.GOOD_CHOICE;
        } else if (finalScore >= rules.statusThresholds().needsAttentionMin()) {
            overallStatus = OverallFoodStatus.NEEDS_ATTENTION;
        } else {
            overallStatus = OverallFoodStatus.HIGH_ATTENTION;
        }

        // Reliability Determination
        AssessmentReliability reliability = calculateReliability(classification, nutrition);

        // Guidance & Population Breakdown
        generateGuidance(overallStatus, keyConcerns, positiveIndicators, awarenessGuidance);
        PopulationGuidance populationGuidance = generatePopulationGuidance(overallStatus, keyConcerns);

        // Add nutrition reference sources
        if (nutrition != null && nutrition.referenceSources() != null) {
            sources.addAll(nutrition.referenceSources());
        }

        List<String> sortedSources = new ArrayList<>(sources);
        java.util.Collections.sort(sortedSources);

        ScoreEligibility eligibility;
        if (isNutritionInsufficient && hasSevereHazard) {
            eligibility = ScoreEligibility.PARTIALLY_RATED;
            limitations.add("Nutrition facts are missing; score is partially evaluated solely from detected ingredient/additive hazards.");
        } else if (nutrition != null && nutrition.dataCompleteness() == DataCompleteness.PARTIAL) {
            eligibility = ScoreEligibility.PARTIALLY_RATED;
        } else {
            eligibility = ScoreEligibility.RATED;
        }

        log.info("Assessed session {}: finalScore={}, status={}, reliability={}, eligibility={}",
                sessionId, finalScore, overallStatus, reliability, eligibility);

        String categoryReason = classification != null
                ? (classification.reason() != null ? classification.reason() : classification.reasonCode().name())
                : "No category classification available.";

        List<AgeGroupAwareness> ageGroupAwareness = ageGuidanceEngine.evaluateDemographics(ingredientRisk, nutrition);
        List<IngredientRiskItem> evaluatedItems = ingredientRisk != null && ingredientRisk.items() != null
                ? ingredientRisk.items()
                : List.of();

        return new FoodRiskAssessment(
                sessionId,
                classification != null ? classification.category() : FoodCategory.UNKNOWN,
                categoryReason,
                HumanConsumptionStatus.HUMAN_FOOD,
                finalScore,
                overallStatus,
                eligibility,
                reliability,
                nutrition != null ? nutrition.dataCompleteness() : DataCompleteness.INSUFFICIENT,
                classification != null ? classification.certainty() : ClassificationCertainty.UNKNOWN,
                breakdown,
                ingredientRisk != null ? ingredientRisk.summary() : null,
                evaluatedItems,
                nutrition,
                keyConcerns,
                positiveIndicators,
                awarenessGuidance,
                populationGuidance,
                ageGroupAwareness,
                sortedSources,
                limitations,
                nutrition != null ? nutrition.nutritionReferenceVersion() : "2026.09",
                rules.scoringRuleVersion(),
                Instant.now()
        );
    }

    private void applyCompositeOverlapRules(
            ScoringRuleDefinition rules,
            IngredientRiskAnalysisResult ingredientRisk,
            NutritionAnalysisResult nutrition,
            List<ScoreImpact> breakdown,
            List<String> keyConcerns,
            Set<String> handledIngredients,
            Set<NutrientType> handledNutrients
    ) {
        // Sugar Overlap
        boolean hasSugarIngredient = hasFlaggedIngredientMatching(ingredientRisk, "sugar", "syrup", "dextrose", "fructose", "sucrose", "glucose", "maltodextrin");
        boolean hasHighSugarNutrition = hasNutrientAboveReference(nutrition, NutrientType.TOTAL_SUGARS, NutrientType.ADDED_SUGARS);

        if (hasSugarIngredient && hasHighSugarNutrition) {
            ScoringRuleDefinition.CompositeOverlapRule rule = rules.compositeOverlapRules().get("sugarOverlap");
            int penalty = rule != null ? rule.penalty() : -20;
            String reason = rule != null ? rule.reason() : "High sugar presence identified across both ingredient formulation and nutritional sugar metrics.";
            breakdown.add(new ScoreImpact("SUGAR_OVERLAP", penalty, reason, "FSSAI/WHO"));
            keyConcerns.add("Elevated sugar corroborated by both ingredients and nutrition facts.");
            markMatchingIngredientsHandled(ingredientRisk, handledIngredients, "sugar", "syrup", "dextrose", "fructose", "sucrose", "glucose", "maltodextrin");
            handledNutrients.add(NutrientType.TOTAL_SUGARS);
            handledNutrients.add(NutrientType.ADDED_SUGARS);
        }

        // Trans Fat Overlap
        boolean hasTransFatIngredient = hasFlaggedIngredientMatching(ingredientRisk, "hydrogenated", "vanaspati", "shortening", "margarine", "trans fat");
        boolean hasTransFatNutrition = hasNutrientAboveReference(nutrition, NutrientType.TRANS_FAT) || hasDetectedTransFat(nutrition);

        if (hasTransFatIngredient && hasTransFatNutrition) {
            ScoringRuleDefinition.CompositeOverlapRule rule = rules.compositeOverlapRules().get("transFatOverlap");
            int penalty = rule != null ? rule.penalty() : -25;
            String reason = rule != null ? rule.reason() : "Industrial hydrogenated lipid ingredient formulation corroborated by declared trans fatty acid profile.";
            breakdown.add(new ScoreImpact("TRANS_FAT_OVERLAP", penalty, reason, "FSSAI"));
            keyConcerns.add("Industrial trans fatty acids corroborated by hydrogenated lipid ingredients.");
            markMatchingIngredientsHandled(ingredientRisk, handledIngredients, "hydrogenated", "vanaspati", "shortening", "margarine", "trans fat");
            handledNutrients.add(NutrientType.TRANS_FAT);
        }

        // Sodium Overlap
        boolean hasSodiumIngredient = hasFlaggedIngredientMatching(ingredientRisk, "salt", "sodium");
        boolean hasSodiumNutrition = hasNutrientAboveReference(nutrition, NutrientType.SODIUM);

        if (hasSodiumIngredient && hasSodiumNutrition) {
            ScoringRuleDefinition.CompositeOverlapRule rule = rules.compositeOverlapRules().get("sodiumOverlap");
            int penalty = rule != null ? rule.penalty() : -20;
            String reason = rule != null ? rule.reason() : "Added salt ingredient formulation corroborated by elevated quantitative sodium content.";
            breakdown.add(new ScoreImpact("SODIUM_OVERLAP", penalty, reason, "WHO"));
            keyConcerns.add("Elevated sodium corroborated by added salt ingredients and nutrition facts.");
            markMatchingIngredientsHandled(ingredientRisk, handledIngredients, "salt", "sodium");
            handledNutrients.add(NutrientType.SODIUM);
        }
    }

    private void applyIngredientDeductions(
            ScoringRuleDefinition rules,
            IngredientRiskAnalysisResult ingredientRisk,
            List<ScoreImpact> breakdown,
            List<String> keyConcerns,
            Set<String> handledIngredients,
            Set<String> sources
    ) {
        if (ingredientRisk == null || ingredientRisk.items() == null) return;

        int lowAttentionCount = 0;

        for (IngredientRiskItem item : ingredientRisk.items()) {
            String itemName = item.normalizedName() != null ? item.normalizedName() : item.originalIngredient();
            if (handledIngredients.contains(itemName)) continue;

            if (item.regulatoryStatus() == RegulatoryStatus.BANNED || item.regulatoryStatus() == RegulatoryStatus.PROHIBITED) {
                int penalty = rules.additiveDeductions().banned();
                breakdown.add(new ScoreImpact("BANNED_ADDITIVE", penalty, itemName + " is prohibited in India according to FSSAI.", "FSSAI"));
                keyConcerns.add("Contains additive prohibited in India (FSSAI): " + itemName);
                sources.add("FSSAI");
            } else if (item.riskLevel() == IngredientRiskLevel.HIGH_ATTENTION) {
                int penalty = rules.additiveDeductions().highAttention();
                breakdown.add(new ScoreImpact("HIGH_ATTENTION_ADDITIVE", penalty, itemName + " requires significant consumer attention.", "FSSAI/WHO"));
                keyConcerns.add("High attention ingredient: " + itemName);
            } else if (item.riskLevel() == IngredientRiskLevel.MODERATE_ATTENTION) {
                int penalty = rules.additiveDeductions().moderateAttention();
                breakdown.add(new ScoreImpact("MODERATE_ATTENTION_ADDITIVE", penalty, itemName + " has moderate consumption guidelines.", "FSSAI"));
                keyConcerns.add("Moderate attention ingredient: " + itemName);
            } else if (item.riskLevel() == IngredientRiskLevel.LOW_ATTENTION) {
                if (lowAttentionCount < 2) {
                    int penalty = rules.additiveDeductions().lowAttention();
                    breakdown.add(new ScoreImpact("LOW_ATTENTION_ADDITIVE", penalty, itemName + " permitted additive with usage considerations.", "FSSAI"));
                    lowAttentionCount++;
                }
            }
        }
    }

    private void applyNutritionDeductions(
            ScoringRuleDefinition rules,
            NutritionAnalysisResult nutrition,
            List<ScoreImpact> breakdown,
            List<String> keyConcerns,
            Set<NutrientType> handledNutrients,
            Set<String> sources
    ) {
        if (nutrition == null || nutrition.findings() == null) return;

        for (NutritionFinding finding : nutrition.findings()) {
            if (handledNutrients.contains(finding.nutrient())) continue;

            if (finding.nutrient() == NutrientType.TOTAL_SUGARS || finding.nutrient() == NutrientType.ADDED_SUGARS) {
                if (finding.status() == ComparisonStatus.ABOVE_REFERENCE) {
                    int penalty = finding.severity() == NutritionSeverity.HIGH
                            ? rules.nutritionDeductions().highSugar()
                            : rules.nutritionDeductions().moderateSugar();
                    breakdown.add(new ScoreImpact("HIGH_SUGAR_NUTRITION", penalty, finding.reason(), "WHO"));
                    keyConcerns.add("Sugar level (" + finding.normalizedValue() + "g/100g) exceeds reference threshold.");
                    sources.add("WHO");
                    handledNutrients.add(NutrientType.TOTAL_SUGARS);
                    handledNutrients.add(NutrientType.ADDED_SUGARS);
                }
            } else if (finding.nutrient() == NutrientType.TRANS_FAT) {
                if (finding.status() == ComparisonStatus.ABOVE_REFERENCE || (finding.observedValue() != null && finding.observedValue().doubleValue() > 0)) {
                    int penalty = rules.nutritionDeductions().transFatDetected();
                    breakdown.add(new ScoreImpact("TRANS_FAT_DETECTED", penalty, finding.reason(), "FSSAI"));
                    keyConcerns.add("Trans fatty acids detected (" + finding.observedValue() + "g/100g).");
                    sources.add("FSSAI");
                    handledNutrients.add(NutrientType.TRANS_FAT);
                }
            } else if (finding.nutrient() == NutrientType.SODIUM) {
                if (finding.status() == ComparisonStatus.ABOVE_REFERENCE) {
                    int penalty = finding.severity() == NutritionSeverity.HIGH
                            ? rules.nutritionDeductions().highSodium()
                            : rules.nutritionDeductions().moderateSodium();
                    breakdown.add(new ScoreImpact("HIGH_SODIUM_NUTRITION", penalty, finding.reason(), "WHO"));
                    keyConcerns.add("Sodium level (" + finding.normalizedValue() + "mg/100g) exceeds reference threshold.");
                    sources.add("WHO");
                    handledNutrients.add(NutrientType.SODIUM);
                }
            } else if (finding.nutrient() == NutrientType.SATURATED_FAT) {
                if (finding.status() == ComparisonStatus.ABOVE_REFERENCE) {
                    int penalty = rules.nutritionDeductions().highSaturatedFat();
                    breakdown.add(new ScoreImpact("HIGH_SATURATED_FAT", penalty, finding.reason(), "WHO"));
                    keyConcerns.add("Saturated fat level exceeds dietary reference threshold.");
                    sources.add("WHO");
                    handledNutrients.add(NutrientType.SATURATED_FAT);
                }
            }
        }
    }

    private int applyPositiveBonuses(
            ScoringRuleDefinition rules,
            NutritionAnalysisResult nutrition,
            List<ScoreImpact> breakdown,
            List<String> positiveIndicators
    ) {
        if (nutrition == null || nutrition.positiveIndicators() == null) return 0;

        int totalBonus = 0;
        for (String indicator : nutrition.positiveIndicators()) {
            if (indicator.contains("dietary fibre")) {
                int bonus = rules.positiveBonuses().sourceOfFibre();
                breakdown.add(new ScoreImpact("FIBRE_BONUS", bonus, "Good source of dietary fibre.", "CODEX"));
                positiveIndicators.add(indicator);
                totalBonus += bonus;
            } else if (indicator.contains("dietary protein")) {
                int bonus = rules.positiveBonuses().sourceOfProtein();
                breakdown.add(new ScoreImpact("PROTEIN_BONUS", bonus, "Meaningful dietary protein contribution.", "CODEX"));
                positiveIndicators.add(indicator);
                totalBonus += bonus;
            } else if (indicator.contains("Low sodium")) {
                int bonus = rules.positiveBonuses().lowerSodium();
                breakdown.add(new ScoreImpact("LOW_SODIUM_BONUS", bonus, "Strictly complies with low-sodium benchmark.", "WHO"));
                positiveIndicators.add(indicator);
                totalBonus += bonus;
            } else if (indicator.contains("Low total sugar")) {
                int bonus = rules.positiveBonuses().lowerSugar();
                breakdown.add(new ScoreImpact("LOW_SUGAR_BONUS", bonus, "Low total sugar content.", "WHO"));
                positiveIndicators.add(indicator);
                totalBonus += bonus;
            }
        }

        return Math.min(rules.maxTotalBonus(), totalBonus);
    }

    private void generateGuidance(
            OverallFoodStatus status,
            List<String> keyConcerns,
            List<String> positiveIndicators,
            List<String> awarenessGuidance
    ) {
        switch (status) {
            case GOOD_CHOICE -> {
                awarenessGuidance.add("Suitable for regular dietary rotation in balanced portions.");
                if (!positiveIndicators.isEmpty()) {
                    awarenessGuidance.add("Nutrient profile provides positive contributions (e.g. fibre or low sodium).");
                }
            }
            case NEEDS_ATTENTION -> {
                awarenessGuidance.add("Moderation recommended. Consider portion size and consumption frequency.");
                if (!keyConcerns.isEmpty()) {
                    awarenessGuidance.add("Primary factor to watch: " + keyConcerns.get(0));
                }
            }
            case HIGH_ATTENTION -> {
                awarenessGuidance.add("Frequent intake is not advised under standard dietary guidance.");
                awarenessGuidance.add("Contains elevated risk nutrients or critical additives warranting consumer awareness.");
            }
            default -> awarenessGuidance.add("Check package label facts carefully before consuming.");
        }
    }

    private PopulationGuidance generatePopulationGuidance(OverallFoodStatus status, List<String> keyConcerns) {
        String concernsNote = keyConcerns.isEmpty() ? "No critical flags identified." : String.join("; ", keyConcerns);
        return switch (status) {
            case GOOD_CHOICE -> new PopulationGuidance(
                    "Can be incorporated into a standard balanced dietary pattern.",
                    "Suitable for growing children within standard balanced meal portions.",
                    "Check specific nutritional facts if managing specific metabolic or dietary conditions."
            );
            case NEEDS_ATTENTION -> new PopulationGuidance(
                    "Balanced portion sizing recommended. " + concernsNote,
                    "Limit portion frequency for children; promote whole-food alternatives.",
                    "Individuals monitoring sodium, sugar, or saturated fats should inspect nutrient table specifics."
            );
            case HIGH_ATTENTION -> new PopulationGuidance(
                    "High awareness advised. Avoid frequent consumption. " + concernsNote,
                    "Not recommended for regular consumption by children due to high sugar, trans fat, sodium, or additive load.",
                    "Consult healthcare provider or dietitian if managing hypertension, diabetes, or cardiovascular concerns."
            );
            default -> new PopulationGuidance(
                    "Evaluate against personal dietary goals.",
                    "Follow age-appropriate dietary guidance.",
                    "Consult qualified professionals for specific dietary requirements."
            );
        };
    }

    private AssessmentReliability calculateReliability(
            FoodClassificationResult classification,
            NutritionAnalysisResult nutrition
    ) {
        if (nutrition == null) return AssessmentReliability.LOW;
        if (nutrition.dataCompleteness() == DataCompleteness.COMPLETE &&
                (classification == null || classification.certainty() == ClassificationCertainty.HIGH)) {
            return AssessmentReliability.HIGH;
        }
        if (nutrition.dataCompleteness() == DataCompleteness.PARTIAL) {
            return AssessmentReliability.MEDIUM;
        }
        return AssessmentReliability.LOW;
    }

    private FoodRiskAssessment buildNonHumanAssessment(
            UUID sessionId,
            FoodClassificationResult classification,
            IngredientRiskAnalysisResult ingredientRisk,
            NutritionAnalysisResult nutrition,
            ScoringRuleDefinition rules,
            List<String> limitations
    ) {
        String entityType = classification.category() == FoodCategory.PET_FOOD ? "pet food / animal feed" : "non-food household product";
        String reason = classification.reason() != null ? classification.reason() : classification.reasonCode().name();
        List<IngredientRiskItem> evaluatedItems = ingredientRisk != null && ingredientRisk.items() != null
                ? ingredientRisk.items()
                : List.of();
        return new FoodRiskAssessment(
                sessionId,
                classification.category(),
                reason,
                HumanConsumptionStatus.NOT_INTENDED_FOR_HUMAN_CONSUMPTION,
                null,
                OverallFoodStatus.NOT_INTENDED_FOR_HUMAN_CONSUMPTION,
                ScoreEligibility.UNRATED,
                classification.certainty() == ClassificationCertainty.HIGH ? AssessmentReliability.HIGH : AssessmentReliability.MEDIUM,
                nutrition != null ? nutrition.dataCompleteness() : DataCompleteness.INSUFFICIENT,
                classification.certainty(),
                List.of(new ScoreImpact("PRODUCT_INTENT", 0, "Product classified as " + entityType + "; human dietary scoring bypassed.", "FSSAI")),
                ingredientRisk != null ? ingredientRisk.summary() : null,
                evaluatedItems,
                nutrition,
                List.of("Product formulated for " + entityType + "; strictly not intended for human consumption."),
                List.of(),
                List.of("DO NOT CONSUME. This item is not manufactured or certified for human dietary intake."),
                new PopulationGuidance("Not intended for human consumption.", "Keep securely out of reach of children.", "Not applicable."),
                List.of(),
                List.of("FSSAI"),
                limitations,
                nutrition != null ? nutrition.nutritionReferenceVersion() : "2026.09",
                rules.scoringRuleVersion(),
                Instant.now()
        );
    }

    private FoodRiskAssessment buildInsufficientAssessment(
            UUID sessionId,
            FoodClassificationResult classification,
            IngredientRiskAnalysisResult ingredientRisk,
            NutritionAnalysisResult nutrition,
            ScoringRuleDefinition rules,
            List<String> limitations
    ) {
        String reason = classification != null
                ? (classification.reason() != null ? classification.reason() : classification.reasonCode().name())
                : "No category classification available.";
        List<IngredientRiskItem> evaluatedItems = ingredientRisk != null && ingredientRisk.items() != null
                ? ingredientRisk.items()
                : List.of();
        return new FoodRiskAssessment(
                sessionId,
                classification != null ? classification.category() : FoodCategory.UNKNOWN,
                reason,
                HumanConsumptionStatus.UNKNOWN,
                null,
                OverallFoodStatus.INSUFFICIENT_DATA,
                ScoreEligibility.UNRATED,
                AssessmentReliability.LOW,
                DataCompleteness.INSUFFICIENT,
                classification != null ? classification.certainty() : ClassificationCertainty.UNKNOWN,
                List.of(new ScoreImpact("INSUFFICIENT_DATA", 0, "Insufficient label evidence extracted to calculate score.", "SYSTEM")),
                ingredientRisk != null ? ingredientRisk.summary() : null,
                evaluatedItems,
                nutrition,
                List.of("Packaging text did not contain sufficient ingredient or nutrition data."),
                List.of(),
                List.of("Please capture clearer, higher-resolution images of both the ingredient list and nutrition table."),
                new PopulationGuidance("Unable to evaluate.", "Unable to evaluate.", "Unable to evaluate."),
                List.of(),
                List.of("FSSAI", "WHO"),
                limitations,
                nutrition != null ? nutrition.nutritionReferenceVersion() : "2026.09",
                rules.scoringRuleVersion(),
                Instant.now()
        );
    }

    private FoodRiskAssessment buildUnratedAssessment(
            UUID sessionId,
            FoodClassificationResult classification,
            IngredientRiskAnalysisResult ingredientRisk,
            NutritionAnalysisResult nutrition,
            ScoringRuleDefinition rules,
            List<String> limitations
    ) {
        String reason = classification != null
                ? (classification.reason() != null ? classification.reason() : classification.reasonCode().name())
                : "Product classification unavailable.";
        List<IngredientRiskItem> evaluatedItems = ingredientRisk != null && ingredientRisk.items() != null
                ? ingredientRisk.items()
                : List.of();

        List<String> unratedLimitations = new ArrayList<>(limitations);
        unratedLimitations.add("Nutrition facts undeclared or unreadable. An overall Food Awareness Score cannot be determined without nutritional data (sugars, sodium, and fats).");

        List<ScoreImpact> breakdown = List.of(
                new ScoreImpact("UNRATED_MISSING_NUTRITION", 0, "Overall score unrated due to missing nutrition facts. Individual ingredients evaluated below.", "SYSTEM")
        );

        List<String> concerns = List.of("Nutrition facts undeclared on packaging. Overall health score withheld to avoid false assurance.");
        List<String> guidance = List.of("Inspect individual ingredient findings below. To obtain an overall score, provide packaging images showing the nutrition facts table.");

        List<AgeGroupAwareness> ageGroupAwareness = ageGuidanceEngine.evaluateDemographics(ingredientRisk, nutrition);

        return new FoodRiskAssessment(
                sessionId,
                classification != null ? classification.category() : FoodCategory.HUMAN_FOOD,
                reason,
                HumanConsumptionStatus.HUMAN_FOOD,
                null,
                OverallFoodStatus.INSUFFICIENT_DATA,
                ScoreEligibility.UNRATED,
                AssessmentReliability.LOW,
                DataCompleteness.INSUFFICIENT,
                classification != null ? classification.certainty() : ClassificationCertainty.UNKNOWN,
                breakdown,
                ingredientRisk != null ? ingredientRisk.summary() : null,
                evaluatedItems,
                nutrition,
                concerns,
                List.of(),
                guidance,
                new PopulationGuidance(
                        "Overall score unrated. Verify sugar, salt, and fat levels from product packaging.",
                        "Inspect package for hidden sugars or allergens before serving to children.",
                        "Individuals monitoring specific nutrients must inspect the manufacturer's nutrition table directly."
                ),
                ageGroupAwareness,
                List.of("FSSAI", "WHO"),
                unratedLimitations,
                nutrition != null ? nutrition.nutritionReferenceVersion() : "2026.09",
                rules.scoringRuleVersion(),
                Instant.now()
        );
    }

    private boolean hasSevereIngredientHazard(IngredientRiskAnalysisResult ingredientRisk) {
        if (ingredientRisk == null || ingredientRisk.items() == null) return false;
        for (IngredientRiskItem item : ingredientRisk.items()) {
            if (item.regulatoryStatus() == RegulatoryStatus.BANNED || item.regulatoryStatus() == RegulatoryStatus.PROHIBITED) {
                return true;
            }
            if (item.riskLevel() == IngredientRiskLevel.HIGH_ATTENTION) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFlaggedIngredientMatching(IngredientRiskAnalysisResult result, String... keywords) {
        if (result == null || result.items() == null) return false;
        for (IngredientRiskItem item : result.items()) {
            if (item.riskLevel() == IngredientRiskLevel.NO_CONCERN
                    || item.riskLevel() == IngredientRiskLevel.NO_SPECIFIC_CONCERN
                    || item.riskLevel() == IngredientRiskLevel.POSITIVE
                    || item.riskLevel() == IngredientRiskLevel.UNKNOWN) continue;
            String itemName = item.normalizedName() != null ? item.normalizedName() : item.originalIngredient();
            String lower = itemName.toLowerCase(Locale.ROOT);
            for (String kw : keywords) {
                if (lower.contains(kw)) return true;
            }
        }
        return false;
    }

    private void markMatchingIngredientsHandled(IngredientRiskAnalysisResult result, Set<String> handled, String... keywords) {
        if (result == null || result.items() == null) return;
        for (IngredientRiskItem item : result.items()) {
            String itemName = item.normalizedName() != null ? item.normalizedName() : item.originalIngredient();
            String lower = itemName.toLowerCase(Locale.ROOT);
            for (String kw : keywords) {
                if (lower.contains(kw)) {
                    handled.add(itemName);
                    break;
                }
            }
        }
    }

    private boolean hasNutrientAboveReference(NutritionAnalysisResult nutrition, NutrientType... nutrients) {
        if (nutrition == null || nutrition.findings() == null) return false;
        for (NutritionFinding finding : nutrition.findings()) {
            for (NutrientType target : nutrients) {
                if (finding.nutrient() == target && finding.status() == ComparisonStatus.ABOVE_REFERENCE) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasDetectedTransFat(NutritionAnalysisResult nutrition) {
        if (nutrition == null || nutrition.findings() == null) return false;
        for (NutritionFinding finding : nutrition.findings()) {
            if (finding.nutrient() == NutrientType.TRANS_FAT) {
                if (finding.observedValue() != null && finding.observedValue().doubleValue() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
