package com.foodrisk.ocr;

import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Offline Tesseract OCR implementation using Tess4J.
 *
 * Implements an efficient, multi-pass OCR extraction strategy:
 * - Pass 1: Contrast-enhanced grayscale + text-region focusing + bicubic upscaling.
 * - Early return: If Pass 1 produces sufficient confidence and text length, Pass 1 is accepted immediately.
 * - Pass 2: Fallback to global Otsu binarization only if Pass 1 is insufficient (low confidence or short text).
 * - Multi-pass selection: Selects the superior pass using a composite score (confidence, readable ratio, keyword density)
 *   rather than raw character count alone.
 * - Ephemeral file cleanup guaranteed in finally blocks.
 */
@Component
public class TesseractOcrProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrProvider.class);

    private final String dataPath;
    private final String language;
    private final ImagePreprocessor preprocessor;
    private final OcrQualityThresholds thresholds;

    @Autowired
    public TesseractOcrProvider(
            @Value("${ocr.tesseract.data-path:tessdata}") String dataPath,
            @Value("${ocr.tesseract.language:eng}") String language,
            @Autowired(required = false) ImagePreprocessor preprocessor,
            @Autowired(required = false) OcrQualityThresholds thresholds
    ) {
        this.dataPath = dataPath;
        this.language = language;
        this.preprocessor = preprocessor != null ? preprocessor : new ImagePreprocessor();
        this.thresholds = thresholds != null ? thresholds : new OcrQualityThresholds();
    }

    public TesseractOcrProvider(
            String dataPath,
            String language,
            ImagePreprocessor preprocessor
    ) {
        this(dataPath, language, preprocessor, new OcrQualityThresholds());
    }

    public TesseractOcrProvider(
            String dataPath,
            String language
    ) {
        this(dataPath, language, new ImagePreprocessor(), new OcrQualityThresholds());
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

        File prepFile1 = null;
        File prepFile2 = null;

        String bestText = "";
        Float bestConfidence = null;

        try {
            // Pass 1: Contrast-enhanced + region-focused + bicubic upscaled grayscale
            boolean pass1Ready = false;
            try {
                prepFile1 = File.createTempFile("ocr_prep1_" + java.util.UUID.randomUUID() + "_", ".png");
                pass1Ready = preprocessor != null && preprocessor.preprocess(
                        imageFile, prepFile1, ImagePreprocessor.PreprocessingMode.ENHANCED_GRAYSCALE
                );
            } catch (Exception e) {
                log.debug("Preprocessing Pass 1 setup skipped: {}", e.getMessage());
            }

            File targetFile1 = (pass1Ready && prepFile1 != null && prepFile1.exists()) ? prepFile1 : imageFile;
            PassResult pass1Result = executePass(tesseract, targetFile1, labelType);
            bestText = pass1Result.text;
            bestConfidence = pass1Result.confidence;

            // Evaluate if Pass 1 is already sufficient
            boolean isPass1Sufficient = (bestConfidence != null && bestConfidence >= thresholds.getPass2TriggerConfidence())
                    && (bestText != null && bestText.trim().length() >= thresholds.getPass2TriggerMinChars());

            // Pass 2: Fallback to global Otsu thresholding only if Pass 1 was insufficient
            if (!isPass1Sufficient && preprocessor != null) {
                try {
                    prepFile2 = File.createTempFile("ocr_prep2_" + java.util.UUID.randomUUID() + "_", ".png");
                    boolean pass2Ready = preprocessor.preprocess(
                            imageFile, prepFile2, ImagePreprocessor.PreprocessingMode.GLOBAL_OTSU
                    );
                    if (pass2Ready && prepFile2.exists()) {
                        PassResult pass2Result = executePass(tesseract, prepFile2, labelType);
                        if (isBetterPass(pass2Result, pass1Result)) {
                            log.debug("Pass 2 (global Otsu thresholding) produced superior OCR result for {}", labelType);
                            bestText = pass2Result.text;
                            bestConfidence = pass2Result.confidence;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Preprocessing Pass 2 omitted: {}", e.getMessage());
                }
            }

        } finally {
            // Privacy guarantee: ensure all ephemeral preprocessed copies are strictly deleted
            if (prepFile1 != null && prepFile1.exists()) {
                prepFile1.delete();
            }
            if (prepFile2 != null && prepFile2.exists()) {
                prepFile2.delete();
            }
        }

        long processingTimeMs = System.currentTimeMillis() - startTime;

        log.info("OCR completed for labelType {} in {} ms. Text length: {} chars, confidence: {}.",
                labelType, processingTimeMs, bestText != null ? bestText.length() : 0, bestConfidence);

        return new OcrResult(bestText != null ? bestText.trim() : "", bestConfidence, processingTimeMs);
    }

    private record PassResult(String text, Float confidence) {}

    private PassResult executePass(ITesseract tesseract, File file, OcrLabelType labelType) {
        String rawText = "";
        Float confidence = null;

        try {
            rawText = tesseract.doOCR(file);

            // Compute confidence if available from words
            try {
                BufferedImage image = ImageIO.read(file);
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
                log.debug("Confidence calculation omitted: {}", e.getMessage());
            }

        } catch (TesseractException e) {
            log.error("Tesseract OCR execution failed for labelType {}: {}", labelType, e.getMessage());
            throw new RuntimeException("OCR extraction failed: " + e.getMessage(), e);
        }

        return new PassResult(rawText != null ? rawText.trim() : "", confidence);
    }

    /**
     * Determines if candidate pass is superior to current pass.
     * Considers composite score of confidence, readable ratio, and text length.
     * Does NOT select based only on raw character count.
     */
    private boolean isBetterPass(PassResult candidate, PassResult current) {
        if (candidate == null || candidate.text.isBlank()) {
            return false;
        }
        if (current == null || current.text.isBlank()) {
            return true;
        }

        double candScore = computeCompositeScore(candidate);
        double currScore = computeCompositeScore(current);

        return candScore > currScore * 1.10; // Must be at least 10% better
    }

    private double computeCompositeScore(PassResult pass) {
        String text = pass.text;
        double conf = pass.confidence != null ? pass.confidence : 50.0;
        int len = Math.min(500, text.length());

        // Calculate readable ratio
        int readable = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ',' || c == '.' || c == ';' || c == ':') {
                readable++;
            }
        }
        double readableRatio = text.length() > 0 ? (double) readable / text.length() : 0;

        // Composite: confidence (40%) + readable ratio (40%) + length factor (20%)
        return (conf * 0.40) + (readableRatio * 100.0 * 0.40) + ((len / 500.0) * 100.0 * 0.20);
    }
}
