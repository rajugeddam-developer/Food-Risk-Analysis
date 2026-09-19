package com.foodrisk.scoring;

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
import java.util.Locale;
import java.util.Map;

/**
 * Loads authoritative demographic guidance rules from classpath JSON.
 */
@Repository
public class JsonAgeGuidanceRepository implements AgeGuidanceRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonAgeGuidanceRepository.class);
    private static final String PRIMARY_PATH = "classpath:data/food-risk-knowledge/age-guidance.json";
    private static final String FALLBACK_PATH = "classpath:data/age-guidance.json";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private final List<AgeGuidanceRuleDefinition> allRules = new ArrayList<>();
    private final Map<AgeGroup, List<AgeGuidanceRuleDefinition>> rulesByGroup = new EnumMap<>(AgeGroup.class);

    public JsonAgeGuidanceRepository(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    public JsonAgeGuidanceRepository() {
        this(new org.springframework.core.io.DefaultResourceLoader(), new ObjectMapper());
        initialize();
    }

    @PostConstruct
    public void initialize() {
        try {
            Resource resource = resourceLoader.getResource(PRIMARY_PATH);
            if (!resource.exists()) {
                resource = resourceLoader.getResource(FALLBACK_PATH);
            }
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    List<AgeGuidanceRuleDefinition> list = objectMapper.readValue(is, new TypeReference<>() {});
                    allRules.addAll(list);
                    for (AgeGuidanceRuleDefinition rule : list) {
                        if (rule.ageGroup() != null) {
                            try {
                                AgeGroup group = AgeGroup.valueOf(rule.ageGroup().toUpperCase(Locale.ROOT));
                                rulesByGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(rule);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
                log.info("Loaded {} demographic age-guidance rules from {}", allRules.size(), resource.getFilename());
            } else {
                log.warn("age-guidance.json not found on classpath");
            }
        } catch (Exception e) {
            log.error("Failed to load age-guidance.json: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<AgeGuidanceRuleDefinition> getRulesForAgeGroup(AgeGroup ageGroup) {
        if (ageGroup == null) return List.of();
        return rulesByGroup.getOrDefault(ageGroup, List.of());
    }

    @Override
    public List<AgeGuidanceRuleDefinition> getAllRules() {
        return Collections.unmodifiableList(allRules);
    }
}
