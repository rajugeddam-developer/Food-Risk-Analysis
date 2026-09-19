package com.foodrisk.service;

import com.foodrisk.dto.OcrAnalysisResponse;
import com.foodrisk.entity.AnalysisStatus;
import com.foodrisk.entity.FoodAnalysisSession;
import com.foodrisk.exception.InvalidImageException;
import com.foodrisk.exception.SessionExpiredException;
import com.foodrisk.ocr.ImageValidator;
import com.foodrisk.ocr.OcrLabelType;
import com.foodrisk.ocr.OcrProvider;
import com.foodrisk.ocr.OcrResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private ImageValidator imageValidator;

    @Mock
    private OcrProvider ocrProvider;

    @Mock
    private AnalysisSessionService sessionService;

    private OcrService ocrService;
    private UUID sessionId;
    private FoodAnalysisSession activeSession;

    @BeforeEach
    void setUp() {
        ocrService = new OcrService(imageValidator, ocrProvider, sessionService);
        sessionId = UUID.randomUUID();
        activeSession = new FoodAnalysisSession("test-token", AnalysisStatus.CREATED, Instant.now().plusSeconds(900));
        activeSession.setId(sessionId);
    }

    private MockMultipartFile createDummyImage(String paramName) {
        return new MockMultipartFile(paramName, "test.png", "image/png", new byte[]{1, 2, 3, 4});
    }

    @Test
    @DisplayName("Should extract text when both ingredient and nutrition images are provided")
    void testBothImagesProvided() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        doNothing().when(imageValidator).validate(any());
        when(ocrProvider.extractText(any(File.class), eq(OcrLabelType.INGREDIENTS)))
                .thenReturn(new OcrResult("Sugar, Palm Oil, INS 330", 92.5f, 150));
        when(ocrProvider.extractText(any(File.class), eq(OcrLabelType.NUTRITION)))
                .thenReturn(new OcrResult("Energy 500 kcal, Fat 25g", 88.0f, 180));

        MockMultipartFile ingFile = createDummyImage("ingredientImage");
        MockMultipartFile nutFile = createDummyImage("nutritionImage");

        OcrAnalysisResponse response = ocrService.processOcr(sessionId, ingFile, nutFile);

        assertThat(response).isNotNull();
        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.ingredients().present()).isTrue();
        assertThat(response.ingredients().rawText()).isEqualTo("Sugar, Palm Oil, INS 330");
        assertThat(response.nutrition().present()).isTrue();
        assertThat(response.nutrition().rawText()).isEqualTo("Energy 500 kcal, Fat 25g");
        verify(sessionService).updateStatus(activeSession, AnalysisStatus.PROCESSING);
    }

    @Test
    @DisplayName("Should handle missing nutrition image gracefully (partial scan)")
    void testMissingNutritionImage() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        doNothing().when(imageValidator).validate(any());
        when(ocrProvider.extractText(any(File.class), eq(OcrLabelType.INGREDIENTS)))
                .thenReturn(new OcrResult("Sugar, Salt", 95.0f, 120));

        MockMultipartFile ingFile = createDummyImage("ingredientImage");

        OcrAnalysisResponse response = ocrService.processOcr(sessionId, ingFile, null);

        assertThat(response.ingredients().present()).isTrue();
        assertThat(response.ingredients().rawText()).isEqualTo("Sugar, Salt");
        assertThat(response.nutrition().present()).isFalse();
        assertThat(response.nutrition().rawText()).isNull();
    }

    @Test
    @DisplayName("Should handle missing ingredient image gracefully (partial scan)")
    void testMissingIngredientImage() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        doNothing().when(imageValidator).validate(any());
        when(ocrProvider.extractText(any(File.class), eq(OcrLabelType.NUTRITION)))
                .thenReturn(new OcrResult("Sodium 200mg", 90.0f, 110));

        MockMultipartFile nutFile = createDummyImage("nutritionImage");

        OcrAnalysisResponse response = ocrService.processOcr(sessionId, null, nutFile);

        assertThat(response.ingredients().present()).isFalse();
        assertThat(response.ingredients().rawText()).isNull();
        assertThat(response.nutrition().present()).isTrue();
        assertThat(response.nutrition().rawText()).isEqualTo("Sodium 200mg");
    }

    @Test
    @DisplayName("Should reject request when both images are missing or empty")
    void testBothImagesMissing() {
        assertThatThrownBy(() -> ocrService.processOcr(sessionId, null, null))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("At least one packaging label image");
    }

    @Test
    @DisplayName("Should reject OCR when session is expired (HTTP 410)")
    void testExpiredSessionRejection() {
        when(sessionService.getActiveSession(sessionId))
                .thenThrow(new SessionExpiredException("Analysis session " + sessionId + " has expired."));

        MockMultipartFile ingFile = createDummyImage("ingredientImage");

        assertThatThrownBy(() -> ocrService.processOcr(sessionId, ingFile, null))
                .isInstanceOf(SessionExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Should ensure temporary files are deleted in finally block after OCR extraction")
    void testTemporaryFileCleanedUp() {
        when(sessionService.getActiveSession(sessionId)).thenReturn(activeSession);
        doNothing().when(imageValidator).validate(any());

        final File[] capturedFile = new File[1];
        when(ocrProvider.extractText(any(File.class), eq(OcrLabelType.INGREDIENTS)))
                .thenAnswer(invocation -> {
                    File file = invocation.getArgument(0);
                    capturedFile[0] = file;
                    assertThat(file.exists()).isTrue();
                    return new OcrResult("Palm Oil", 90f, 50);
                });

        MockMultipartFile ingFile = createDummyImage("ingredientImage");
        ocrService.processOcr(sessionId, ingFile, null);

        // Verify the file was cleaned up in the finally block
        assertThat(capturedFile[0]).isNotNull();
        assertThat(capturedFile[0].exists()).isFalse();
    }
}
