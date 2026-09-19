package com.foodrisk.ocr;

import com.foodrisk.exception.ImageSizeLimitExceededException;
import com.foodrisk.exception.InvalidImageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {

    private ImageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImageValidator();
    }

    private byte[] createSampleImageBytes(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawString("OCR Test", 10, 20);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should accept valid JPEG image")
    void testValidJpeg() throws IOException {
        byte[] bytes = createSampleImageBytes("jpeg", 100, 100);
        MockMultipartFile file = new MockMultipartFile("ingredientImage", "label.jpg", "image/jpeg", bytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept valid PNG image")
    void testValidPng() throws IOException {
        byte[] bytes = createSampleImageBytes("png", 100, 100);
        MockMultipartFile file = new MockMultipartFile("nutritionImage", "table.png", "image/png", bytes);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept valid WebP image header")
    void testValidWebp() {
        // Minimal RIFF ... WEBP header
        byte[] webpBytes = new byte[32];
        webpBytes[0] = 0x52; webpBytes[1] = 0x49; webpBytes[2] = 0x46; webpBytes[3] = 0x46; // RIFF
        webpBytes[8] = 0x57; webpBytes[9] = 0x45; webpBytes[10] = 0x42; webpBytes[11] = 0x50; // WEBP

        MockMultipartFile file = new MockMultipartFile("image", "label.webp", "image/webp", webpBytes);
        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject empty image file")
    void testEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("empty or missing");
    }

    @Test
    @DisplayName("Should reject null image file")
    void testNullFile() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("empty or missing");
    }

    @Test
    @DisplayName("Should reject disallowed MIME type")
    void testInvalidMime() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("Unsupported MIME type");
    }

    @Test
    @DisplayName("Should reject fake extension with non-image payload")
    void testFakeExtension() {
        byte[] fakeContent = "This is plain text pretending to be a JPG file".getBytes();
        MockMultipartFile file = new MockMultipartFile("image", "malicious.jpg", "image/jpeg", fakeContent);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("magic bytes do not match");
    }

    @Test
    @DisplayName("Should reject PDF file claiming to be JPEG")
    void testPdfHeaderRejection() {
        byte[] pdfBytes = "%PDF-1.4 simulated pdf".getBytes();
        MockMultipartFile file = new MockMultipartFile("image", "exploit.jpg", "image/jpeg", pdfBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("PDF documents are not allowed");
    }

    @Test
    @DisplayName("Should reject oversized image (> 10MB)")
    void testOversizedImage() {
        byte[] bigBytes = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile file = new MockMultipartFile("image", "huge.jpg", "image/jpeg", bigBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ImageSizeLimitExceededException.class)
                .hasMessageContaining("exceeds maximum limit");
    }

    @Test
    @DisplayName("Should reject corrupted image that cannot be decoded")
    void testCorruptedImage() {
        // JPEG magic bytes followed by garbage that fails decoding
        byte[] corruptJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile("image", "corrupt.jpg", "image/jpeg", corruptJpeg);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    @DisplayName("Decompression bomb defense: Should reject image with width exceeding 8000px")
    void testOversizedWidthRejected() throws IOException {
        byte[] wideImageBytes = createSampleImageBytes("png", 8500, 10);
        MockMultipartFile file = new MockMultipartFile("image", "bomb_wide.png", "image/png", wideImageBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("exceed maximum permitted limit");
    }

    @Test
    @DisplayName("Decompression bomb defense: Should reject image with height exceeding 8000px")
    void testOversizedHeightRejected() throws IOException {
        byte[] tallImageBytes = createSampleImageBytes("png", 10, 8500);
        MockMultipartFile file = new MockMultipartFile("image", "bomb_tall.png", "image/png", tallImageBytes);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("exceed maximum permitted limit");
    }
}
