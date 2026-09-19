package com.foodrisk.scoring;

import com.foodrisk.nutrition.ComparisonStatus;
import com.foodrisk.nutrition.NutrientType;
import com.foodrisk.nutrition.NutritionAnalysisResult;
import com.foodrisk.nutrition.NutritionFinding;
import com.foodrisk.risk.IngredientRiskAnalysisResult;
import com.foodrisk.risk.IngredientRiskItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic engine evaluating M8 ingredients and M9 nutrition findings against
 * authoritative demographic guidance rules (WHO, FSSAI, Codex, ICMR).
 *
 * Enforces scientific boundaries:
 * - Generates contextual consumer awareness, NOT medical diagnosis or clinical prediction.
 * - Rules are strictly data-driven via AgeGuidanceRepository.
 * - Distinguishes Children, Adults, and Older Adults.
 */
@Component
public class AgeGuidanceEngine {

    private static final Logger log = LoggerFactory.getLogger(AgeGuidanceEngine.class);

    private final AgeGuidanceRepository repository;

    public AgeGuidanceEngine(AgeGuidanceRepository repository) {
        this.repository = repository;
    }

    public List<AgeGroupAwareness> evaluateDemographics(
            IngredientRiskAnalysisResult ingredientRisk,
            NutritionAnalysisResult nutrition
    ) {
        List<AgeGroupAwareness> result = new ArrayList<>();
        for (AgeGroup group : AgeGroup.values()) {
            result.add(evaluateSingleGroup(group, ingredientRisk, nutrition));
        }
        return result;
    }

    private AgeGroupAwareness evaluateSingleGroup(
            AgeGroup group,
            IngredientRiskAnalysisResult ingredientRisk,
            NutritionAnalysisResult nutrition
    ) {
        List<AgeGuidanceRuleDefinition> rules = repository.getRulesForAgeGroup(group);
        List<String> contributingFactors = new ArrayList<>();
        Set<String> sourceIds = new LinkedHashSet<>();
        AgeAttentionLevel currentLevel = AgeAttentionLevel.ACCEPTABLE;

        if (rules.isEmpty()) {
            return new AgeGroupAwareness(
                    group,
                    AgeAttentionLevel.ACCEPTABLE,
                    "General dietary awareness applies. Follow balanced meal guidelines.",
                    List.of(),
                    List.of("WHO")
            );
        }

        for (AgeGuidanceRuleDefinition rule : rules) {
            boolean matched = false;

            if ("NUTRIENT".equalsIgnoreCase(rule.triggerType())) {
                matched = matchNutrientRule(rule, nutrition);
            } else if ("ADDITIVE_KEYWORD".equalsIgnoreCase(rule.triggerType())) {
                matched = matchAdditiveKeywordRule(rule, ingredientRisk);
            } else if ("POSITIVE_NUTRIENT".equalsIgnoreCase(rule.triggerType())) {
                matched = matchPositiveNutrientRule(rule, nutrition);
            }

            if (matched) {
                if (rule.reason() != null && !rule.reason().isBlank()) {
                    contributingFactors.add(rule.reason());
                }
                if (rule.sourceId() != null) {
                    sourceIds.add(rule.sourceId());
                }

                AgeAttentionLevel ruleLevel = parseLevel(rule.attentionLevel());
                if (isMoreSevere(ruleLevel, currentLevel)) {
                    currentLevel = ruleLevel;
                }
            }
        }

        String summary = generateGroupSummary(group, currentLevel, contributingFactors);

        return new AgeGroupAwareness(
                group,
                currentLevel,
                summary,
                contributingFactors,
                new ArrayList<>(sourceIds)
        );
    }

    private boolean matchNutrientRule(AgeGuidanceRuleDefinition rule, NutritionAnalysisResult nutrition) {
        if (nutrition == null || nutrition.findings() == null) return false;
        try {
            NutrientType target = NutrientType.valueOf(rule.nutrient().toUpperCase(Locale.ROOT));
            for (NutritionFinding f : nutrition.findings()) {
                if (f.nutrient() == target) {
                    if (f.status() == ComparisonStatus.ABOVE_REFERENCE) {
                        return true;
                    }
                    if (f.normalizedValue() != null && rule.threshold() != null) {
                        if (f.normalizedValue().compareTo(BigDecimal.valueOf(rule.threshold())) >= 0) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean matchAdditiveKeywordRule(AgeGuidanceRuleDefinition rule, IngredientRiskAnalysisResult ingredientRisk) {
        if (ingredientRisk == null || ingredientRisk.items() == null || rule.keywords() == null) return false;
        for (IngredientRiskItem item : ingredientRisk.items()) {
            String text = ((item.normalizedName() != null ? item.normalizedName() : "") + " " +
                           (item.originalIngredient() != null ? item.originalIngredient() : "") + " " +
                           (item.additiveCode() != null ? item.additiveCode() : "")).toUpperCase(Locale.ROOT);

            for (String kw : rule.keywords()) {
                if (text.contains(kw.toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchPositiveNutrientRule(AgeGuidanceRuleDefinition rule, NutritionAnalysisResult nutrition) {
        if (nutrition == null || nutrition.findings() == null) return false;
        try {
            NutrientType target = NutrientType.valueOf(rule.nutrient().toUpperCase(Locale.ROOT));
            for (NutritionFinding f : nutrition.findings()) {
                if (f.nutrient() == target && f.normalizedValue() != null && rule.threshold() != null) {
                    if (f.normalizedValue().compareTo(BigDecimal.valueOf(rule.threshold())) >= 0) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private AgeAttentionLevel parseLevel(String level) {
        if (level == null) return AgeAttentionLevel.ACCEPTABLE;
        try {
            return AgeAttentionLevel.valueOf(level.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AgeAttentionLevel.ACCEPTABLE;
        }
    }

    private boolean isMoreSevere(AgeAttentionLevel candidate, AgeAttentionLevel existing) {
        return candidate.ordinal() > existing.ordinal();
    }

    private String generateGroupSummary(AgeGroup group, AgeAttentionLevel level, List<String> factors) {
        return switch (group) {
            case CHILDREN -> switch (level) {
                case HIGHER_ATTENTION -> "Higher attention advised for children due to elevated sugar, trans fat, sodium, or sensitive additives.";
                case MODERATE_ATTENTION -> "Moderate attention advised for children; consider portion size and consumption frequency.";
                case ACCEPTABLE -> "Suitable for inclusion in balanced children's meals within appropriate portion sizes.";
            };
            case ADULTS -> switch (level) {
                case HIGHER_ATTENTION -> "Higher attention advised for adults due to elevated saturated fat, trans fat, or high sodium concentration.";
                case MODERATE_ATTENTION -> "Moderate attention advised; factor into overall daily dietary balance.";
                case ACCEPTABLE -> "Nutritional profile is suitable for standard dietary rotation in balanced portions.";
            };
            case OLDER_ADULTS -> switch (level) {
                case HIGHER_ATTENTION -> "Higher attention recommended for older adults managing blood pressure and cardiovascular health.";
                case MODERATE_ATTENTION -> "Moderate attention advised for older adults regarding sodium or saturated lipid intake.";
                case ACCEPTABLE -> "Suitable for older adults with no specific sodium or saturated lipid restrictions flagged.";
            };
        };
    }
}