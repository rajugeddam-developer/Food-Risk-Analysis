package com.foodrisk.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.dto.NormalizedIngredient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IngredientRiskEngineTest {

    private IngredientRiskEngine engine;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        JsonRiskRuleRepository repository = new JsonRiskRuleRepository(
                new DefaultResourceLoader(),
                new ObjectMapper()
        );
        repository.initialize();
        engine = new IngredientRiskEngine(repository);
        sessionId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Empty ingredients list returns empty summary with zero counts")
    void testEmptyIngredients() {
        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of());
        assertNotNull(result);
        assertEquals(sessionId, result.sessionId());
        assertEquals(0, result.summary().totalIngredients());
        assertEquals(0, result.summary().highAttentionIngredients());
        assertTrue(result.items().isEmpty());
    }

    @Test
    @DisplayName("Permitted additive (INS 330) evaluates to PERMITTED and NO_CONCERN")
    void testPermittedAdditiveNoConcern() {
        NormalizedIngredient citricAcid = new NormalizedIngredient(
                "Acidity Regulator (INS 330)", "INS 330", false, "INS 330", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(citricAcid));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Citric Acid", item.normalizedName());
        assertEquals("INS 330", item.additiveCode());
        assertEquals(RegulatoryStatus.PERMITTED, item.regulatoryStatus());
        assertEquals(IngredientRiskLevel.NO_CONCERN, item.riskLevel());
        assertEquals(EvidenceStatus.SUPPORTED, item.evidenceStatus());
        assertEquals("Acidity Regulator", item.functionalClass());
        assertFalse(item.reasons().isEmpty());
    }

    @Test
    @DisplayName("Permitted additive MSG (INS 621) evaluates to PERMITTED and NO_CONCERN based on JECFA/Codex GMP evidence")
    void testPermittedAdditiveMsgNoConcern() {
        NormalizedIngredient msg = new NormalizedIngredient(
                "Monosodium Glutamate", "MSG", false, "INS 621", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(msg));

        IngredientRiskItem item = result.items().get(0);
        assertEquals(RegulatoryStatus.PERMITTED, item.regulatoryStatus());
        assertEquals(IngredientRiskLevel.NO_CONCERN, item.riskLevel());
        assertEquals(1, result.summary().noConcernIngredients());
        assertEquals(0, result.summary().highAttentionIngredients());
        assertEquals(0, result.summary().moderateAttentionIngredients());
        assertNotNull(item.summary());
    }

    @Test
    @DisplayName("Prohibited additive (INS 924a) is marked PROHIBITED and HIGH_ATTENTION")
    void testBannedAdditive() {
        NormalizedIngredient bromate = new NormalizedIngredient(
                "Potassium Bromate", "Potassium Bromate", false, "INS 924a", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(bromate));

        IngredientRiskItem item = result.items().get(0);
        assertEquals(RegulatoryStatus.PROHIBITED, item.regulatoryStatus());
        assertEquals(IngredientRiskLevel.HIGH_ATTENTION, item.riskLevel());
        assertEquals(1, result.summary().highAttentionIngredients());
    }

    @Test
    @DisplayName("Verified ingredient rule (Palm Oil) flags LOW_ATTENTION")
    void testIngredientRulePalmOil() {
        NormalizedIngredient palmOil = new NormalizedIngredient(
                "Refined Palm Oil", "Refined Palm Oil", false, null, false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(palmOil));

        IngredientRiskItem item = result.items().get(0);
        assertEquals("Palm Oil", item.normalizedName());
        assertEquals(IngredientRiskLevel.LOW_ATTENTION, item.riskLevel());
        assertEquals(EvidenceStatus.SUPPORTED, item.evidenceStatus());
        assertEquals(1, result.summary().lowAttentionIngredients());
    }

    @Test
    @DisplayName("Unrecognized ingredient defaults to RegulatoryStatus.UNKNOWN, never BANNED")
    void testUnrecognizedIngredient() {
        NormalizedIngredient mystery = new NormalizedIngredient(
                "Unknown", "xyz?123", false, null, false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(mystery));

        IngredientRiskItem item = result.items().get(0);
        assertEquals(RegulatoryStatus.UNKNOWN, item.regulatoryStatus());
        assertEquals(IngredientRiskLevel.UNKNOWN, item.riskLevel());
        assertEquals(EvidenceStatus.INSUFFICIENT, item.evidenceStatus());
        assertNotEquals(RegulatoryStatus.BANNED, item.regulatoryStatus());
    }

    @Test
    @DisplayName("Uncertain M6 OCR flag is preserved and not converted into confirmed hazard")
    void testUncertainOcrPreserved() {
        NormalizedIngredient uncertainIng = new NormalizedIngredient(
                "Sod..m B.nz..te", "Sod..m B.nz..te", false, null, true
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(uncertainIng));

        IngredientRiskItem item = result.items().get(0);
        assertEquals(IngredientRiskLevel.UNKNOWN, item.riskLevel());
        assertEquals(EvidenceStatus.INSUFFICIENT, item.evidenceStatus());
        assertEquals(1, result.summary().uncertainIngredients());
    }

    @Test
    @DisplayName("Duplicate ingredients are deduplicated in summary counts to prevent double counting")
    void testDeduplicationInSummary() {
        NormalizedIngredient palm1 = new NormalizedIngredient(
                "Palm Oil", "Palm Oil", false, null, false
        );
        NormalizedIngredient palm2 = new NormalizedIngredient(
                "Palm Olein", "Palm Olein", false, null, false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(palm1, palm2));

        // Both items retained in items list for full traceability
        assertEquals(2, result.items().size());
        // Deduplicated in summary: Palm Oil counted once in low attention count
        assertEquals(1, result.summary().lowAttentionIngredients());
    }

    @Test
    @DisplayName("Sugar and Salt culinary ingredients evaluate to NO_CONCERN and PERMITTED, avoiding unfair hazard flags")
    void testSugarAndSaltCulinaryIngredients() {
        NormalizedIngredient sugar = new NormalizedIngredient("Sugar", "Sugar", false, null, false);
        NormalizedIngredient salt = new NormalizedIngredient("Iodised Salt", "Salt", false, null, false);

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(sugar, salt));

        assertEquals(2, result.items().size());
        for (IngredientRiskItem item : result.items()) {
            assertEquals(RegulatoryStatus.PERMITTED, item.regulatoryStatus());
            assertEquals(IngredientRiskLevel.NO_CONCERN, item.riskLevel());
            assertNotNull(item.summary(), "Deterministic summary should be present");
        }
        assertEquals(0, result.summary().highAttentionIngredients());
        assertEquals(0, result.summary().moderateAttentionIngredients());
        assertEquals(2, result.summary().noConcernIngredients());
    }

    @Test
    @DisplayName("Curated additive INS 551 (Silicon Dioxide) resolves with verified sources and NO_CONCERN")
    void testIns551SiliconDioxide() {
        NormalizedIngredient silica = new NormalizedIngredient("Silicon Dioxide (INS 551)", "INS 551", true, "INS 551", false);

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(silica));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Silicon Dioxide", item.normalizedName());
        assertEquals(RegulatoryStatus.PERMITTED, item.regulatoryStatus());
        assertEquals(IngredientRiskLevel.NO_CONCERN, item.riskLevel());
        assertEquals(EvidenceStatus.SUPPORTED, item.evidenceStatus());
        assertTrue(item.sourceIds().contains("FSSAI") || item.sourceIds().contains("JECFA"));
    }

    @Test
    @DisplayName("Completely unlisted ingredient (Xyzbar) strictly defaults to UNKNOWN with INSUFFICIENT evidence")
    void testUnlistedIngredientXyzbar() {
        NormalizedIngredient unlisted = new NormalizedIngredient("Xyzbar", "Xyzbar", false, null, false);

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(unlisted));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals(RegulatoryStatus.UNKNOWN, item.regulatoryStatus());
        assertEquals(IngredientRiskLevel.UNKNOWN, item.riskLevel());
        assertEquals(EvidenceStatus.INSUFFICIENT, item.evidenceStatus());
        assertEquals("This ingredient could not be verified against the configured knowledge base.", item.summary());
        assertEquals(1, result.summary().unknownIngredients());
    }

    @Test
    @DisplayName("Blank ingredient string evaluates to UNKNOWN without crashing")
    void testBlankIngredient() {
        NormalizedIngredient blank = new NormalizedIngredient("   ", "   ", false, null, false);

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(blank));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals(IngredientRiskLevel.UNKNOWN, item.riskLevel());
        assertEquals(RegulatoryStatus.UNKNOWN, item.regulatoryStatus());
        assertEquals(EvidenceStatus.INSUFFICIENT, item.evidenceStatus());
    }

    @Test
    @DisplayName("Invalid fuzzy substring match: Watermelon does NOT match Water, Buckwheat does NOT match Wheat Flour")
    void testInvalidFuzzySubstringMatchDoesNotMatch() {
        NormalizedIngredient watermelon = new NormalizedIngredient("Watermelon", "Watermelon", false, null, false);
        NormalizedIngredient buckwheat = new NormalizedIngredient("Buckwheat", "Buckwheat", false, null, false);

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(watermelon, buckwheat));

        assertEquals(2, result.items().size());
        for (IngredientRiskItem item : result.items()) {
            assertEquals(IngredientRiskLevel.UNKNOWN, item.riskLevel(), "Unrelated ingredient should not match via substring");
            assertEquals(RegulatoryStatus.UNKNOWN, item.regulatoryStatus());
            assertEquals(EvidenceStatus.INSUFFICIENT, item.evidenceStatus());
        }
        assertEquals(2, result.summary().unknownIngredients());
    }

    @Test
    @DisplayName("Valid clause matching: Vegetable Oil (Palm Oil) resolves to Palm Oil")
    void testValidClauseMatchResolves() {
        NormalizedIngredient ing = new NormalizedIngredient(
                "Vegetable Oil (Palm Oil)", "Vegetable Oil (Palm Oil)", false, null, false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(ing));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Palm Oil", item.normalizedName());
        assertEquals(IngredientRiskLevel.LOW_ATTENTION, item.riskLevel());
        assertEquals(RegulatoryStatus.PERMITTED, item.regulatoryStatus());
    }

    @Test
    @DisplayName("Tier 1 substance: Propylparaben (INS 216 / E216) evaluates to HIGH_ATTENTION with jurisdiction-specific regulatory breakdown")
    void testTier1Propylparaben() {
        NormalizedIngredient propylparaben = new NormalizedIngredient(
                "Preservative (INS 216)", "Propylparaben", true, "INS 216", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(propylparaben));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Propylparaben", item.normalizedName());
        assertEquals(IngredientRiskLevel.HIGH_ATTENTION, item.riskLevel());
        assertEquals(RegulatoryStatus.RESTRICTED, item.regulatoryStatus());
        assertEquals("Regional Variation", item.jurisdiction());
        assertNotNull(item.jurisdictionalStatus());
        assertTrue(item.jurisdictionalStatus().containsKey("European Union"));
        assertTrue(item.sourceIds().contains("EFSA") || item.sourceIds().contains("FDA"));
    }

    @Test
    @DisplayName("Tier 1 substance: Azodicarbonamide (INS 927a / ADA) evaluates to HIGH_ATTENTION with jurisdiction-specific regulatory breakdown")
    void testTier1Azodicarbonamide() {
        NormalizedIngredient ada = new NormalizedIngredient(
                "Flour Treatment Agent (INS 927a)", "Azodicarbonamide", true, "INS 927a", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(ada));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Azodicarbonamide", item.normalizedName());
        assertEquals(IngredientRiskLevel.HIGH_ATTENTION, item.riskLevel());
        assertEquals(RegulatoryStatus.PROHIBITED, item.regulatoryStatus());
        assertEquals("Regional Variation", item.jurisdiction());
        assertNotNull(item.jurisdictionalStatus());
        assertTrue(item.jurisdictionalStatus().containsKey("United States"));
        assertTrue(item.jurisdictionalStatus().containsKey("India"));
    }

    @Test
    @DisplayName("Tier 1 substance: Potassium Bromate (INS 924a) distinguishes FSSAI regulatory ban from JECFA evaluation")
    void testTier1PotassiumBromateJurisdiction() {
        NormalizedIngredient bromate = new NormalizedIngredient(
                "Flour Improver (INS 924a)", "Potassium Bromate", true, "INS 924a", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(bromate));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Potassium Bromate", item.normalizedName());
        assertEquals(IngredientRiskLevel.HIGH_ATTENTION, item.riskLevel());
        assertEquals(RegulatoryStatus.PROHIBITED, item.regulatoryStatus());
        assertNotNull(item.jurisdictionalStatus());
        assertTrue(item.jurisdictionalStatus().containsKey("India"));
    }

    @Test
    @DisplayName("Tier 1 substance: BHA (INS 320) evaluates to MODERATE_ATTENTION and RESTRICTED")
    void testTier1Bha() {
        NormalizedIngredient bha = new NormalizedIngredient(
                "Antioxidant (INS 320)", "BHA", true, "INS 320", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(bha));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Butylated Hydroxyanisole (BHA)", item.normalizedName());
        assertEquals(IngredientRiskLevel.MODERATE_ATTENTION, item.riskLevel());
        assertEquals(RegulatoryStatus.RESTRICTED, item.regulatoryStatus());
    }

    @Test
    @DisplayName("Tier 1 substance: Titanium Dioxide (INS 171) evaluates to HIGH_ATTENTION and RESTRICTED")
    void testTier1TitaniumDioxide() {
        NormalizedIngredient tio2 = new NormalizedIngredient(
                "Color (INS 171)", "Titanium Dioxide", true, "INS 171", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(tio2));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Titanium Dioxide", item.normalizedName());
        assertEquals(IngredientRiskLevel.HIGH_ATTENTION, item.riskLevel());
        assertEquals(RegulatoryStatus.RESTRICTED, item.regulatoryStatus());
    }

    @Test
    @DisplayName("Tier 1 substance: Brominated Vegetable Oil (BVO) evaluates to HIGH_ATTENTION and PROHIBITED")
    void testTier1BrominatedVegetableOil() {
        NormalizedIngredient bvo = new NormalizedIngredient(
                "Brominated Vegetable Oil", "BVO", false, null, false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(bvo));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Brominated Vegetable Oil", item.normalizedName());
        assertEquals(IngredientRiskLevel.HIGH_ATTENTION, item.riskLevel());
        assertEquals(RegulatoryStatus.PROHIBITED, item.regulatoryStatus());
    }

    @Test
    @DisplayName("Tier 2 substance: Carrageenan (INS 407) evaluates to LOW_ATTENTION and PERMITTED with cautious scientific wording")
    void testTier2Carrageenan() {
        NormalizedIngredient carrageenan = new NormalizedIngredient(
                "Thickener (INS 407)", "Carrageenan", true, "INS 407", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(carrageenan));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Carrageenan", item.normalizedName());
        assertEquals(IngredientRiskLevel.LOW_ATTENTION, item.riskLevel());
        assertEquals(RegulatoryStatus.PERMITTED, item.regulatoryStatus());
    }

    @Test
    @DisplayName("Tier 2 substance: Sucralose (INS 955) evaluates to LOW_ATTENTION and PERMITTED")
    void testTier2Sucralose() {
        NormalizedIngredient sucralose = new NormalizedIngredient(
                "Sweetener (INS 955)", "Sucralose", true, "INS 955", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(sucralose));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Sucralose", item.normalizedName());
        assertEquals(IngredientRiskLevel.LOW_ATTENTION, item.riskLevel());
        assertEquals(RegulatoryStatus.PERMITTED, item.regulatoryStatus());
    }

    @Test
    @DisplayName("Tier 4 commonly permitted additive: Xanthan Gum (INS 415) evaluates to NO_CONCERN and PERMITTED with no specific concern identified")
    void testTier4XanthanGum() {
        NormalizedIngredient xanthan = new NormalizedIngredient(
                "Stabilizer (INS 415)", "Xanthan Gum", true, "INS 415", false
        );

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(xanthan));

        assertEquals(1, result.items().size());
        IngredientRiskItem item = result.items().get(0);
        assertEquals("Xanthan Gum", item.normalizedName());
        assertEquals(IngredientRiskLevel.NO_CONCERN, item.riskLevel());
        assertEquals(RegulatoryStatus.PERMITTED, item.regulatoryStatus());
        assertEquals(0, result.summary().highAttentionIngredients());
        assertEquals(1, result.summary().noConcernIngredients());
    }

    @Test
    @DisplayName("E-number does NOT automatically mean harmful: E330, E415, E500 evaluate to NO_CONCERN")
    void testENumberDoesNotMeanHarmful() {
        NormalizedIngredient e330 = new NormalizedIngredient("E330", "E330", false, "E330", false);
        NormalizedIngredient e415 = new NormalizedIngredient("E415", "E415", false, "E415", false);
        NormalizedIngredient e500 = new NormalizedIngredient("E500", "E500", false, "E500", false);

        IngredientRiskAnalysisResult result = engine.evaluate(sessionId, List.of(e330, e415, e500));

        assertEquals(3, result.items().size());
        for (IngredientRiskItem item : result.items()) {
            assertEquals(RegulatoryStatus.PERMITTED, item.regulatoryStatus());
            assertEquals(IngredientRiskLevel.NO_CONCERN, item.riskLevel());
        }
        assertEquals(3, result.summary().noConcernIngredients());
        assertEquals(0, result.summary().highAttentionIngredients());
    }
}

