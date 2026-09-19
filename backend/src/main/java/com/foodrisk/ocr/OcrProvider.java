package com.foodrisk.ocr;

import java.io.File;

/**
 * Strategy interface for OCR extraction providers.
 */
public interface OcrProvider {

    /**
     * Extracts raw text from the specified temporary image file.
     *
     * @param imageFile temporary image file on local filesystem
     * @param labelType packaging label type (INGREDIENTS or NUTRITION)
     * @return OcrResult containing raw extracted text, optional confidence, and processing time
     */
    OcrResult extractText(File imageFile, OcrLabelType labelType);
}
