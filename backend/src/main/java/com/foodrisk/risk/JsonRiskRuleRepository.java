package com.foodrisk.risk;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * File-backed repository implementation loading verified regulatory rules from classpath JSON datasets.
 */
@Repository
public class JsonRiskRuleRepository implements RiskRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonRiskRuleRepository.class);

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private final List<SourceDefinition> sources = new ArrayList<>();
    private final Map<String, SourceDefinition> sourceMap = new ConcurrentHashMap<>();

    private final List<AdditiveRuleDefinition> additives = new ArrayList<>();
    private final Map<String, AdditiveRuleDefinition> additiveIndex = new ConcurrentHashMap<>();

    private final List<IngredientRuleDefinition> ingredientRules = new ArrayList<>();
    private final Map<String, IngredientRuleDefinition> ingredientRuleIndex = new ConcurrentHashMap<>();

    public JsonRiskRuleRepository(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        loadSources();
        loadAdditives();
        loadIngredientRules();
    }

    private Resource resolveResource(String primaryPath, String fallbackPath) {
        Resource primary = resourceLoader.getResource(primaryPath);
        if (primary.exists()) {
            return primary;
        }
        Resource fallback = resourceLoader.getResource(fallbackPath);
        if (fallback.exists()) {
            return fallback;
        }
        return primary;
    }

    private void loadSources() {
        try {
            Resource res = resolveResource("classpath:data/food-risk-knowledge/sources.json", "classpath:data/sources.json");
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    List<SourceDefinition> list = objectMapper.readValue(is, new TypeReference<>() {});
                    sources.addAll(list);
                    for (SourceDefinition s : list) {
                        if (s.id() != null) {
                            sourceMap.put(s.id().toUpperCase(Locale.ROOT), s);
                        }
                    }
                }
                log.info("Loaded {} authoritative data source definitions from {}", sources.size(), res.getFilename());
            } else {
                log.warn("sources.json not found on classpath");
            }
        } catch (Exception e) {
            log.error("Failed to load sources.json: {}", e.getMessage());
        }
    }

    private void loadAdditives() {
        try {
            Resource res = resolveResource("classpath:data/food-risk-knowledge/additives.json", "classpath:data/additives.json");
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    List<AdditiveRuleDefinition> list = objectMapper.readValue(is, new TypeReference<>() {});
                    additives.addAll(list);
                    for (AdditiveRuleDefinition a : list) {
                        indexAdditive(a);
                    }
                }
                log.info("Loaded and indexed {} verified additive definitions from {}", additives.size(), res.getFilename());
            } else {
                log.warn("additives.json not found on classpath");
            }
        } catch (Exception e) {
            log.error("Failed to load additives.json: {}", e.getMessage());
        }
    }

    private void indexAdditive(AdditiveRuleDefinition a) {
        if (a.code() != null) {
            additiveIndex.put(normalizeKey(a.code()), a);
        }
        if (a.name() != null) {
            additiveIndex.put(normalizeKey(a.name()), a);
        }
        if (a.synonyms() != null) {
            for (String syn : a.synonyms()) {
                additiveIndex.put(normalizeKey(syn), a);
            }
        }
    }

    private void loadIngredientRules() {
        try {
            Resource res = resourceLoader.getResource("classpath:data/food-risk-knowledge/ingredients.json");
            if (!res.exists()) {
                res = resourceLoader.getResource("classpath:data/food-risk-knowledge/ingredient-rules.json");
            }
            if (!res.exists()) {
                res = resourceLoader.getResource("classpath:data/ingredient-rules.json");
            }
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    List<IngredientRuleDefinition> list = objectMapper.readValue(is, new TypeReference<>() {});
                    ingredientRules.addAll(list);
                    for (IngredientRuleDefinition r : list) {
                        indexIngredientRule(r);
                    }
                }
                log.info("Loaded and indexed {} verified ingredient definitions from {}", ingredientRules.size(), res.getFilename());
            } else {
                log.warn("ingredients.json / ingredient-rules.json not found on classpath");
            }
        } catch (Exception e) {
            log.error("Failed to load ingredient definitions: {}", e.getMessage());
        }
    }

    private void indexIngredientRule(IngredientRuleDefinition r) {
        if (r.canonicalName() != null) {
            ingredientRuleIndex.put(normalizeKey(r.canonicalName()), r);
        }
        if (r.aliases() != null) {
            for (String alias : r.aliases()) {
                ingredientRuleIndex.put(normalizeKey(alias), r);
            }
        }
    }

    @Override
    public Optional<AdditiveRuleDefinition> findAdditiveByCodeOrSynonym(String query) {
        if (query == null || query.isBlank()) return Optional.empty();

        // Reject ambiguous OCR patterns (e.g. "INS 3?0", "E??0")
        if (query.contains("?") || query.contains("*") || query.contains("~")) {
            return Optional.empty();
        }

        String key = normalizeKey(query);
        AdditiveRuleDefinition match = additiveIndex.get(key);
        if (match != null) {
            return Optional.of(match);
        }

        // Token / word-boundary matching on code, name, or synonyms (e.g. "Acidity Regulator (INS 330)" or "Emulsifier (Soy Lecithin)")
        for (AdditiveRuleDefinition def : additives) {
            if (def.code() != null && matchesWordBoundary(query, def.code())) {
                return Optional.of(def);
            }
            if (def.name() != null && matchesWordBoundary(query, def.name())) {
                return Optional.of(def);
            }
            if (def.synonyms() != null) {
                for (String syn : def.synonyms()) {
                    if (matchesWordBoundary(query, syn)) {
                        return Optional.of(def);
                    }
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<IngredientRuleDefinition> findIngredientRule(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) return Optional.empty();

        if (ingredientName.contains("?") || ingredientName.contains("*")) {
            return Optional.empty();
        }

        String key = normalizeKey(ingredientName);
        IngredientRuleDefinition directMatch = ingredientRuleIndex.get(key);
        if (directMatch != null) {
            return Optional.of(directMatch);
        }

        // Token / word-boundary matching on canonical name or aliases to prevent false positives (e.g. "Watermelon" != "Water", "Buckwheat" != "Wheat")
        for (IngredientRuleDefinition def : ingredientRules) {
            if (def.canonicalName() != null && matchesWordBoundary(ingredientName, def.canonicalName())) {
                return Optional.of(def);
            }
            if (def.aliases() != null) {
                for (String alias : def.aliases()) {
                    if (matchesWordBoundary(ingredientName, alias)) {
                        return Optional.of(def);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean matchesWordBoundary(String query, String target) {
        if (query == null || target == null || target.isBlank()) return false;
        String trimmed = target.trim();
        if (trimmed.length() < 3) return false;

        // Case-insensitive word-boundary matching bounded by non-alphanumerics
        String regex = "(?i)(^|[^a-zA-Z0-9])" + Pattern.quote(trimmed) + "([^a-zA-Z0-9]|$)";
        return Pattern.compile(regex).matcher(query).find();
    }

    @Override
    public Optional<SourceDefinition> findSourceById(String sourceId) {
        if (sourceId == null) return Optional.empty();
        return Optional.ofNullable(sourceMap.get(sourceId.toUpperCase(Locale.ROOT)));
    }

    @Override
    public List<AdditiveRuleDefinition> getAllAdditives() {
        return Collections.unmodifiableList(additives);
    }

    @Override
    public List<IngredientRuleDefinition> getAllIngredientRules() {
        return Collections.unmodifiableList(ingredientRules);
    }

    private String normalizeKey(String s) {
        if (s == null) return "";
        return s.toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace(".", "")
                .trim();
    }
}
