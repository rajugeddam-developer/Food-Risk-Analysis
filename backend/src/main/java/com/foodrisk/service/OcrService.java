package com.foodrisk.service;

import com.foodrisk.dto.OcrAnalysisResponse;
import com.foodrisk.dto.OcrLabelResult;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.InvalidImageException;
import com.foodrisk.ocr.BufferedImageInput;
import com.foodrisk.ocr.ImageQualityAssessor;
import com.foodrisk.ocr.ImageValidator;
import com.foodrisk.ocr.OcrExtractionValidator;
import com.foodrisk.ocr.OcrLabelType;
import com.foodrisk.ocr.OcrProvider;
import com.foodrisk.ocr.OcrResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Coordinates OCR extraction on transient food packaging images.
 *
 * Enforces Milestone M5 boundaries:
 * - OCR extraction only: extracts raw text faithfully without spelling fixes, risk logic, or normalization.
 * - Privacy: image payloads are written to ephemeral temporary files with randomized names and deleted in finally blocks.
 * - Permissive partial inputs: supports single image (ingredients OR nutrition) when one is omitted.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final ImageValidator imageValidator;
    private final OcrProvider ocrProvider;
    private final AnalysisSessionService sessionService;
    private final ImageQualityAssessor qualityAssessor;
    private final OcrExtractionValidator extractionValidator;

    @Autowired
    public OcrService(
            ImageValidator imageValidator,
            OcrProvider ocrProvider,
            AnalysisSessionService sessionService,
            @Autowired(required = false) ImageQualityAssessor qualityAssessor,
            @Autowired(required = false) OcrExtractionValidator extractionValidator
    ) {
        this.imageValidator = imageValidator;
        this.ocrProvider = ocrProvider;
        this.sessionService = sessionService;
        this.qualityAssessor = qualityAssessor != null ? qualityAssessor : new ImageQualityAssessor();
        this.extractionValidator = extractionValidator != null ? extractionValidator : new OcrExtractionValidator();
    }

    public OcrService(
            ImageValidator imageValidator,
            OcrProvider ocrProvider,
            AnalysisSessionService sessionService
    ) {
        this(imageValidator, ocrProvider, sessionService, new ImageQualityAssessor(), new OcrExtractionValidator());
    }

    /**
     * Executes OCR using immutable BufferedImageInput payloads.
     * Prevents servlet lifecycle temp-file recycling bugs across asynchronous thread boundaries.
     */
    /**
     * Executes OCR using immutable BufferedImageInput payloads.
     * Prevents servlet lifecycle temp-file recycling bugs across asynchronous thread boundaries.
     */
    public OcrAnalysisResponse processOcrPayloads(
            UUID sessionId,
            BufferedImageInput ingredientImage,
            BufferedImageInput nutritionImage
    ) {
        boolean hasIngredient = ingredientImage != null && !ingredientImage.isEmpty();
        boolean hasNutrition = nutritionImage != null && !nutritionImage.isEmpty();

        if (!hasIngredient && !hasNutrition) {
            throw new InvalidImageException("At least one packaging label image (ingredients or nutrition) must be provided.");
        }

        // Validate session is active and not expired
        FoodAnalysisSession session = sessionService.getActiveSession(sessionId);
        sessionService.updateStatus(session, AnalysisStatus.PROCESSING);

        long startAll = System.currentTimeMillis();
        OcrLabelResult ingredientResult = OcrLabelResult.missing();
        OcrLabelResult nutritionResult = OcrLabelResult.missing();

        try {
            // Process Ingredients Image if provided
            if (hasIngredient) {
                ingredientResult = extractLabel(
                        ingredientImage.bytes(),
                        ingredientImage.contentType(),
                        ingredientImage.originalFilename(),
                        OcrLabelType.INGREDIENTS
                );
            }

            // Process Nutrition Image if provided
            if (hasNutrition) {
                nutritionResult = extractLabel(
                        nutritionImage.bytes(),
                        nutritionImage.contentType(),
                        nutritionImage.originalFilename(),
                        OcrLabelType.NUTRITION
                );
            }

            long totalTime = System.currentTimeMillis() - startAll;
            log.info("Completed OCR processing for session {} in {} ms (ingredients: {}, nutrition: {})",
                    sessionId, totalTime, ingredientResult.present(), nutritionResult.present());

            return new OcrAnalysisResponse(
                    sessionId,
                    session.getStatus().name(),
                    ingredientResult,
                    nutritionResult,
                    totalTime
            );

        } catch (RuntimeException ex) {
            log.error("OCR execution failed for session {}: {}", sessionId, ex.getMessage());
            sessionService.updateStatus(session, AnalysisStatus.FAILED);
            throw ex;
        }
    }

    /**
     * Overload for MultipartFile inputs.
     */
    public OcrAnalysisResponse processOcr(
            UUID sessionId,
            MultipartFile ingredientImage,
            MultipartFile nutritionImage
    ) {
        BufferedImageInput ingInput = ingredientImage != null && !ingredientImage.isEmpty()
                ? BufferedImageInput.from(ingredientImage, imageValidator)
                : null;
        BufferedImageInput nutInput = nutritionImage != null && !nutritionImage.isEmpty()
                ? BufferedImageInput.from(nutritionImage, imageValidator)
                : null;

        return processOcrPayloads(sessionId, ingInput, nutInput);
    }

    private OcrLabelResult extractLabel(byte[] bytes, String contentType, String originalFilename, OcrLabelType labelType) {
        // 1. Validate image format, size, magic bytes
        imageValidator.validateBytes(bytes, contentType);

        // 2. Assess focus/blur, contrast, and resolution viability
        ImageQualityAssessor.ImageQualityResult quality = qualityAssessor.assess(bytes, labelType);
        if (!quality.isUsable()) {
            log.info("Image quality assessment failed for {}: {}", labelType, quality.issue());
            throw new InvalidImageException(quality.issue().getCategory(), quality.userGuidance());
        }

        // 3. Ephemeral temp file with cryptographically random UUID name
        File tempFile = null;
        try {
            String ext = switch (contentType != null ? contentType.toLowerCase() : "") {
                case "image/jpeg", "image/jpg" -> ".jpg";
                case "image/webp" -> ".webp";
                default -> ".png";
            };
            tempFile = File.createTempFile("ocr_ephemeral_" + UUID.randomUUID() + "_", ext);

            try (OutputStream out = new FileOutputStream(tempFile)) {
                out.write(bytes);
            }

            OcrResult ocrResult = ocrProvider.extractText(tempFile, labelType);

            return new OcrLabelResult(
                    ocrResult.text(),
                    ocrResult.confidence(),
                    ocrResult.processingTimeMs(),
                    true
            );

        } catch (IOException e) {
            log.error("Failed to handle temporary image file for {}: {}", labelType, e.getMessage());
            throw new RuntimeException("Image processing failed: " + e.getMessage(), e);
        } finally {
            // Privacy guarantee: Always delete ephemeral file immediately
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    tempFile.deleteOnExit();
                }
            }
        }
    }
}
