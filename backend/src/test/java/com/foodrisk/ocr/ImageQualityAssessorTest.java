package com.foodrisk.ocr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ImageQualityAssessorTest {

    private ImageQualityAssessor assessor;

    @BeforeEach
    void setUp() {
        assessor = new ImageQualityAssessor(new ImageQualityThresholds());
    }

    private byte[] createSharpTextImage(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        for (int y = 30; y < height - 20; y += 30) {
            g.drawString("INGREDIENTS: Wheat Flour, Sugar, Palm Oil, Salt, INS 330, Cocoa Solids", 20, y);
        }
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createRegionalLabelPackaging(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // 75% of package is solid red pouch
        g.setColor(new Color(200, 30, 30));
        g.fillRect(0, 0, width, height);

        // 25% in lower right is a crisp white nutrition facts box with black text
        int boxW = width / 2;
        int boxH = height / 2;
        int boxX = width / 2;
        int boxY = height / 2;

        g.setColor(Color.WHITE);
        g.fillRect(boxX, boxY, boxW, boxH);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        for (int y = boxY + 25; y < boxY + boxH - 10; y += 22) {
            g.drawString("Nutrition Facts: Energy 450 kcal, Protein 8g, Fat 18g", boxX + 10, y);
        }
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createBlurryTextImage(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        for (int y = 30; y < height - 20; y += 30) {
            g.drawString("Ingredients: Wheat, Sugar, Salt", 20, y);
        }
        g.dispose();

        // Apply heavy Gaussian blur convolution
        int size = 9;
        float[] matrix = new float[size * size];
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = 1.0f / (size * size);
        }
        ConvolveOp op = new ConvolveOp(new Kernel(size, size, matrix), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage blurred = op.filter(img, null);
        blurred = op.filter(blurred, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(blurred, "PNG", baos);
        return baos.toByteArray();
    }

    private byte[] createLowContrastImage(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(130, 130, 130));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(133, 133, 133));
        g.drawString("Low contrast ingredients text", 20, 50);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should accept clear, sharp packaging label image")
    void testSharpImageAccepted() throws IOException {
        byte[] bytes = createSharpTextImage(600, 400);
        ImageQualityAssessor.ImageQualityResult result = assessor.assess(bytes, OcrLabelType.INGREDIENTS);

        assertThat(result.isUsable()).isTrue();
        assertThat(result.issue()).isEqualTo(ImageQualityAssessor.QualityIssue.OK);
        assertThat(result.userGuidance()).isNull();
        assertThat(result.blurScore()).isGreaterThan(18.0);
    }

    @Test
    @DisplayName("Should accept high-resolution packaging with localized sharp label box")
    void testRegionalLabelPackagingAccepted() throws IOException {
        byte[] bytes = createRegionalLabelPackaging(1200, 1000);
        ImageQualityAssessor.ImageQualityResult result = assessor.assess(bytes, OcrLabelType.NUTRITION);

        assertThat(result.isUsable()).isTrue();
        assertThat(result.issue()).isEqualTo(ImageQualityAssessor.QualityIssue.OK);
    }

    @Test
    @DisplayName("Should reject blurry or out-of-focus image with actionable guidance")
    void testBlurryImageRejected() throws IOException {
        byte[] bytes = createBlurryTextImage(500, 500);
        ImageQualityAssessor.ImageQualityResult result = assessor.assess(bytes, OcrLabelType.INGREDIENTS);

        assertThat(result.isUsable()).isFalse();
        assertThat(result.issue()).isEqualTo(ImageQualityAssessor.QualityIssue.BLURRY);
        assertThat(result.userGuidance()).contains("retake the photo with the text in focus");
    }

    @Test
    @DisplayName("Should reject low-contrast or poorly lit image with lighting guidance")
    void testLowContrastImageRejected() throws IOException {
        byte[] bytes = createLowContrastImage(400, 400);
        ImageQualityAssessor.ImageQualityResult result = assessor.assess(bytes, OcrLabelType.NUTRITION);

        assertThat(result.isUsable()).isFalse();
        assertThat(result.issue()).isEqualTo(ImageQualityAssessor.QualityIssue.LOW_CONTRAST);
        assertThat(result.userGuidance()).contains("retake the photo with the text in focus and good lighting");
    }

    @Test
    @DisplayName("Should reject image with resolution too low for packaging text")
    void testLowResolutionRejected() throws IOException {
        byte[] bytes = createSharpTextImage(150, 150); // Below 200x200 minimum
        ImageQualityAssessor.ImageQualityResult result = assessor.assess(bytes, OcrLabelType.INGREDIENTS);

        assertThat(result.isUsable()).isFalse();
        assertThat(result.issue()).isEqualTo(ImageQualityAssessor.QualityIssue.TOO_LOW_RESOLUTION);
        assertThat(result.userGuidance()).contains("resolution is too low");
    }

    @Test
    @DisplayName("Should reject empty or corrupted image bytes gracefully")
    void testEmptyOrCorruptBytes() {
        ImageQualityAssessor.ImageQualityResult emptyResult = assessor.assess(new byte[0], OcrLabelType.INGREDIENTS);
        assertThat(emptyResult.isUsable()).isFalse();
        assertThat(emptyResult.issue()).isEqualTo(ImageQualityAssessor.QualityIssue.UNREADABLE);

        ImageQualityAssessor.ImageQualityResult corruptResult = assessor.assess(new byte[]{1, 2, 3, 4}, OcrLabelType.INGREDIENTS);
        assertThat(corruptResult.isUsable()).isFalse();
        assertThat(corruptResult.issue()).isEqualTo(ImageQualityAssessor.QualityIssue.UNREADABLE);
    }
}
