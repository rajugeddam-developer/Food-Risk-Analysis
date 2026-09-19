package com.foodrisk.scoring;

import java.util.List;

/**
 * Contract for accessing authoritative demographic guidance rules.
 */
public interface AgeGuidanceRepository {

    List<AgeGuidanceRuleDefinition> getRulesForAgeGroup(AgeGroup ageGroup);

    List<AgeGuidanceRuleDefinition> getAllRules();
}
