package com.foodrisk.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Preprocesses packaging label images to maximize Tesseract OCR text recognition.
 *
 * Implements pure Java image enhancement (no OpenCV or native dependencies):
 * 1. Grayscale luminance conversion.
 * 2. Region-of-interest (ROI) text detection & cropping (focuses on the packaging label box).
 * 3. Bicubic upscaling (1.5x - 2.0x) for small packaging text glyphs.
 * 4. Contrast normalization (histogram dynamic range stretching with percentile clipping).
 * 5. Binarization:
 *    - Global Otsu thresholding (minimizes global intra-class variance).
 *    - Local adaptive window thresholding (handles uneven packaging glare and lighting).
 */
@Component
public class ImagePreprocessor {

    private static final Logger log = LoggerFactory.getLogger(ImagePreprocessor.class);

    public enum PreprocessingMode {
        ENHANCED_GRAYSCALE,
        GLOBAL_OTSU,
        LOCAL_ADAPTIVE
    }

    /**
     * Preprocesses an input image file and writes the enhanced image to an ephemeral output file.
     *
     * @param inputFile  raw input image
     * @param outputFile destination file
     * @param mode       preprocessing mode
     * @return true if preprocessing was successfully applied
     */
    public boolean preprocess(File inputFile, File outputFile, PreprocessingMode mode) {
        if (inputFile == null || !inputFile.exists() || outputFile == null) {
            return false;
        }

        try {
            BufferedImage original = ImageIO.read(inputFile);
            if (original == null) {
                log.warn("Could not read image file {} for preprocessing", inputFile.getName());
                return false;
            }

            BufferedImage enhanced = processImage(original, mode);
            ImageIO.write(enhanced, "PNG", outputFile);
            return true;
        } catch (IOException e) {
            log.warn("Image preprocessing failed for {}: {}", inputFile.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * In-memory image processing pipeline.
     */
    public BufferedImage processImage(BufferedImage original, PreprocessingMode mode) {
        // 1. Identify relevant label region (candidate text bounding box)
        BufferedImage focused = focusOnTextRegion(original);

        int width = focused.getWidth();
        int height = focused.getHeight();

        // 2. Text Scaling: Cap oversized photos and upscale small text
        int maxDim = Math.max(width, height);
        double scale = 1.0;
        if (maxDim > 1000) {
            scale = 1000.0 / maxDim; // Downscale oversized camera photos to avoid OCR timeouts on cloud hosts
        } else if (maxDim < 800) {
            scale = 1.3; // Upscale small text so Tesseract LSTM has sufficient pixel stroke width
        }

        int targetWidth = (int) Math.round(width * scale);
        int targetHeight = (int) Math.round(height * scale);

        // 3. Grayscale conversion & Bicubic Upscaling
        BufferedImage gray = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(focused, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        // 4. Contrast Normalization (dynamic histogram stretching with 1% percentile clipping)
        normalizeContrast(gray);

        // 5. Apply binarization if requested
        if (mode == PreprocessingMode.GLOBAL_OTSU) {
            return applyGlobalOtsuThreshold(gray);
        } else if (mode == PreprocessingMode.LOCAL_ADAPTIVE) {
            return applyLocalAdaptiveThreshold(gray, 25, 10);
        }

        return gray;
    }

    /**
     * Identifies candidate text regions by gradient energy profiling and crops
     * to the packaging label box if a distinct region is detected.
     */
    public BufferedImage focusOnTextRegion(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        // Avoid cropping very small images
        if (w < 400 || h < 400) {
            return src;
        }

        int step = Math.max(1, Math.min(w, h) / 200);
        int[] rowEnergy = new int[h];
        int[] colEnergy = new int[w];

        long totalEnergy = 0;
        int count = 0;

        for (int y = step; y < h - step; y += step) {
            for (int x = step; x < w - step; x += step) {
                int rgbCenter = src.getRGB(x, y);
                int rgbRight = src.getRGB(x + step, y);
                int rgbDown = src.getRGB(x, y + step);

                int lumaCenter = (int) (0.299 * ((rgbCenter >> 16) & 0xFF) + 0.587 * ((rgbCenter >> 8) & 0xFF) + 0.114 * (rgbCenter & 0xFF));
                int lumaRight = (int) (0.299 * ((rgbRight >> 16) & 0xFF) + 0.587 * ((rgbRight >> 8) & 0xFF) + 0.114 * (rgbRight & 0xFF));
                int lumaDown = (int) (0.299 * ((rgbDown >> 16) & 0xFF) + 0.587 * ((rgbDown >> 8) & 0xFF) + 0.114 * (rgbDown & 0xFF));

                int grad = Math.abs(lumaCenter - lumaRight) + Math.abs(lumaCenter - lumaDown);
                rowEnergy[y] += grad;
                colEnergy[x] += grad;
                totalEnergy += grad;
                count++;
            }
        }

        if (count == 0) return src;
        double avgEnergy = (double) totalEnergy / count;

        // Find bounding boundaries exceeding average edge density
        int minY = 0, maxY = h - 1;
        int minX = 0, maxX = w - 1;

        for (int y = 0; y < h; y += step) {
            if (rowEnergy[y] > avgEnergy * 0.8) {
                minY = y;
                break;
            }
        }
        for (int y = h - 1; y >= 0; y -= step) {
            if (rowEnergy[y] > avgEnergy * 0.8) {
                maxY = y;
                break;
            }
        }
        for (int x = 0; x < w; x += step) {
            if (colEnergy[x] > avgEnergy * 0.8) {
                minX = x;
                break;
            }
        }
        for (int x = w - 1; x >= 0; x -= step) {
            if (colEnergy[x] > avgEnergy * 0.8) {
                maxX = x;
                break;
            }
        }

        // Add 5% padding around detected region
        int padX = (int) (w * 0.05);
        int padY = (int) (h * 0.05);

        int cropX = Math.max(0, minX - padX);
        int cropY = Math.max(0, minY - padY);
        int cropW = Math.min(w - cropX, (maxX - minX) + (2 * padX));
        int cropH = Math.min(h - cropY, (maxY - minY) + (2 * padY));

        // Only crop if the detected region covers at least 30% and less than 95% of the image
        long cropArea = (long) cropW * cropH;
        long totalArea = (long) w * h;
        if (cropArea >= totalArea * 0.30 && cropArea <= totalArea * 0.95 && cropW >= 200 && cropH >= 200) {
            log.debug("Focused on text region: {}x{} at ({},{}) from original {}x{}", cropW, cropH, cropX, cropY, w, h);
            return src.getSubimage(cropX, cropY, cropW, cropH);
        }

        return src;
    }

    private void normalizeContrast(BufferedImage gray) {
        int width = gray.getWidth();
        int height = gray.getHeight();
        int totalPixels = width * height;

        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int val = gray.getRaster().getSample(x, y, 0);
                histogram[val]++;
            }
        }

        int clipCount = totalPixels / 100; // 1% clipping
        int minLuma = 0;
        int accumulated = 0;
        for (int i = 0; i < 256; i++) {
            accumulated += histogram[i];
            if (accumulated >= clipCount) {
                minLuma = i;
                break;
            }
        }

        accumulated = 0;
        int maxLuma = 255;
        for (int i = 255; i >= 0; i--) {
            accumulated += histogram[i];
            if (accumulated >= clipCount) {
                maxLuma = i;
                break;
            }
        }

        if (maxLuma > minLuma) {
            double range = maxLuma - minLuma;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int val = gray.getRaster().getSample(x, y, 0);
                    int stretched = (int) Math.round(Math.max(0, Math.min(255, ((val - minLuma) / range) * 255.0)));
                    gray.getRaster().setSample(x, y, 0, stretched);
                }
            }
        }
    }

    /**
     * Global automatic thresholding via Otsu's method (Otsu 1979).
     * Calculates a single optimal global threshold by maximizing inter-class variance.
     */
    public BufferedImage applyGlobalOtsuThreshold(BufferedImage gray) {
        int threshold = computeOtsuThreshold(gray);
        int width = gray.getWidth();
        int height = gray.getHeight();

        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int val = gray.getRaster().getSample(x, y, 0);
                binary.getRaster().setSample(x, y, 0, val >= threshold ? 255 : 0);
            }
        }
        return binary;
    }

    /**
     * Local adaptive window thresholding.
     * Computes the local mean in an S x S window and thresholds pixel by (localMean - C).
     * Distinguishes local adaptive thresholding from global Otsu.
     */
    public BufferedImage applyLocalAdaptiveThreshold(BufferedImage gray, int windowSize, int c) {
        int width = gray.getWidth();
        int height = gray.getHeight();
        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        // Integral image for fast local box mean computation O(1) per pixel
        long[][] integral = new long[width + 1][height + 1];
        for (int y = 0; y < height; y++) {
            long sumRow = 0;
            for (int x = 0; x < width; x++) {
                sumRow += gray.getRaster().getSample(x, y, 0);
                integral[x + 1][y + 1] = integral[x + 1][y] + sumRow;
            }
        }

        int halfWin = windowSize / 2;
        for (int y = 0; y < height; y++) {
            int y1 = Math.max(0, y - halfWin);
            int y2 = Math.min(height, y + halfWin + 1);
            for (int x = 0; x < width; x++) {
                int x1 = Math.max(0, x - halfWin);
                int x2 = Math.min(width, x + halfWin + 1);

                long count = (long) (x2 - x1) * (y2 - y1);
                long sum = integral[x2][y2] - integral[x1][y2] - integral[x2][y1] + integral[x1][y1];
                int localMean = (int) (sum / count);

                int val = gray.getRaster().getSample(x, y, 0);
                binary.getRaster().setSample(x, y, 0, (val >= localMean - c) ? 255 : 0);
            }
        }

        return binary;
    }

    private int computeOtsuThreshold(BufferedImage gray) {
        int[] histogram = new int[256];
        int width = gray.getWidth();
        int height = gray.getHeight();
        int total = width * height;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int val = gray.getRaster().getSample(x, y, 0);
                histogram[val]++;
            }
        }

        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float varMax = 0;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            wF = total - wB;
            if (wF == 0) break;

            sumB += (float) (t * histogram[t]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);
            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }

        return threshold;
    }
}
