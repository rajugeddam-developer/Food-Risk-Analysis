package com.foodrisk.scoring;

import java.util.Map;

/**
 * Configuration model representing scoring constants, thresholds, and composite overlap rules from scoring-rules.json.
 */
public record ScoringRuleDefinition(
        String scoringRuleVersion,
        int baselineScore,
        int minScore,
        int maxScore,
        int maxTotalPenalty,
        int maxTotalBonus,
        StatusThresholds statusThresholds,
        AdditiveDeductions additiveDeductions,
        NutritionDeductions nutritionDeductions,
        Map<String, CompositeOverlapRule> compositeOverlapRules,
        PositiveBonuses positiveBonuses
) {
    public record StatusThresholds(int goodChoiceMin, int needsAttentionMin) {}
    public record AdditiveDeductions(int banned, int highAttention, int moderateAttention, int lowAttention) {}
    public record NutritionDeductions(int highSugar, int moderateSugar, int highSodium, int moderateSodium, int highSaturatedFat, int transFatDetected) {}
    public record CompositeOverlapRule(int penalty, String reason) {}
    public record PositiveBonuses(int sourceOfFibre, int sourceOfProtein, int lowerSodium, int lowerSugar) {}
}
