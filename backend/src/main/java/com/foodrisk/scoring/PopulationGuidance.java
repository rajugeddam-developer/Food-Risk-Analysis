package com.foodrisk.scoring;

/**
 * Balanced, non-medical consumer awareness guidance tailored for broad demographic contexts.
 */
public record PopulationGuidance(
        String generalPopulation,
        String children,
        String specialDietaryNeeds
) {}
