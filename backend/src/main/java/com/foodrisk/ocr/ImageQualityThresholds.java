package com.foodrisk.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralized, configurable quality thresholds for food packaging label image assessment.
 *
 * Threshold Taxonomy:
 * 1. Security & Technical Limits:
 *    Hard boundaries required for memory safety, decompression-bomb defense, and raster decodability.
 * 2. Engineering Heuristics:
 *    Pragmatic rules of thumb designed to filter out obviously unreadable captures (e.g. extreme blur,
 *    pitch-black captures) while remaining permissive enough to allow small packaging fonts.
 * 3. Empirically Benchmarked Thresholds:
 *    Thresholds tuned against representative food packaging samples (high-res small print,
 *    moderate glare, mobile camera motion).
 */
@Component
@ConfigurationProperties(prefix = "ocr.quality.image")
public class ImageQualityThresholds {

    // =========================================================================
    // Category 1: Security & Technical Limits
    // =========================================================================
    /** Minimum width in pixels for Tesseract raster decodability. Technical limit. */
    public static final int DEFAULT_MIN_WIDTH = 200;

    /** Minimum height in pixels for Tesseract raster decodability. Technical limit. */
    public static final int DEFAULT_MIN_HEIGHT = 200;

    /** Minimum total pixel count (200x200 = 40,000). Technical limit. */
    public static final long DEFAULT_MIN_PIXELS = 40_000L;

    /** Maximum image width to prevent decompression bombs. Security limit. */
    public static final int DEFAULT_MAX_WIDTH = 8000;

    /** Maximum image height to prevent decompression bombs. Security limit. */
    public static final int DEFAULT_MAX_HEIGHT = 8000;

    /** Maximum file upload size (10 MB). Security limit. */
    public static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024L;

    // =========================================================================
    // Category 2: Engineering Heuristics & Empirically Benchmarked Settings
    // =========================================================================
    /**
     * Discrete Laplacian gradient variance threshold.
     * Engineering heuristic: values below this indicates extreme defocus or severe motion blur
     * where character edge contrast is destroyed.
     */
    public static final double DEFAULT_MIN_BLUR_LAPLACIAN_VAR = 18.0;

    /**
     * Luminance standard deviation threshold (0 to 255).
     * Engineering heuristic: values below this indicate flat, pitch-black, or completely washed-out frames.
     */
    public static final double DEFAULT_MIN_CONTRAST_STD_DEV = 14.0;

    /**
     * Grid tile division (e.g. 4 means a 4x4 grid = 16 regional tiles).
     * Engineering heuristic: allows localized text-region detection so high-res packages
     * with plain backgrounds and small sharp label boxes are not falsely rejected.
     */
    public static final int DEFAULT_TILE_GRID_SIZE = 4;

    /**
     * Threshold for considering a regional tile "active" (high gradient content).
     * Engineering heuristic.
     */
    public static final double DEFAULT_ACTIVE_TILE_CONTRAST_RATIO = 1.15;

    // Instance fields for Spring configuration overrides
    private int minWidth = DEFAULT_MIN_WIDTH;
    private int minHeight = DEFAULT_MIN_HEIGHT;
    private long minPixels = DEFAULT_MIN_PIXELS;
    private double minBlurLaplacianVar = DEFAULT_MIN_BLUR_LAPLACIAN_VAR;
    private double minContrastStdDev = DEFAULT_MIN_CONTRAST_STD_DEV;
    private int tileGridSize = DEFAULT_TILE_GRID_SIZE;

    public ImageQualityThresholds() {}

    public int getMinWidth() {
        return minWidth;
    }

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
    }

    public long getMinPixels() {
        return minPixels;
    }

    public void setMinPixels(long minPixels) {
        this.minPixels = minPixels;
    }

    public double getMinBlurLaplacianVar() {
        return minBlurLaplacianVar;
    }

    public void setMinBlurLaplacianVar(double minBlurLaplacianVar) {
        this.minBlurLaplacianVar = minBlurLaplacianVar;
    }

    public double getMinContrastStdDev() {
        return minContrastStdDev;
    }

    public void setMinContrastStdDev(double minContrastStdDev) {
        this.minContrastStdDev = minContrastStdDev;
    }

    public int getTileGridSize() {
        return tileGridSize;
    }

    public void setTileGridSize(int tileGridSize) {
        this.tileGridSize = tileGridSize;
    }
}
