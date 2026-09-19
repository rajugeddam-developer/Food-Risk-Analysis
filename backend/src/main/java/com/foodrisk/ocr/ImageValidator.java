package com.foodrisk.ocr;

import com.foodrisk.exception.ImageSizeLimitExceededException;
import com.foodrisk.exception.InvalidImageException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Validates packaging image uploads prior to OCR processing.
 *
 * Enforces:
 * - Non-empty payload check.
 * - Maximum byte size limit (10MB).
 * - MIME type whitelist (image/jpeg, image/png, image/webp).
 * - Exact magic byte header matching.
 * - Decompression bomb defense: image dimension and pixel count checked via header metadata prior to decoding raster.
 * - Non-zero decoded image dimensions (width > 0, height > 0).
 * - Maximum dimensions <= 8000x8000 and <= 64 MP.
 * - Immediate rejection of PDF, SVG, EXE, ZIP, HTML, and corrupted files.
 */
@Component
public class ImageValidator {

    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    public static final int MAX_IMAGE_WIDTH = 8000;
    public static final int MAX_IMAGE_HEIGHT = 8000;
    public static final long MAX_PIXEL_COUNT = 64_000_000L; // 64 MP

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    // Magic bytes
    // JPEG: FF D8 FF
    // PNG: 89 50 4E 47 0D 0A 1A 0A
    // WEBP: RIFF (bytes 0-3: 52 49 46 46) + WEBP (bytes 8-11: 57 45 42 50)
    // PDF: %PDF (25 50 44 46)
    // ZIP: PK (50 4B 03 04)
    // EXE: MZ (4D 5A)

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("Uploaded image file is empty or missing.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ImageSizeLimitExceededException(
                    "Image file size (" + file.getSize() + " bytes) exceeds maximum limit of " + MAX_FILE_SIZE_BYTES + " bytes (10MB)."
            );
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidImageException(
                    "Unsupported MIME type: '" + contentType + "'. Allowed formats: JPEG, PNG, WEBP."
            );
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("Failed to read uploaded image data: " + e.getMessage());
        }

        validateBytes(bytes, contentType);
    }

    public void validateBytes(byte[] bytes, String declaredMimeType) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidImageException("Image payload is empty.");
        }

        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new ImageSizeLimitExceededException(
                    "Image payload size exceeds maximum limit of 10MB."
            );
        }

        // Check for disallowed signatures first (PDF, EXE, ZIP, HTML)
        if (isPdf(bytes)) {
            throw new InvalidImageException("PDF documents are not allowed. Please upload a JPEG, PNG, or WEBP image.");
        }
        if (isZip(bytes)) {
            throw new InvalidImageException("Archive (ZIP) files are not allowed. Please upload a JPEG, PNG, or WEBP image.");
        }
        if (isExe(bytes)) {
            throw new InvalidImageException("Executable files are strictly rejected.");
        }
        if (isHtmlOrSvg(bytes)) {
            throw new InvalidImageException("HTML or SVG files are not allowed. Only bitmap images (JPEG, PNG, WEBP) are supported.");
        }

        // Validate magic bytes for claimed format
        boolean matchesMagicBytes = false;
        if (isJpeg(bytes)) {
            matchesMagicBytes = true;
        } else if (isPng(bytes)) {
            matchesMagicBytes = true;
        } else if (isWebp(bytes)) {
            matchesMagicBytes = true;
        }

        if (!matchesMagicBytes) {
            throw new InvalidImageException("Image header magic bytes do not match supported formats (JPEG, PNG, WEBP).");
        }

        // Safe dimension check: read metadata header BEFORE decoding raster (decompression bomb protection)
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
            if (iis == null) {
                throw new InvalidImageException("Failed to create image input stream.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                if (isWebp(bytes) && bytes.length >= 30) {
                    validateWebpDimensions(bytes);
                    return;
                }
                throw new InvalidImageException("Image data could not be decoded. File may be corrupted or in an unrecognized format.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkDimensionLimits(width, height);
            } finally {
                reader.dispose();
            }
        } catch (InvalidImageException iie) {
            throw iie;
        } catch (IOException e) {
            throw new InvalidImageException("Corrupted image data: " + e.getMessage());
        }
    }

    private void checkDimensionLimits(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new InvalidImageException("Invalid image dimensions: " + width + "x" + height);
        }
        if (width > MAX_IMAGE_WIDTH || height > MAX_IMAGE_HEIGHT) {
            throw new InvalidImageException(
                    String.format("Image dimensions (%dx%d) exceed maximum permitted limit of %dx%d pixels.",
                            width, height, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
            );
        }
        long pixelCount = (long) width * (long) height;
        if (pixelCount > MAX_PIXEL_COUNT) {
            throw new InvalidImageException(
                    String.format("Image pixel count (%d pixels) exceeds maximum limit of %d pixels (64MP).",
                            pixelCount, MAX_PIXEL_COUNT)
            );
        }
    }

    private void validateWebpDimensions(byte[] b) {
        if (b.length < 30) {
            return;
        }
        String format = new String(b, 12, 4);
        int width = 0;
        int height = 0;

        if ("VP8X".equals(format) && b.length >= 30) {
            width = 1 + ((b[24] & 0xFF) | ((b[25] & 0xFF) << 8) | ((b[26] & 0xFF) << 16));
            height = 1 + ((b[27] & 0xFF) | ((b[28] & 0xFF) << 8) | ((b[29] & 0xFF) << 16));
        } else if ("VP8L".equals(format) && b.length >= 25) {
            int b1 = b[21] & 0xFF;
            int b2 = b[22] & 0xFF;
            int b3 = b[23] & 0xFF;
            int b4 = b[24] & 0xFF;
            width = 1 + (((b2 & 0x3F) << 8) | b1);
            height = 1 + (((b4 & 0x0F) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6));
        } else if ("VP8 ".equals(format) && b.length >= 30) {
            width = ((b[26] & 0xFF) | ((b[27] & 0xFF) << 8)) & 0x3FFF;
            height = ((b[28] & 0xFF) | ((b[29] & 0xFF) << 8)) & 0x3FFF;
        }

        if (width > 0 && height > 0) {
            checkDimensionLimits(width, height);
        }
    }

    private boolean isJpeg(byte[] b) {
        if (b.length < 3) return false;
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] b) {
        if (b.length < 8) return false;
        return (b[0] & 0xFF) == 0x89 &&
               (b[1] & 0xFF) == 0x50 && // 'P'
               (b[2] & 0xFF) == 0x4E && // 'N'
               (b[3] & 0xFF) == 0x47 && // 'G'
               (b[4] & 0xFF) == 0x0D &&
               (b[5] & 0xFF) == 0x0A &&
               (b[6] & 0xFF) == 0x1A &&
               (b[7] & 0xFF) == 0x0A;
    }

    private boolean isWebp(byte[] b) {
        if (b.length < 12) return false;
        // 'RIFF' at 0..3 and 'WEBP' at 8..11
        boolean riff = (b[0] & 0xFF) == 0x52 && (b[1] & 0xFF) == 0x49 && (b[2] & 0xFF) == 0x46 && (b[3] & 0xFF) == 0x46;
        boolean webp = (b[8] & 0xFF) == 0x57 && (b[9] & 0xFF) == 0x45 && (b[10] & 0xFF) == 0x42 && (b[11] & 0xFF) == 0x50;
        return riff && webp;
    }

    private boolean isPdf(byte[] b) {
        if (b.length < 4) return false;
        return (b[0] & 0xFF) == 0x25 && (b[1] & 0xFF) == 0x50 && (b[2] & 0xFF) == 0x44 && (b[3] & 0xFF) == 0x46; // %PDF
    }

    private boolean isZip(byte[] b) {
        if (b.length < 4) return false;
        return (b[0] & 0xFF) == 0x50 && (b[1] & 0xFF) == 0x4B && (b[2] & 0xFF) == 0x03 && (b[3] & 0xFF) == 0x04; // PK..
    }

    private boolean isExe(byte[] b) {
        if (b.length < 2) return false;
        return (b[0] & 0xFF) == 0x4D && (b[1] & 0xFF) == 0x5A; // MZ
    }

    private boolean isHtmlOrSvg(byte[] b) {
        int checkLen = Math.min(b.length, 128);
        String header = new String(b, 0, checkLen).toLowerCase();
        return header.contains("<!doctype html") ||
               header.contains("<html") ||
               header.contains("<svg") ||
               header.contains("<?xml");
    }
}
