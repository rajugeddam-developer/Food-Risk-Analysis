package com.foodrisk.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import java.io.InputStream;

/**
 * JSON-backed repository loading verified Food Awareness scoring configuration from scoring-rules.json.
 */
@Repository
public class JsonScoringRuleRepository implements ScoringRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonScoringRuleRepository.class);
    private static final String PRIMARY_SCORING_RULES_PATH = "classpath:data/food-risk-knowledge/scoring-rules.json";
    private static final String FALLBACK_SCORING_RULES_PATH = "classpath:data/scoring-rules.json";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private ScoringRuleDefinition scoringRules;

    public JsonScoringRuleRepository(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        try {
            Resource resource = resourceLoader.getResource(PRIMARY_SCORING_RULES_PATH);
            if (!resource.exists()) {
                resource = resourceLoader.getResource(FALLBACK_SCORING_RULES_PATH);
            }
            try (InputStream is = resource.getInputStream()) {
                this.scoringRules = objectMapper.readValue(is, ScoringRuleDefinition.class);
                log.info("Loaded Food Awareness scoring rules (version {}) from {}", scoringRules.scoringRuleVersion(), resource.getFilename());
            }
        } catch (Exception e) {
            log.error("Failed to load scoring rules from {} or {}", PRIMARY_SCORING_RULES_PATH, FALLBACK_SCORING_RULES_PATH, e);
            throw new IllegalStateException("Failed to load scoring rules", e);
        }
    }

    @Override
    public ScoringRuleDefinition getScoringRules() {
        return scoringRules;
    }

    @Override
    public String getScoringRuleVersion() {
        return scoringRules != null ? scoringRules.scoringRuleVersion() : "UNKNOWN";
    }
}
