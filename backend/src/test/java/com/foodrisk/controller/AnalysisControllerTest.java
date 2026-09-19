package com.foodrisk.controller;

import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.repository.FoodAnalysisSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodAnalysisSessionRepository sessionRepository;

    private byte[] createSamplePng() throws IOException {
        BufferedImage img = new BufferedImage(400, 250, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 250);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("INGREDIENTS: SALT SUGAR WATER", 20, 60);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("POST /api/analysis/session creates guest session and echoes X-Request-ID")
    void testCreateSession() throws Exception {
        String customRequestId = "client-req-" + UUID.randomUUID();

        mockMvc.perform(post("/api/analysis/session")
                        .header("X-Request-ID", customRequestId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-ID", customRequestId))
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.sessionToken").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/analysis/{sessionId} retrieves session metadata")
    void testGetSession() throws Exception {
        FoodAnalysisSession session = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.CREATED,
                Instant.now().plusSeconds(900)
        );
        FoodAnalysisSession saved = sessionRepository.save(session);

        mockMvc.perform(get("/api/analysis/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(saved.getId().toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.sessionToken").value(saved.getSessionToken()));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/ocr processes valid image and returns raw OCR text")
    void testOcrEndpointSuccess() throws Exception {
        FoodAnalysisSession session = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.CREATED,
                Instant.now().plusSeconds(900)
        );
        FoodAnalysisSession saved = sessionRepository.save(session);

        MockMultipartFile file = new MockMultipartFile(
                "ingredientImage",
                "label.png",
                "image/png",
                createSamplePng()
        );

        mockMvc.perform(multipart("/api/analysis/" + saved.getId() + "/ocr")
                        .file(file)
                        .header("X-Request-ID", "req-ocr-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-ocr-123"))
                .andExpect(jsonPath("$.sessionId").value(saved.getId().toString()))
                .andExpect(jsonPath("$.ingredients.present").value(true))
                .andExpect(jsonPath("$.ingredients.rawText").isNotEmpty())
                .andExpect(jsonPath("$.nutrition.present").value(false))
                .andExpect(jsonPath("$.nutrition.rawText").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/ocr rejects expired session with 410 GONE")
    void testOcrExpiredSessionReturns410() throws Exception {
        FoodAnalysisSession expiredSession = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.CREATED,
                Instant.now().minusSeconds(60) // already expired
        );
        FoodAnalysisSession saved = sessionRepository.save(expiredSession);

        MockMultipartFile file = new MockMultipartFile(
                "ingredientImage",
                "label.png",
                "image/png",
                createSamplePng()
        );

        mockMvc.perform(multipart("/api/analysis/" + saved.getId() + "/ocr")
                        .file(file))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    @DisplayName("POST /api/analysis/{sessionId}/ocr rejects invalid image format with 400 BAD REQUEST")
    void testOcrInvalidImageReturns400() throws Exception {
        FoodAnalysisSession session = new FoodAnalysisSession(
                "token-" + UUID.randomUUID(),
                AnalysisStatus.CREATED,
                Instant.now().plusSeconds(900)
        );
        FoodAnalysisSession saved = sessionRepository.save(session);

        MockMultipartFile corruptFile = new MockMultipartFile(
                "ingredientImage",
                "bad.txt",
                "text/plain",
                "Not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/analysis/" + saved.getId() + "/ocr")
                        .file(corruptFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported MIME type")));
    }
}
