package com.foodrisk.ocr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMultipartFileTest {

    @Test
    @DisplayName("Should preserve content and metadata from source MultipartFile")
    void testFromSource() throws IOException {
        byte[] data = "test image bytes".getBytes();
        MockMultipartFile mockSource = new MockMultipartFile("ingredientImage", "label.png", "image/png", data);

        InMemoryMultipartFile inMemory = InMemoryMultipartFile.from(mockSource);

        assertThat(inMemory).isNotNull();
        assertThat(inMemory.getName()).isEqualTo("ingredientImage");
        assertThat(inMemory.getOriginalFilename()).isEqualTo("label.png");
        assertThat(inMemory.getContentType()).isEqualTo("image/png");
        assertThat(inMemory.getSize()).isEqualTo(data.length);
        assertThat(inMemory.isEmpty()).isFalse();
        assertThat(inMemory.getBytes()).isEqualTo(data);
    }

    @Test
    @DisplayName("Should handle null source gracefully")
    void testNullSource() throws IOException {
        InMemoryMultipartFile inMemory = InMemoryMultipartFile.from(null);
        assertThat(inMemory).isNull();
    }

    @Test
    @DisplayName("Should successfully transfer bytes to destination file")
    void testTransferTo() throws IOException {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        InMemoryMultipartFile inMemory = new InMemoryMultipartFile("file", "test.bin", "application/octet-stream", data);

        File temp = File.createTempFile("test_transfer_", ".bin");
        try {
            inMemory.transferTo(temp);
            assertThat(temp.length()).isEqualTo(data.length);
        } finally {
            temp.delete();
        }
    }
}
