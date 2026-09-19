package com.foodrisk.classification;

/**
 * Product classification categories.
 *
 * Explicitly excludes alarmist or unprovable classifications such as "WASTE_FOOD".
 */
public enum FoodCategory {
    HUMAN_FOOD,
    PET_FOOD,
    ANIMAL_FEED,
    NON_FOOD,
    UNKNOWN
}
