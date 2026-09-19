package com.foodrisk.ocr;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thread-safe, memory-backed implementation of {@link MultipartFile}.
 *
 * Used to capture incoming servlet multipart data synchronously on the HTTP request thread,
 * ensuring that data remains intact when processed asynchronously across thread boundaries,
 * immune to servlet container request recycling or ephemeral temp-file deletion.
 */
public class InMemoryMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name != null ? name : "file";
        this.originalFilename = originalFilename != null ? originalFilename : "image.png";
        this.contentType = contentType != null ? contentType : "image/png";
        this.content = content != null ? content : new byte[0];
    }

    public static InMemoryMultipartFile from(MultipartFile source) throws IOException {
        if (source == null) {
            return null;
        }
        return new InMemoryMultipartFile(
                source.getName(),
                source.getOriginalFilename(),
                source.getContentType(),
                source.getBytes()
        );
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public Resource getResource() {
        return new ByteArrayResource(content);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.write(dest.toPath(), content);
    }

    @Override
    public void transferTo(Path dest) throws IOException, IllegalStateException {
        Files.write(dest, content);
    }
}
