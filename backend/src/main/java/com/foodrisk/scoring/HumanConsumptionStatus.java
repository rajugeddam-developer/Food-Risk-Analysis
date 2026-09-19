package com.foodrisk.scoring;

/**
 * Indicates whether the analyzed packaging item is formulated for human dietary consumption.
 */
public enum HumanConsumptionStatus {
    HUMAN_FOOD,
    NOT_INTENDED_FOR_HUMAN_CONSUMPTION,
    UNKNOWN
}
