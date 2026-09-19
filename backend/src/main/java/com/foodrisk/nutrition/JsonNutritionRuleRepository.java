package com.foodrisk.nutrition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory repository loading verified nutrition reference rules from classpath JSON.
 */
@Repository
public class JsonNutritionRuleRepository implements NutritionRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonNutritionRuleRepository.class);
    private static final String REFERENCE_VERSION = "2026.09";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private final List<NutritionRuleDefinition> allRules = new ArrayList<>();
    private final Map<NutrientType, List<NutritionRuleDefinition>> ruleIndex = new EnumMap<>(NutrientType.class);

    public JsonNutritionRuleRepository(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        try {
            Resource res = resourceLoader.getResource("classpath:data/food-risk-knowledge/nutrition.json");
            if (!res.exists()) {
                res = resourceLoader.getResource("classpath:data/food-risk-knowledge/nutrition-rules.json");
            }
            if (!res.exists()) {
                res = resourceLoader.getResource("classpath:data/nutrition-rules.json");
            }
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    List<NutritionRuleDefinition> list = objectMapper.readValue(is, new TypeReference<>() {});
                    allRules.addAll(list);

                    for (NutritionRuleDefinition rule : list) {
                        if (rule.nutrient() != null) {
                            ruleIndex.computeIfAbsent(rule.nutrient(), k -> new ArrayList<>()).add(rule);
                        }
                    }

                    // Sort rules for each nutrient by threshold descending (highest first)
                    for (List<NutritionRuleDefinition> rules : ruleIndex.values()) {
                        rules.sort((r1, r2) -> {
                            if (r1.threshold() == null) return 1;
                            if (r2.threshold() == null) return -1;
                            return r2.threshold().compareTo(r1.threshold());
                        });
                    }
                }
                log.info("Loaded and indexed {} verified nutrition reference rules (version {})", allRules.size(), REFERENCE_VERSION);
            } else {
                log.warn("nutrition-rules.json not found on classpath");
            }
        } catch (Exception e) {
            log.error("Failed to load nutrition-rules.json: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<NutritionRuleDefinition> getRulesForNutrient(NutrientType nutrient) {
        if (nutrient == null) return List.of();
        return ruleIndex.getOrDefault(nutrient, List.of());
    }

    @Override
    public List<NutritionRuleDefinition> getAllRules() {
        return Collections.unmodifiableList(allRules);
    }

    @Override
    public String getReferenceVersion() {
        return REFERENCE_VERSION;
    }
}
