package com.foodrisk.cache;

import com.foodrisk.classification.FoodClassificationResult;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.scoring.FoodRiskAssessment;

import java.time.Instant;

/**
 * Model representing cached analysis results for a unique product fingerprint.
 */
public class CachedProductAnalysis {

    private String fingerprint;
    private String scoringRuleVersion;
    private String nutritionReferenceVersion;
    private NormalizedFoodData normalizedFoodData;
    private FoodClassificationResult classificationResult;
    private IngredientRiskAnalysisResult ingredientRiskResult;
    private NutritionAnalysisResult nutritionResult;
    private FoodRiskAssessment foodRiskAssessment;
    private Instant cachedAt;

    public CachedProductAnalysis() {
    }

    public CachedProductAnalysis(
            String fingerprint,
            String scoringRuleVersion,
            String nutritionReferenceVersion,
            NormalizedFoodData normalizedFoodData,
            FoodClassificationResult classificationResult,
            IngredientRiskAnalysisResult ingredientRiskResult,
            NutritionAnalysisResult nutritionResult,
            FoodRiskAssessment foodRiskAssessment
    ) {
        this.fingerprint = fingerprint;
        this.scoringRuleVersion = scoringRuleVersion;
        this.nutritionReferenceVersion = nutritionReferenceVersion;
        this.normalizedFoodData = normalizedFoodData;
        this.classificationResult = classificationResult;
        this.ingredientRiskResult = ingredientRiskResult;
        this.nutritionResult = nutritionResult;
        this.foodRiskAssessment = foodRiskAssessment;
        this.cachedAt = Instant.now();
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getScoringRuleVersion() {
        return scoringRuleVersion;
    }

    public void setScoringRuleVersion(String scoringRuleVersion) {
        this.scoringRuleVersion = scoringRuleVersion;
    }

    public String getNutritionReferenceVersion() {
        return nutritionReferenceVersion;
    }

    public void setNutritionReferenceVersion(String nutritionReferenceVersion) {
        this.nutritionReferenceVersion = nutritionReferenceVersion;
    }

    public NormalizedFoodData getNormalizedFoodData() {
        return normalizedFoodData;
    }

    public void setNormalizedFoodData(NormalizedFoodData normalizedFoodData) {
        this.normalizedFoodData = normalizedFoodData;
    }

    public FoodClassificationResult getClassificationResult() {
        return classificationResult;
    }

    public void setClassificationResult(FoodClassificationResult classificationResult) {
        this.classificationResult = classificationResult;
    }

    public IngredientRiskAnalysisResult getIngredientRiskResult() {
        return ingredientRiskResult;
    }

    public void setIngredientRiskResult(IngredientRiskAnalysisResult ingredientRiskResult) {
        this.ingredientRiskResult = ingredientRiskResult;
    }

    public NutritionAnalysisResult getNutritionResult() {
        return nutritionResult;
    }

    public void setNutritionResult(NutritionAnalysisResult nutritionResult) {
        this.nutritionResult = nutritionResult;
    }

    public FoodRiskAssessment getFoodRiskAssessment() {
        return foodRiskAssessment;
    }

    public void setFoodRiskAssessment(FoodRiskAssessment foodRiskAssessment) {
        this.foodRiskAssessment = foodRiskAssessment;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public void setCachedAt(Instant cachedAt) {
        this.cachedAt = cachedAt;
    }
}
