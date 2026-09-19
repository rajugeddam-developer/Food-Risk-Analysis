package com.foodrisk.risk;

import java.util.List;
import java.util.Optional;

/**
 * Contract for accessing verified ingredient and additive risk rules.
 *
 * Prepares the system for M12 Redis caching without leaking persistence mechanisms to callers.
 */
public interface RiskRuleRepository {

    /**
     * Looks up an additive rule by canonical code, code variant (e.g. INS 330, INS330, E330),
     * or known chemical synonym.
     */
    Optional<AdditiveRuleDefinition> findAdditiveByCodeOrSynonym(String query);

    /**
     * Looks up an ingredient rule by canonical name or common commercial alias.
     */
    Optional<IngredientRuleDefinition> findIngredientRule(String ingredientName);

    /**
     * Retrieves source metadata by identifier (e.g. FSSAI, WHO, CODEX).
     */
    Optional<SourceDefinition> findSourceById(String sourceId);

    /**
     * Returns all loaded additive rules.
     */
    List<AdditiveRuleDefinition> getAllAdditives();

    /**
     * Returns all loaded ingredient rules.
     */
    List<IngredientRuleDefinition> getAllIngredientRules();
}
