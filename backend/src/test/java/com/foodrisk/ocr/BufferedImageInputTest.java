package com.foodrisk.ocr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class BufferedImageInputTest {

    private byte[] createSamplePngBytes(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawString("Label Text", 20, 40);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should create immutable BufferedImageInput from valid MultipartFile")
    void testCreateFromMultipartFile() throws IOException {
        byte[] originalBytes = createSamplePngBytes(300, 300);
        MockMultipartFile mockFile = new MockMultipartFile(
                "ingredientImage", "label.png", "image/png", originalBytes
        );

        ImageValidator validator = new ImageValidator();
        BufferedImageInput input = BufferedImageInput.from(mockFile, validator);

        assertThat(input).isNotNull();
        assertThat(input.contentType()).isEqualTo("image/png");
        assertThat(input.originalFilename()).isEqualTo("label.png");
        assertThat(input.size()).isEqualTo(originalBytes.length);
        assertThat(input.width()).isEqualTo(300);
        assertThat(input.height()).isEqualTo(300);
        assertThat(input.isEmpty()).isFalse();
        assertThat(input.bytes()).isEqualTo(originalBytes);
    }

    @Test
    @DisplayName("Should enforce byte array immutability")
    void testByteImmutability() throws IOException {
        byte[] originalBytes = createSamplePngBytes(250, 250);
        BufferedImageInput input = new BufferedImageInput(
                originalBytes, "image/png", "test.png", originalBytes.length, 250, 250
        );

        // Mutate original array
        originalBytes[0] = 0;
        assertThat(input.bytes()[0]).isNotEqualTo((byte) 0);

        // Mutate array returned from getter
        byte[] extracted = input.bytes();
        extracted[0] = 0;
        assertThat(input.bytes()[0]).isNotEqualTo((byte) 0);
    }

    @Test
    @DisplayName("Should safely transfer across async thread boundary without request dependency")
    void testAsyncThreadSafety() throws IOException, ExecutionException, InterruptedException {
        byte[] originalBytes = createSamplePngBytes(250, 250);
        MockMultipartFile mockFile = new MockMultipartFile(
                "ingredientImage", "label.png", "image/png", originalBytes
        );

        BufferedImageInput input = BufferedImageInput.from(mockFile, new ImageValidator());

        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
            // Simulated async worker executing on another thread
            return input.bytes().length;
        });

        int asyncLength = future.get();
        assertThat(asyncLength).isEqualTo(originalBytes.length);

        MultipartFile inMemFile = input.toMultipartFile("ingredientImage");
        assertThat(inMemFile.getBytes()).isEqualTo(originalBytes);
        assertThat(inMemFile.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Should return null for null or empty input files")
    void testNullOrEmptyInput() {
        assertThat(BufferedImageInput.from(null, new ImageValidator())).isNull();

        MockMultipartFile emptyFile = new MockMultipartFile("empty", "empty.png", "image/png", new byte[0]);
        assertThat(BufferedImageInput.from(emptyFile, new ImageValidator())).isNull();
    }
}
