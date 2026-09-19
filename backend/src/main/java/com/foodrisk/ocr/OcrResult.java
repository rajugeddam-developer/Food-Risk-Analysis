package com.foodrisk.ocr;

/**
 * Result of an OCR extraction pass on an image.
 *
 * @param text raw extracted text from OCR engine (never silently corrected or altered in M5)
 * @param confidence average confidence score (0.0 - 100.0) or null if reliable confidence is unavailable
 * @param processingTimeMs elapsed processing time in milliseconds
 */
public record OcrResult(
        String text,
        Float confidence,
        long processingTimeMs
) {}
