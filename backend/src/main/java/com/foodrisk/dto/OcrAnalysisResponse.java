package com.foodrisk.dto;

import java.util.UUID;

/**
 * Encapsulates the response of an OCR processing run on an analysis session.
 *
 * Preserves raw extracted OCR text and processing metadata without altering or normalizing contents.
 */
public record OcrAnalysisResponse(
        UUID sessionId,
        String status,
        OcrLabelResult ingredients,
        OcrLabelResult nutrition,
        long totalProcessingTimeMs
) {}
