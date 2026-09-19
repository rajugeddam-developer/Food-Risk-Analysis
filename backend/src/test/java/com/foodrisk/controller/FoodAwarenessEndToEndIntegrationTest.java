package com.foodrisk.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrisk.dto.NormalizedFoodData;
import com.foodrisk.dto.NormalizedIngredient;
import com.foodrisk.dto.NormalizedNutrition;
import com.foodrisk.service.context.AnalysisContextStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End integration test validating the complete Food Awareness pipeline:
 * Structured Food Data -> M7 -> M8 -> M9 -> M10 -> Age Awareness -> REST API contract.
 *
 * Verifies:
 * - Real deterministic Food Awareness Score (0-100)
 * - Real M8 ingredient items with 1-line deterministic summaries
 * - Real M9 nutrition findings with explicit zero preservation
 * - Real data-driven AgeGroupAwareness across Children, Adults, and Older Adults
 * - Absolute absence of demo / fallback data in the live response
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FoodAwarenessEndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalysisContextStore contextStore;

    @Test
    @DisplayName("Complete E2E analysis pipeline produces verified FoodRiskAssessment contract without demo data")
    void testCompleteAnalysisPipelineEndToEnd() throws Exception {
        // 1. Create a real analysis session via REST API
        MvcResult sessionResult = mockMvc.perform(post("/api/analysis/session")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn();

        JsonNode sessionJson = objectMapper.readTree(sessionResult.getResponse().getContentAsString());
        UUID sessionId = UUID.fromString(sessionJson.get("sessionId").asText());

        // 2. Supply realistic Structured Food Data (as produced by OCR + Gemini normalization)
        List<NormalizedIngredient> ingredients = List.of(
                new NormalizedIngredient("Whole Wheat Flour (Atta)", "Whole Wheat Flour", false, null, false),
                new NormalizedIngredient("Rolled Oats", "Rolled Oats", false, null, false),
                new NormalizedIngredient("Sugar", "Sugar", false, null, false),
                new NormalizedIngredient("Refined Palm Oil", "Palm Oil", false, null, false),
                new NormalizedIngredient("Citric Acid (INS 330)", "INS 330", true, "INS 330", false),
                new NormalizedIngredient("Iodised Salt", "Salt", false, null, false)
        );

        NormalizedNutrition nutrition = new NormalizedNutrition(
                "PER_100G",
                440.0,
                8.0,
                65.0,
                18.0,
                12.0,
                16.0,
                7.0,
                0.0, // explicit zero trans fat
                400.0,
                5.0,
                List.of("Energy: 440 kcal", "Protein: 8g", "Sugars: 18g", "Added Sugars: 12g", "Sat Fat: 7g", "Trans Fat: 0g", "Sodium: 400mg", "Fibre: 5g")
        );

        NormalizedFoodData normalizedFoodData = new NormalizedFoodData(
                "Whole Grain Oat & Honey Biscuit",
                "30g",
                30.0,
                ingredients,
                nutrition,
                List.of()
        );

        contextStore.storeNormalizedFoodData(sessionId, normalizedFoodData);

        // 3. Execute M7: Food Classification
        mockMvc.perform(post("/api/analysis/" + sessionId + "/classify")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("HUMAN_FOOD"));

        // 4. Execute M8: Ingredient Risk Analysis
        mockMvc.perform(post("/api/analysis/" + sessionId + "/ingredient-risk")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalIngredients").value(6))
                .andExpect(jsonPath("$.summary.identifiedIngredients").value(6))
                .andExpect(jsonPath("$.items", hasSize(6)));

        // 5. Execute M9: Nutrition Analysis
        mockMvc.perform(post("/api/analysis/" + sessionId + "/nutrition")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings", hasSize(greaterThanOrEqualTo(3))));

        // 6. Execute M10: Food Awareness Assessment synthesis and retrieve API response
        MvcResult assessmentResult = mockMvc.perform(get("/api/analysis/" + sessionId + "/assessment")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Verify core assessment fields
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.productCategory").value("HUMAN_FOOD"))
                .andExpect(jsonPath("$.humanConsumptionStatus").value("HUMAN_FOOD"))
                .andExpect(jsonPath("$.overallScore", allOf(greaterThanOrEqualTo(0), lessThanOrEqualTo(100))))
                .andExpect(jsonPath("$.overallStatus").isNotEmpty())
                .andExpect(jsonPath("$.scoreBreakdown").isArray())
                // Verify real M8 items in API response
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.items[0].normalizedName").value("Wheat Flour"))
                .andExpect(jsonPath("$.items[0].riskLevel").value("POSITIVE"))
                .andExpect(jsonPath("$.items[0].summary").isNotEmpty())
                .andExpect(jsonPath("$.items[1].normalizedName").value("Rolled Oats"))
                .andExpect(jsonPath("$.items[1].riskLevel").value("POSITIVE"))
                .andExpect(jsonPath("$.items[2].normalizedName").value("Sugar"))
                .andExpect(jsonPath("$.items[2].riskLevel").value("NO_CONCERN"))
                .andExpect(jsonPath("$.items[3].normalizedName").value("Palm Oil"))
                .andExpect(jsonPath("$.items[3].riskLevel").value("LOW_ATTENTION"))
                .andExpect(jsonPath("$.items[4].normalizedName").value("Citric Acid"))
                .andExpect(jsonPath("$.items[4].additiveCode").value("INS 330"))
                .andExpect(jsonPath("$.items[4].regulatoryStatus").value("PERMITTED"))
                .andExpect(jsonPath("$.items[4].riskLevel").value("NO_CONCERN"))
                // Verify nutrition findings
                .andExpect(jsonPath("$.nutritionSummary.findings").isArray())
                // Verify 3 demographic age awareness groups
                .andExpect(jsonPath("$.ageGroupAwareness", hasSize(3)))
                .andExpect(jsonPath("$.ageGroupAwareness[0].ageGroup").value("CHILDREN"))
                .andExpect(jsonPath("$.ageGroupAwareness[0].attentionLevel").value("HIGHER_ATTENTION"))
                .andExpect(jsonPath("$.ageGroupAwareness[0].contributingFactors", hasItem(containsString("sugars"))))
                .andExpect(jsonPath("$.ageGroupAwareness[0].sourceIds", hasItem("WHO")))
                .andExpect(jsonPath("$.ageGroupAwareness[1].ageGroup").value("ADULTS"))
                .andExpect(jsonPath("$.ageGroupAwareness[1].attentionLevel").value("MODERATE_ATTENTION"))
                .andExpect(jsonPath("$.ageGroupAwareness[1].contributingFactors", hasItem(containsString("Saturated"))))
                .andExpect(jsonPath("$.ageGroupAwareness[2].ageGroup").value("OLDER_ADULTS"))
                .andExpect(jsonPath("$.ageGroupAwareness[2].sourceIds").isNotEmpty())
                .andReturn();

        String responseBody = assessmentResult.getResponse().getContentAsString();

        // 7. Verify absolute absence of demo data
        assertFalse(responseBody.contains("demo-sample-crisps"), "Response must not contain demo sessionId");
        assertFalse(responseBody.contains("DEMO_INGREDIENTS_LIST"), "Response must not contain DEMO_INGREDIENTS_LIST");
        assertFalse(responseBody.contains("demo-pet-food"), "Response must not contain demo pet food");
        assertFalse(responseBody.contains("demo-non-food"), "Response must not contain demo non food");
        assertFalse(responseBody.contains("Potatoes (Agricultural Produce)"), "Response must not contain sample potato crisps produce");
    }
}
