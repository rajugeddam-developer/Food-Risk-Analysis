package com.foodrisk.scoring;

import com.foodrisk.classification.ClassificationCertainty;
import com.foodrisk.classification.FoodCategory;
import com.foodrisk.nutrition.DataCompleteness;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.risk.IngredientRiskItem;
import com.foodrisk.risk.IngredientRiskSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Synthesized Food Awareness Assessment produced by Milestone M10.
 *
 * Integrates M7 product category, M8 ingredient risks, and M9 nutrition findings into a
 * deterministic, explainable Food Awareness Score (0–100), demographic awareness, and real evaluated ingredient items.
 */
public record FoodRiskAssessment(
        UUID sessionId,
        FoodCategory productCategory,
        String productCategoryReason,
        HumanConsumptionStatus humanConsumptionStatus,
        Integer overallScore,
        OverallFoodStatus overallStatus,
        ScoreEligibility scoreEligibility,
        AssessmentReliability assessmentReliability,
        DataCompleteness nutritionDataCompleteness,
        ClassificationCertainty classificationReliability,
        List<ScoreImpact> scoreBreakdown,
        IngredientRiskSummary ingredientSummary,
        List<IngredientRiskItem> items,
        NutritionAnalysisResult nutritionSummary,
        List<String> keyConcerns,
        List<String> positiveIndicators,
        List<String> awarenessGuidance,
        PopulationGuidance populationGuidance,
        List<AgeGroupAwareness> ageGroupAwareness,
        List<String> sources,
        List<String> limitations,
        String nutritionReferenceVersion,
        String scoringRuleVersion,
        Instant assessedAt
) {
    public FoodRiskAssessment(
            UUID sessionId,
            FoodCategory productCategory,
            String productCategoryReason,
            HumanConsumptionStatus humanConsumptionStatus,
            Integer overallScore,
            OverallFoodStatus overallStatus,
            AssessmentReliability assessmentReliability,
            DataCompleteness nutritionDataCompleteness,
            ClassificationCertainty classificationReliability,
            List<ScoreImpact> scoreBreakdown,
            IngredientRiskSummary ingredientSummary,
            List<IngredientRiskItem> items,
            NutritionAnalysisResult nutritionSummary,
            List<String> keyConcerns,
            List<String> positiveIndicators,
            List<String> awarenessGuidance,
            PopulationGuidance populationGuidance,
            List<AgeGroupAwareness> ageGroupAwareness,
            List<String> sources,
            List<String> limitations,
            String nutritionReferenceVersion,
            String scoringRuleVersion,
            Instant assessedAt
    ) {
        this(sessionId, productCategory, productCategoryReason, humanConsumptionStatus, overallScore, overallStatus,
                overallScore != null ? ScoreEligibility.RATED : ScoreEligibility.UNRATED,
                assessmentReliability, nutritionDataCompleteness, classificationReliability, scoreBreakdown,
                ingredientSummary, items, nutritionSummary, keyConcerns, positiveIndicators, awarenessGuidance,
                populationGuidance, ageGroupAwareness, sources, limitations, nutritionReferenceVersion, scoringRuleVersion, assessedAt);
    }

    public FoodRiskAssessment(
            UUID sessionId,
            FoodCategory productCategory,
            String productCategoryReason,
            HumanConsumptionStatus humanConsumptionStatus,
            Integer overallScore,
            OverallFoodStatus overallStatus,
            AssessmentReliability assessmentReliability,
            DataCompleteness nutritionDataCompleteness,
            ClassificationCertainty classificationReliability,
            List<ScoreImpact> scoreBreakdown,
            IngredientRiskSummary ingredientSummary,
            NutritionAnalysisResult nutritionSummary,
            List<String> keyConcerns,
            List<String> positiveIndicators,
            List<String> awarenessGuidance,
            PopulationGuidance populationGuidance,
            List<String> sources,
            List<String> limitations,
            String nutritionReferenceVersion,
            String scoringRuleVersion,
            Instant assessedAt
    ) {
        this(sessionId, productCategory, productCategoryReason, humanConsumptionStatus, overallScore, overallStatus,
                overallScore != null ? ScoreEligibility.RATED : ScoreEligibility.UNRATED,
                assessmentReliability, nutritionDataCompleteness, classificationReliability, scoreBreakdown,
                ingredientSummary, List.of(), nutritionSummary, keyConcerns, positiveIndicators, awarenessGuidance,
                populationGuidance, List.of(), sources, limitations, nutritionReferenceVersion, scoringRuleVersion, assessedAt);
    }
}
