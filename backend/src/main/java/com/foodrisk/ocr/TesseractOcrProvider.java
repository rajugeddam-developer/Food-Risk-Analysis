package com.foodrisk.ocr;

import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Offline Tesseract OCR implementation using Tess4J.
 *
 * Strict requirements:
 * - Operates entirely offline; verifies traineddata on startup and fails fast if missing.
 * - Instantiates Tesseract safely per execution to ensure thread safety.
 * - Extracts raw text faithfully without artificial corrections (no M6 normalization here).
 * - Tracks execution duration in milliseconds.
 */
@Component
public class TesseractOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrProvider.class);

    private final String dataPath;
    private final String language;

    public TesseractOcrProvider(
            @Value("${ocr.tesseract.data-path:tessdata}") String dataPath,
            @Value("${ocr.tesseract.language:eng}") String language
    ) {
        this.dataPath = dataPath;
        this.language = language;
    }

    @PostConstruct
    public void verifyTessData() {
        File dir = new File(dataPath);
        File trainedData = new File(dir, language + ".traineddata");

        if (!trainedData.exists() || !trainedData.isFile()) {
            String errorMsg = String.format(
                    "Tesseract traineddata file missing at: %s. Local language data is required for offline OCR. Do not attempt internet downloads.",
                    trainedData.getAbsolutePath()
            );
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        log.info("Initialized local offline Tesseract OCR provider with data-path '{}' and language '{}' ({})",
                dir.getAbsolutePath(), language, trainedData.getName());
    }

    @Override
    public OcrResult extractText(File imageFile, OcrLabelType labelType) {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file for OCR does not exist: " + (imageFile != null ? imageFile.getAbsolutePath() : "null"));
        }

        long startTime = System.currentTimeMillis();

        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(new File(dataPath).getAbsolutePath());
        tesseract.setLanguage(language);

        String rawText;
        Float confidence = null;

        try {
            rawText = tesseract.doOCR(imageFile);

            // Compute confidence if available from words
            try {
                BufferedImage image = ImageIO.read(imageFile);
                if (image != null) {
                    List<Word> words = tesseract.getWords(image, 3); // 3 = RIL_WORD in TessPageIteratorLevel
                    if (words != null && !words.isEmpty()) {
                        float sum = 0;
                        int count = 0;
                        for (Word w : words) {
                            if (w.getConfidence() > 0) {
                                sum += w.getConfidence();
                                count++;
                            }
                        }
                        if (count > 0) {
                            confidence = sum / count;
                        }
                    }
                }
            } catch (Exception e) {
                // If confidence calculation is unavailable, keep confidence as null (do not fabricate)
                log.debug("Confidence calculation omitted: {}", e.getMessage());
            }

        } catch (TesseractException e) {
            log.error("Tesseract OCR execution failed for labelType {}: {}", labelType, e.getMessage());
            throw new RuntimeException("OCR extraction failed: " + e.getMessage(), e);
        }

        long processingTimeMs = System.currentTimeMillis() - startTime;

        log.info("OCR completed for labelType {} in {} ms. Text length: {} chars.",
                labelType, processingTimeMs, rawText != null ? rawText.length() : 0);

        return new OcrResult(rawText != null ? rawText.trim() : "", confidence, processingTimeMs);
    }
}
