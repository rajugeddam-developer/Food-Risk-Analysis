package com.foodrisk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to explain a specific verified food finding via Gemini with factual guardrails")
public record ExplainFindingRequest(
        @NotBlank(message = "Item name is required")
        @Schema(description = "Exact name of the verified ingredient or nutrient finding to explain", example = "INS 551")
        String itemName,

        @Schema(description = "Optional finding type: INGREDIENT or NUTRIENT", example = "INGREDIENT")
        String itemType
) {}
