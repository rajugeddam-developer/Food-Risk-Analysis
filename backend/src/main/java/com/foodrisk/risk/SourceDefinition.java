package com.foodrisk.risk;

/**
 * Metadata defining an authoritative regulatory or health agency source.
 */
public record SourceDefinition(
        String id,
        String organization,
        String country,
        String sourceType,
        String version,
        String lastVerified,
        String reference
) {}
