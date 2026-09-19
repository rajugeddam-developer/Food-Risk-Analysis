package com.foodrisk.nutrition;

/**
 * Qualitative metric expressing the proportion of core nutrition facts declared on the packaging.
 */
public enum DataCompleteness {
    /** All primary macronutrients and key risk nutrients are declared and extracted. */
    COMPLETE,

    /** Some core nutrients are declared, but key items are omitted or unreadable. */
    PARTIAL,

    /** Critical nutritional data is absent or fewer than two nutrient entries could be verified. */
    INSUFFICIENT
}
