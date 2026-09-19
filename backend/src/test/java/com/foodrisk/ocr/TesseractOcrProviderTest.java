package com.foodrisk.ocr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TesseractOcrProviderTest {

    @Test
    @DisplayName("Should initialize cleanly when local traineddata exists")
    void testInitializationWithValidData() {
        TesseractOcrProvider provider = new TesseractOcrProvider("tessdata", "eng");
        assertThatCode(provider::verifyTessData).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should fail fast with clear error message when traineddata is missing")
    void testFailFastOnMissingTrainedData() {
        TesseractOcrProvider provider = new TesseractOcrProvider("non_existent_folder", "eng");
        assertThatThrownBy(provider::verifyTessData)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tesseract traineddata file missing")
                .hasMessageContaining("Do not attempt internet downloads");
    }

    @Test
    @DisplayName("Should extract raw OCR text from clean test image without network access")
    void testExtractTextOffline() throws IOException {
        TesseractOcrProvider provider = new TesseractOcrProvider("tessdata", "eng");
        provider.verifyTessData();

        // Create an image with large, clear text
        int width = 400;
        int height = 150;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 28));
        g.drawString("PALM OIL SUGAR", 20, 80);
        g.dispose();

        File tempFile = File.createTempFile("ocr_test_", ".png");
        try {
            ImageIO.write(img, "png", tempFile);

            OcrResult result = provider.extractText(tempFile, OcrLabelType.INGREDIENTS);

            assertThat(result).isNotNull();
            assertThat(result.text()).isNotEmpty();
            // Should contain extracted words
            assertThat(result.text().toUpperCase()).contains("OIL");
            assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
