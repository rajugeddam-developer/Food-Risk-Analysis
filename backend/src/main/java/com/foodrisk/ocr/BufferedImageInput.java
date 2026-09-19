package com.foodrisk.ocr;

import com.foodrisk.exception.InvalidImageException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

/**
 * Immutable, thread-safe representation of an uploaded packaging image payload.
 *
 * Solves the Servlet container lifecycle issue where Tomcat destroys or recycles
 * temporary request files upon sending HTTP 202 Accepted.
 *
 * This payload is buffered synchronously on the servlet HTTP worker thread before
 * any asynchronous pipeline execution. The raw MultipartFile is never passed into
 * asynchronous CompletableFuture workers.
 */
public record BufferedImageInput(
        byte[] bytes,
        String contentType,
        String originalFilename,
        long size,
        int width,
        int height
) {
    public BufferedImageInput {
        Objects.requireNonNull(bytes, "Image bytes cannot be null");
        // Ensure immutability by cloning the byte array
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public boolean isEmpty() {
        return bytes == null || bytes.length == 0;
    }

    /**
     * Converts to an in-memory MultipartFile compatible with existing interfaces.
     */
    public MultipartFile toMultipartFile(String paramName) {
        return new InMemoryMultipartFile(
                paramName != null ? paramName : "image",
                originalFilename != null ? originalFilename : "image.png",
                contentType != null ? contentType : "image/png",
                bytes
        );
    }

    /**
     * Synchronously buffers a MultipartFile into an immutable BufferedImageInput payload.
     *
     * @param file servlet-bound multipart file
     * @return immutable BufferedImageInput, or null if file is null/empty
     */
    public static BufferedImageInput from(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("Failed to read image upload stream: " + e.getMessage());
        }

        if (rawBytes == null || rawBytes.length == 0) {
            return null;
        }

        int width = 0;
        int height = 0;
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            if (iis != null) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(iis);
                        width = reader.getWidth(0);
                        height = reader.getHeight(0);
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (Exception ignored) {
            // Non-critical: fallback to 0, 0 if image metadata cannot be read
        }

        return new BufferedImageInput(
                rawBytes,
                file.getContentType() != null ? file.getContentType() : "image/png",
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.png",
                file.getSize(),
                width,
                height
        );
    }

    public static BufferedImageInput from(MultipartFile file, ImageValidator validator) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (validator != null) {
            validator.validate(file);
        }
        return from(file);
    }
}
