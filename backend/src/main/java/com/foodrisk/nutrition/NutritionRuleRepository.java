package com.foodrisk.nutrition;

import java.util.List;

/**
 * Strategy interface for accessing authoritative nutrition reference rules.
 */
public interface NutritionRuleRepository {

    /**
     * Retrieves all verified reference rules for a specific nutrient type, ordered by threshold descending.
     */
    List<NutritionRuleDefinition> getRulesForNutrient(NutrientType nutrient);

    /**
     * Retrieves all loaded reference rules.
     */
    List<NutritionRuleDefinition> getAllRules();

    /**
     * Version identifier of the loaded reference dataset.
     */
    String getReferenceVersion();
}
