package com.foodrisk.nutrition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonNutritionRuleRepositoryTest {

    private JsonNutritionRuleRepository repository;

    @BeforeEach
    void setUp() {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();
        repository = new JsonNutritionRuleRepository(resourceLoader, objectMapper);
        repository.initialize();
    }

    @Test
    @DisplayName("Repository loads version, notes, and rules from nutrition-rules.json")
    void testInitialDataLoaded() {
        assertEquals("2026.09", repository.getReferenceVersion());
        assertFalse(repository.getAllRules().isEmpty(), "Nutrition rules should not be empty");

        // Verify rules exist for key nutrients
        List<NutritionRuleDefinition> sugarRules = repository.getRulesForNutrient(NutrientType.TOTAL_SUGARS);
        assertFalse(sugarRules.isEmpty(), "Rules for TOTAL_SUGARS should be present");

        List<NutritionRuleDefinition> sodiumRules = repository.getRulesForNutrient(NutrientType.SODIUM);
        assertFalse(sodiumRules.isEmpty(), "Rules for SODIUM should be present");

        List<NutritionRuleDefinition> transFatRules = repository.getRulesForNutrient(NutrientType.TRANS_FAT);
        assertFalse(transFatRules.isEmpty(), "Rules for TRANS_FAT should be present");

        List<NutritionRuleDefinition> fibreRules = repository.getRulesForNutrient(NutrientType.FIBRE);
        assertFalse(fibreRules.isEmpty(), "Rules for FIBRE should be present");
    }

    @Test
    @DisplayName("Rules for a nutrient are sorted descending by threshold")
    void testRuleSortingDescending() {
        List<NutritionRuleDefinition> sugarRules = repository.getRulesForNutrient(NutrientType.TOTAL_SUGARS);
        assertTrue(sugarRules.size() >= 2, "Expected at least 2 sugar rules (high and moderate)");

        BigDecimal prevThreshold = BigDecimal.valueOf(Double.MAX_VALUE);
        for (NutritionRuleDefinition rule : sugarRules) {
            assertTrue(rule.threshold().compareTo(prevThreshold) <= 0,
                    "Rules should be ordered descending by threshold");
            prevThreshold = rule.threshold();
        }
    }

    @Test
    @DisplayName("Trans fat rule has PRODUCT_CLASSIFICATION_THRESHOLD reference type and FSSAI source")
    void testTransFatRuleMetadata() {
        List<NutritionRuleDefinition> transFatRules = repository.getRulesForNutrient(NutrientType.TRANS_FAT);
        assertFalse(transFatRules.isEmpty());

        NutritionRuleDefinition rule = transFatRules.get(0);
        assertEquals(ReferenceType.PRODUCT_CLASSIFICATION_THRESHOLD, rule.referenceType());
        assertEquals("FSSAI", rule.sourceId());
        assertEquals(NutritionBasis.PER_100G, rule.basis());
        assertEquals(NutritionSeverity.HIGH, rule.severity());
    }

    @Test
    @DisplayName("Fibre rule has CODEX source and positive benchmark")
    void testFibreRuleMetadata() {
        List<NutritionRuleDefinition> fibreRules = repository.getRulesForNutrient(NutrientType.FIBRE);
        assertFalse(fibreRules.isEmpty());

        NutritionRuleDefinition rule = fibreRules.get(0);
        assertEquals(ReferenceType.NUTRITION_REFERENCE, rule.referenceType());
        assertEquals("CODEX", rule.sourceId());
        assertEquals(new BigDecimal("3.0"), rule.threshold());
    }
}
