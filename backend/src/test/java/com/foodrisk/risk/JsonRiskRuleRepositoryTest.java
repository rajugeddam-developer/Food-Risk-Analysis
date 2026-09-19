package com.foodrisk.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonRiskRuleRepositoryTest {

    private JsonRiskRuleRepository repository;

    @BeforeEach
    void setUp() {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();
        repository = new JsonRiskRuleRepository(resourceLoader, objectMapper);
        repository.initialize();
    }

    @Test
    @DisplayName("Repository loads sources, additives, and ingredient rules from JSON")
    void testInitialDataLoaded() {
        assertFalse(repository.getAllAdditives().isEmpty(), "Additives list should not be empty");
        assertFalse(repository.getAllIngredientRules().isEmpty(), "Ingredient rules should not be empty");

        Optional<SourceDefinition> fssaiSource = repository.findSourceById("FSSAI");
        assertTrue(fssaiSource.isPresent());
        assertEquals("FSSAI", fssaiSource.get().id());
        assertEquals("Food Safety and Standards Authority of India", fssaiSource.get().organization());
    }

    @Test
    @DisplayName("Canonical INS/E-number resolution matches INS 330, E330, INS330, and Citric Acid")
    void testCanonicalAdditiveResolution() {
        Optional<AdditiveRuleDefinition> ins330 = repository.findAdditiveByCodeOrSynonym("INS 330");
        assertTrue(ins330.isPresent(), "INS 330 should be found");
        assertEquals("INS 330", ins330.get().code());
        assertEquals(RegulatoryStatus.PERMITTED, ins330.get().regulatoryStatus());
        assertEquals(IngredientRiskLevel.NO_CONCERN, ins330.get().riskLevel());

        Optional<AdditiveRuleDefinition> e330 = repository.findAdditiveByCodeOrSynonym("E330");
        assertTrue(e330.isPresent(), "E330 should resolve to same additive");
        assertEquals("INS 330", e330.get().code());

        Optional<AdditiveRuleDefinition> ins330NoSpace = repository.findAdditiveByCodeOrSynonym("INS330");
        assertTrue(ins330NoSpace.isPresent(), "INS330 should resolve to same additive");

        Optional<AdditiveRuleDefinition> citricAcid = repository.findAdditiveByCodeOrSynonym("Citric Acid");
        assertTrue(citricAcid.isPresent(), "Citric Acid name should resolve to INS 330");
        assertEquals("INS 330", citricAcid.get().code());
    }

    @Test
    @DisplayName("Additive with restricted/banned status is accurately mapped")
    void testAdditiveRegulatoryStatuses() {
        // Monosodium glutamate INS 621: PERMITTED, NO_CONCERN (JECFA ADI not specified, Codex GMP)
        Optional<AdditiveRuleDefinition> msg = repository.findAdditiveByCodeOrSynonym("INS 621");
        assertTrue(msg.isPresent());
        assertEquals(RegulatoryStatus.PERMITTED, msg.get().regulatoryStatus());
        assertEquals(IngredientRiskLevel.NO_CONCERN, msg.get().riskLevel());

        // Potassium Bromate INS 924a: PROHIBITED in India by FSSAI since 2016
        Optional<AdditiveRuleDefinition> bromate = repository.findAdditiveByCodeOrSynonym("INS 924a");
        assertTrue(bromate.isPresent());
        assertEquals(RegulatoryStatus.PROHIBITED, bromate.get().regulatoryStatus());
        assertEquals(IngredientRiskLevel.HIGH_ATTENTION, bromate.get().riskLevel());
    }

    @Test
    @DisplayName("Ambiguous OCR strings return empty Optional instead of guessing")
    void testAmbiguousOcrQueriesReturnEmpty() {
        assertTrue(repository.findAdditiveByCodeOrSynonym("INS 3?0").isEmpty());
        assertTrue(repository.findAdditiveByCodeOrSynonym("E*30").isEmpty());
        assertTrue(repository.findAdditiveByCodeOrSynonym("").isEmpty());
        assertTrue(repository.findAdditiveByCodeOrSynonym(null).isEmpty());

        assertTrue(repository.findIngredientRule("Palm?Oil").isEmpty());
    }

    @Test
    @DisplayName("Ingredient rules resolve canonical names and aliases")
    void testIngredientRuleResolution() {
        Optional<IngredientRuleDefinition> palmOil = repository.findIngredientRule("Palm Oil");
        assertTrue(palmOil.isPresent());
        assertEquals("Palm Oil", palmOil.get().canonicalName());
        assertEquals(IngredientRiskLevel.LOW_ATTENTION, palmOil.get().riskLevel());

        Optional<IngredientRuleDefinition> refinedPalm = repository.findIngredientRule("Refined Palm Olein");
        assertTrue(refinedPalm.isPresent());
        assertEquals("Palm Oil", refinedPalm.get().canonicalName());

        Optional<IngredientRuleDefinition> hfcs = repository.findIngredientRule("High Fructose Corn Syrup");
        assertTrue(hfcs.isPresent());
        assertEquals(IngredientRiskLevel.MODERATE_ATTENTION, hfcs.get().riskLevel());
    }
}
