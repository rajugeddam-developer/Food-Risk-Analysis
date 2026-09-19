package com.foodrisk.ocr;

import com.foodrisk.exception.ErrorCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Evaluates food-packaging image quality prior to OCR extraction.
 *
 * Core Principle:
 * Assesses whether relevant food-label information can be reliably extracted,
 * rather than demanding global photographic perfection. Uses localized regional/tile
 * analysis to ensure high-resolution packaging photos with small, sharp text boxes
 * are not falsely rejected due to large, uniform packaging backgrounds.
 */
@Component
public class ImageQualityAssessor {

    private static final Logger log = LoggerFactory.getLogger(ImageQualityAssessor.class);

    private final ImageQualityThresholds thresholds;

    @Autowired
    public ImageQualityAssessor(ImageQualityThresholds thresholds) {
        this.thresholds = thresholds != null ? thresholds : new ImageQualityThresholds();
    }

    public ImageQualityAssessor() {
        this(new ImageQualityThresholds());
    }

    public enum QualityIssue {
        OK(null),
        TOO_LOW_RESOLUTION(ErrorCategory.INVALID_IMAGE),
        BLURRY(ErrorCategory.IMAGE_UNCLEAR),
        LOW_CONTRAST(ErrorCategory.IMAGE_UNCLEAR),
        UNREADABLE(ErrorCategory.INVALID_IMAGE);

        private final ErrorCategory category;

        QualityIssue(ErrorCategory category) {
            this.category = category;
        }

        public ErrorCategory getCategory() {
            return category;
        }
    }

    public record ImageQualityResult(
            boolean isUsable,
            QualityIssue issue,
            String userGuidance,
            double blurScore,
            double contrastScore,
            int width,
            int height
    ) {
        public static ImageQualityResult ok(double blurScore, double contrastScore, int width, int height) {
            return new ImageQualityResult(true, QualityIssue.OK, null, blurScore, contrastScore, width, height);
        }

        public static ImageQualityResult reject(QualityIssue issue, String guidance, double blurScore, double contrastScore, int width, int height) {
            return new ImageQualityResult(false, issue, guidance, blurScore, contrastScore, width, height);
        }
    }

    public ImageQualityResult assess(byte[] imageBytes, OcrLabelType labelType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ImageQualityResult.reject(
                    QualityIssue.UNREADABLE,
                    "Uploaded image file is empty or missing. Please select a valid photo.",
                    0, 0, 0, 0
            );
        }

        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            log.warn("Failed to decode image for quality assessment: {}", e.getMessage());
            return ImageQualityResult.reject(
                    QualityIssue.UNREADABLE,
                    "Unable to read the image format. Please capture a clear JPEG, PNG, or WebP photo.",
                    0, 0, 0, 0
            );
        }

        if (img == null) {
            return ImageQualityResult.reject(
                    QualityIssue.UNREADABLE,
                    "Unable to read the image format. Please capture a clear JPEG, PNG, or WebP photo.",
                    0, 0, 0, 0
            );
        }

        return assess(img, labelType);
    }

    public ImageQualityResult assess(BufferedImage img, OcrLabelType labelType) {
        int width = img.getWidth();
        int height = img.getHeight();

        // 1. Resolution & Technical Dimension Check
        if (width < thresholds.getMinWidth() || height < thresholds.getMinHeight()
                || (long) width * height < thresholds.getMinPixels()) {
            String labelDesc = labelType == OcrLabelType.INGREDIENTS ? "ingredients list" : "nutrition table";
            String guidance = String.format(
                    "The photo resolution is too low (%dx%d) to read the %s. Please capture a closer, higher-resolution photo.",
                    width, height, labelDesc
            );
            log.info("Image rejected for {}: resolution {}x{} below technical minimum ({}x{})",
                    labelType, width, height, thresholds.getMinWidth(), thresholds.getMinHeight());
            return ImageQualityResult.reject(QualityIssue.TOO_LOW_RESOLUTION, guidance, 0, 0, width, height);
        }

        // 2. Localized Regional / Tile-Based Evaluation
        // Divide the image into a grid of tiles to detect localized label text
        int gridSize = Math.max(2, thresholds.getTileGridSize());
        int tileW = width / gridSize;
        int tileH = height / gridSize;

        double maxTileBlur = 0.0;
        double maxTileContrast = 0.0;
        double totalBlur = 0.0;
        double totalContrast = 0.0;
        int activeTiles = 0;

        for (int gy = 0; gy < gridSize; gy++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int startX = gx * tileW;
                int startY = gy * tileH;
                int endX = (gx == gridSize - 1) ? width : startX + tileW;
                int endY = (gy == gridSize - 1) ? height : startY + tileH;

                TileMetrics tm = computeTileMetrics(img, startX, startY, endX, endY);
                if (tm != null) {
                    if (tm.blurVar > maxTileBlur) {
                        maxTileBlur = tm.blurVar;
                    }
                    if (tm.contrastStdDev > maxTileContrast) {
                        maxTileContrast = tm.contrastStdDev;
                    }
                    totalBlur += tm.blurVar;
                    totalContrast += tm.contrastStdDev;
                    activeTiles++;
                }
            }
        }

        double avgBlur = activeTiles > 0 ? totalBlur / activeTiles : 0;
        double avgContrast = activeTiles > 0 ? totalContrast / activeTiles : 0;

        // Effective score: prioritize the best readable tile region (70% best region + 30% average)
        // This ensures high-resolution packaging with small sharp text is NOT rejected!
        double effectiveBlur = (0.70 * maxTileBlur) + (0.30 * avgBlur);
        double effectiveContrast = (0.70 * maxTileContrast) + (0.30 * avgContrast);

        // 3. Contrast Evaluation
        if (maxTileContrast < thresholds.getMinContrastStdDev() && effectiveContrast < thresholds.getMinContrastStdDev()) {
            String guidance = "We couldn't read the food label clearly. Please retake the photo with the text in focus and good lighting.";
            log.info("Image rejected for {}: contrast {} < threshold {}", labelType, maxTileContrast, thresholds.getMinContrastStdDev());
            return ImageQualityResult.reject(QualityIssue.LOW_CONTRAST, guidance, effectiveBlur, effectiveContrast, width, height);
        }

        // 4. Blur / Focus Evaluation via Laplacian Variance
        if (maxTileBlur < thresholds.getMinBlurLaplacianVar() && effectiveBlur < thresholds.getMinBlurLaplacianVar()) {
            String guidance = "We couldn't read the food label clearly. Please retake the photo with the text in focus and good lighting.";
            log.info("Image rejected for {}: blur variance {} < threshold {}", labelType, maxTileBlur, thresholds.getMinBlurLaplacianVar());
            return ImageQualityResult.reject(QualityIssue.BLURRY, guidance, effectiveBlur, effectiveContrast, width, height);
        }

        log.debug("Image quality OK for {}: {}x{}, effectiveBlur={}, maxTileBlur={}, effectiveContrast={}",
                labelType, width, height, effectiveBlur, maxTileBlur, effectiveContrast);
        return ImageQualityResult.ok(effectiveBlur, effectiveContrast, width, height);
    }

    private record TileMetrics(double contrastStdDev, double blurVar) {}

    private TileMetrics computeTileMetrics(BufferedImage img, int startX, int startY, int endX, int endY) {
        int w = endX - startX;
        int h = endY - startY;
        if (w < 4 || h < 4) {
            return null;
        }

        // Subsample large tiles to ensure evaluation finishes in < 15ms
        int step = Math.max(1, Math.min(w, h) / 100);

        double sumLuma = 0;
        double sumLumaSq = 0;
        int lumaCount = 0;

        for (int y = startY; y < endY; y += step) {
            for (int x = startX; x < endX; x += step) {
                double luma = getLuma(img, x, y);
                sumLuma += luma;
                sumLumaSq += luma * luma;
                lumaCount++;
            }
        }

        if (lumaCount == 0) return null;
        double meanLuma = sumLuma / lumaCount;
        double contrastStdDev = Math.sqrt(Math.max(0, (sumLumaSq / lumaCount) - (meanLuma * meanLuma)));

        // Compute discrete Laplacian variance within tile
        double sumLap = 0;
        double sumLapSq = 0;
        int lapCount = 0;

        for (int y = startY + 1; y < endY - 1; y += step) {
            for (int x = startX + 1; x < endX - 1; x += step) {
                double center = getLuma(img, x, y);
                double left = getLuma(img, x - 1, y);
                double right = getLuma(img, x + 1, y);
                double top = getLuma(img, x, y - 1);
                double bottom = getLuma(img, x, y + 1);

                double lap = left + right + top + bottom - (4.0 * center);
                sumLap += lap;
                sumLapSq += lap * lap;
                lapCount++;
            }
        }

        if (lapCount == 0) return null;
        double meanLap = sumLap / lapCount;
        double blurVar = Math.max(0, (sumLapSq / lapCount) - (meanLap * meanLap));

        return new TileMetrics(contrastStdDev, blurVar);
    }

    private static double getLuma(BufferedImage img, int x, int y) {
        int rgb = img.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }
}
