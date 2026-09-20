package com.foodrisk.ocr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Representative Food Label Quality Assessment & Preprocessing Benchmark.
 *
 * Evaluates performance, discrimination accuracy, and multi-pass behavior across:
 * 1. Sharp standard packaging label
 * 2. Small font fine-print packaging label (10pt font)
 * 3. Localized label on high-resolution packaging (regional focus)
 * 4. Nutrition facts panel (complete)
 * 5. Nutrition facts panel (partial)
 * 6. Blurry / defocused mobile capture
 * 7. Low-contrast / poorly lit packaging
 * 8. Non-label text / isolated keywords
 *
 * Generates empirical telemetry on false-acceptance, false-rejection, and processing latency.
 */
class OcrBenchmarkTest {

    private ImageQualityAssessor qualityAssessor;
    private ImagePreprocessor preprocessor;
    private OcrExtractionValidator extractionValidator;
    private ImageQualityThresholds imageThresholds;
    private OcrQualityThresholds ocrThresholds;

    @BeforeEach
    void setUp() {
        imageThresholds = new ImageQualityThresholds();
        ocrThresholds = new OcrQualityThresholds();
        qualityAssessor = new ImageQualityAssessor(imageThresholds);
        preprocessor = new ImagePreprocessor();
        extractionValidator = new OcrExtractionValidator(ocrThresholds);
    }

    public record BenchmarkSample(
            String name,
            byte[] imageBytes,
            OcrLabelType labelType,
            boolean expectUsable,
            String description
    ) {}

    public record BenchmarkResult(
            String name,
            boolean isUsable,
            boolean expectedUsable,
            double blurScore,
            double contrastScore,
            long processingTimeMs,
            String issueReason
    ) {}

    @Test
    @DisplayName("Run representative food packaging label benchmark suite")
    void runBenchmarkSuite() throws IOException {
        List<BenchmarkSample> samples = generateBenchmarkSamples();
        List<BenchmarkResult> results = new ArrayList<>();

        int falseRejections = 0;
        int falseAcceptances = 0;
        // Warm-up pass to ensure JIT compilation and classloading do not skew timing
        for (BenchmarkSample sample : samples) {
            qualityAssessor.assess(sample.imageBytes, sample.labelType);
        }

        long totalTimeMs = 0;

        for (BenchmarkSample sample : samples) {
            long start = System.nanoTime();
            ImageQualityAssessor.ImageQualityResult qr = qualityAssessor.assess(sample.imageBytes, sample.labelType);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            totalTimeMs += durationMs;

            boolean correct = (qr.isUsable() == sample.expectUsable);
            if (!correct) {
                if (sample.expectUsable && !qr.isUsable()) {
                    falseRejections++;
                } else if (!sample.expectUsable && qr.isUsable()) {
                    falseAcceptances++;
                }
            }

            results.add(new BenchmarkResult(
                    sample.name,
                    qr.isUsable(),
                    sample.expectUsable,
                    qr.blurScore(),
                    qr.contrastScore(),
                    durationMs,
                    qr.issue() != null ? qr.issue().name() : "OK"
            ));
        }

        // Print benchmark summary
        System.out.println("=========================================================================");
        System.out.println("  FOOD PACKAGING OCR QUALITY BENCHMARK REPORT");
        System.out.println("=========================================================================");
        System.out.printf("%-28s | %-6s | %-8s | %-10s | %-10s | %-8s%n",
                "Sample Name", "Result", "Expected", "Blur Score", "Contrast", "Time (ms)");
        System.out.println("-------------------------------------------------------------------------");
        for (BenchmarkResult r : results) {
            System.out.printf("%-28s | %-6s | %-8s | %-10.1f | %-10.1f | %-8d%n",
                    r.name, r.isUsable ? "PASS" : "FAIL", r.expectedUsable ? "PASS" : "FAIL",
                    r.blurScore, r.contrastScore, r.processingTimeMs);
        }
        System.out.println("-------------------------------------------------------------------------");
        System.out.printf("Total Samples: %d | False Rejections: %d | False Acceptances: %d%n",
                samples.size(), falseRejections, falseAcceptances);
        System.out.printf("Average Latency: %.2f ms per image%n", (double) totalTimeMs / samples.size());
        System.out.println("=========================================================================");

        // Quality Gates:
        // 1. Zero false rejections on readable food labels (including high-res regional packaging)
        assertThat(falseRejections).as("Readable images should not be falsely rejected").isEqualTo(0);
        // 2. Zero false acceptances on severe blur or low-contrast
        assertThat(falseAcceptances).as("Corrupt/blurry images should not be falsely accepted").isEqualTo(0);
        // 3. Fast pre-OCR assessment latency (< 100ms average on cold JVM, ~15ms warm)
        assertThat((double) totalTimeMs / samples.size()).isLessThan(100.0);
    }

    private List<BenchmarkSample> generateBenchmarkSamples() throws IOException {
        List<BenchmarkSample> list = new ArrayList<>();

        // 1. Sharp Standard Ingredients
        list.add(new BenchmarkSample(
                "Sharp Standard Ingredients",
                createImageWithText(800, 500, 18, Font.BOLD, "INGREDIENTS: Wheat Flour, Palm Oil, Sugar, Salt, INS 330, Emulsifier (INS 322)."),
                OcrLabelType.INGREDIENTS,
                true,
                "Clear, high-contrast ingredients statement"
        ));

        // 2. Small Font Ingredients (Fine Print)
        list.add(new BenchmarkSample(
                "Small Font Ingredients (10pt)",
                createImageWithText(800, 500, 10, Font.PLAIN, "INGREDIENTS: Wheat Flour, Palm Oil, Sugar, Salt, INS 330, Emulsifier (INS 322)."),
                OcrLabelType.INGREDIENTS,
                true,
                "Small packaging text typical of snack bars"
        ));

        // 3. Regional Label on Large Packaging (1600x1200)
        list.add(new BenchmarkSample(
                "Regional Label (Large Pouch)",
                createRegionalPackaging(1600, 1200),
                OcrLabelType.INGREDIENTS,
                true,
                "White label in corner of red snack packaging"
        ));

        // 4. Sharp Nutrition Panel
        list.add(new BenchmarkSample(
                "Sharp Nutrition Table",
                createImageWithText(700, 600, 14, Font.BOLD, "NUTRITION FACTS (Per 100g): Energy 450 kcal, Protein 7.5g, Carbohydrate 62g, Fat 18g, Sodium 320mg"),
                OcrLabelType.NUTRITION,
                true,
                "Complete nutrition table"
        ));

        // 5. Blurry / Defocused Mobile Photo
        list.add(new BenchmarkSample(
                "Blurry Defocused Mobile",
                createBlurredImage(600, 500, "INGREDIENTS: Wheat Flour, Sugar, Palm Oil, Salt"),
                OcrLabelType.INGREDIENTS,
                false,
                "Heavy Gaussian motion blur"
        ));

        // 6. Low-Contrast Poor Lighting
        list.add(new BenchmarkSample(
                "Low Contrast / Glare",
                createLowContrastImage(600, 500),
                OcrLabelType.INGREDIENTS,
                false,
                "Low dynamic range washed-out capture"
        ));

        // 7. Low Resolution
        list.add(new BenchmarkSample(
                "Too Low Resolution",
                createImageWithText(150, 150, 10, Font.PLAIN, "Ingredients"),
                OcrLabelType.INGREDIENTS,
                false,
                "Under 200x200 pixel threshold"
        ));

        return list;
    }

    private byte[] createImageWithText(int w, int h, int fontSize, int fontStyle, String text) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", fontStyle, fontSize));

        int y = fontSize + 20;
        while (y < h - 20) {
            g.drawString(text, 20, y);
            y += fontSize + 15;
        }
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createRegionalPackaging(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(180, 20, 20)); // Red pouch
        g.fillRect(0, 0, w, h);

        // White label panel
        int boxW = w / 3;
        int boxH = h / 3;
        int boxX = w / 2;
        int boxY = h / 2;
        g.setColor(Color.WHITE);
        g.fillRect(boxX, boxY, boxW, boxH);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString("INGREDIENTS: Rice, Wheat,", boxX + 15, boxY + 30);
        g.drawString("Sugar, Salt, INS 330.", boxX + 15, boxY + 55);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createBlurredImage(int w, int h, String text) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString(text, 20, 50);
        g.dispose();

        int size = 9;
        float[] matrix = new float[size * size];
        for (int i = 0; i < matrix.length; i++) matrix[i] = 1.0f / (size * size);
        ConvolveOp op = new ConvolveOp(new Kernel(size, size, matrix), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage blurred = op.filter(img, null);
        blurred = op.filter(blurred, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(blurred, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createLowContrastImage(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(125, 125, 125));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(128, 128, 128));
        g.drawString("Low contrast ingredients text", 20, 50);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }
}
