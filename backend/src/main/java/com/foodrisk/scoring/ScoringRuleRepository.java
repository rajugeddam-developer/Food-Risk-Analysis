package com.foodrisk.scoring;

/**
 * Repository interface for retrieving Food Awareness scoring rules and thresholds.
 */
public interface ScoringRuleRepository {

    ScoringRuleDefinition getScoringRules();

    String getScoringRuleVersion();
}
