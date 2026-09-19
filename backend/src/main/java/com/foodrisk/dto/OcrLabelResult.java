package com.foodrisk.dto;

/**
 * Encapsulates raw OCR output for a single food packaging surface (ingredients or nutrition).
 *
 * @param rawText extracted raw text (null if image was omitted/missing)
 * @param confidence confidence score or null if unavailable
 * @param processingTimeMs time taken to perform OCR
 * @param present indicates whether this packaging surface was provided in the request
 */
public record OcrLabelResult(
        String rawText,
        Float confidence,
        long processingTimeMs,
        boolean present
) {
    public static OcrLabelResult missing() {
        return new OcrLabelResult(null, null, 0L, false);
    }
}
