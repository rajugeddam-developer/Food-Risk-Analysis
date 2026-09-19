import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from '../router/Router';
import { UploadCard } from '../components/scan/UploadCard';
import { ScanProgressModal } from '../components/scan/ScanProgressModal';
import { OcrResultModal } from '../components/scan/OcrResultModal';
import { NormalizedFoodModal } from '../components/scan/NormalizedFoodModal';
import { AnalysisResultModal } from '../components/scan/AnalysisResultModal';
import { Button } from '../components/common/Button';
import { GlassCard } from '../components/common/GlassCard';
import type { CapturedImage } from '../components/scan/scanTypes';
import {
  createAnalysisSession,
  startFoodAnalysis,
  getAnalysisStatus,
  runOcr,
  normalizeFood,
  classifyFood,
  analyzeIngredientRisk,
  analyzeNutrition,
  ApiError,
  formatApiErrorMessage,
  type OcrAnalysisResponse,
  type NormalizedFoodData,
  type FoodClassificationResult,
  type IngredientRiskAnalysisResult,
  type AnalysisStatusResponse
} from '../api/analysisApi';
import './scan.css';

export const ScanPage: React.FC = () => {
  const { navigate } = useRouter();

  const [ingredientsImage, setIngredientsImage] = useState<CapturedImage | null>(null);
  const [nutritionImage, setNutritionImage] = useState<CapturedImage | null>(null);

  // Active transient session & analysis state
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [ocrData, setOcrData] = useState<OcrAnalysisResponse | null>(null);
  const [normalizedData, setNormalizedData] = useState<NormalizedFoodData | null>(null);
  const [classificationData, setClassificationData] = useState<FoodClassificationResult | null>(null);
  const [riskData, setRiskData] = useState<IngredientRiskAnalysisResult | null>(null);

  // Unified Orchestration Progress State
  const [isOrchestrating, setIsOrchestrating] = useState<boolean>(false);
  const [orchestrationStage, setOrchestrationStage] = useState<string>('IMAGE_VALIDATION');
  const [orchestrationError, setOrchestrationError] = useState<string | null>(null);

  // Step-by-step developer modal states
  const [isStepByStepMode, setIsStepByStepMode] = useState<boolean>(false);
  const [isOcrLoading, setIsOcrLoading] = useState<boolean>(false);
  const [isOcrModalOpen, setIsOcrModalOpen] = useState<boolean>(false);
  const [isNormalizeLoading, setIsNormalizeLoading] = useState<boolean>(false);
  const [isNormalizedModalOpen, setIsNormalizedModalOpen] = useState<boolean>(false);
  const [isRiskLoading, setIsRiskLoading] = useState<boolean>(false);
  const [isResultModalOpen, setIsResultModalOpen] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const pollingRef = useRef<boolean>(false);

  // Enforce deterministic object URL cleanup on unmount to prevent browser memory leaks
  useEffect(() => {
    return () => {
      pollingRef.current = false;
      if (ingredientsImage?.previewUrl && ingredientsImage.previewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(ingredientsImage.previewUrl);
      }
      if (nutritionImage?.previewUrl && nutritionImage.previewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(nutritionImage.previewUrl);
      }
    };
  }, [ingredientsImage, nutritionImage]);

  const hasAtLeastOneImage = Boolean(ingredientsImage || nutritionImage);

  // ---------------------------------------------------------------------------
  // Milestone M11/M12: Unified Orchestrated End-to-End Analysis Workflow
  // ---------------------------------------------------------------------------
  const handleStartUnifiedAnalysis = async () => {
    if (!hasAtLeastOneImage) return;

    setErrorMessage(null);
    setOrchestrationError(null);
    setIsOrchestrating(true);
    setOrchestrationStage('IMAGE_VALIDATION');
    pollingRef.current = true;

    try {
      // 1. Create transient analysis session (15-min TTL)
      const session = await createAnalysisSession();
      const activeSessionId = session.sessionId;
      setSessionId(activeSessionId);
      if (typeof window !== 'undefined') {
        sessionStorage.setItem('lastAnalysisSessionId', activeSessionId);
      }

      // 2. Trigger asynchronous backend analysis orchestrator
      setOrchestrationStage('OCR_PROCESSING');
      await startFoodAnalysis(
        activeSessionId,
        ingredientsImage?.file,
        nutritionImage?.file
      );

      // 3. Poll status with backoff until completed or failed
      let delayMs = 1500;
      const startTime = Date.now();
      const maxTimeoutMs = 65000;
      let networkFailureRetries = 0;
      const maxNetworkRetries = 3;

      while (pollingRef.current) {
        if (Date.now() - startTime > maxTimeoutMs) {
          throw new Error('Analysis timed out. Please verify lighting clarity and retry.');
        }

        await new Promise(res => setTimeout(res, delayMs));
        if (!pollingRef.current) break;

        let statusResponse: AnalysisStatusResponse;
        try {
          statusResponse = await getAnalysisStatus(activeSessionId);
          networkFailureRetries = 0; // Reset consecutive transient errors
        } catch (pollErr: unknown) {
          // Do not retry terminal client errors
          if (pollErr instanceof ApiError && [400, 401, 403, 404, 410, 413, 429].includes(pollErr.status)) {
            throw pollErr;
          }
          networkFailureRetries++;
          if (networkFailureRetries <= maxNetworkRetries) {
            console.warn(`Transient polling error (retry ${networkFailureRetries}/${maxNetworkRetries}):`, pollErr);
            continue;
          }
          throw pollErr;
        }

        if (statusResponse.currentStage) {
          setOrchestrationStage(statusResponse.currentStage);
        }

        if (statusResponse.status === 'COMPLETED') {
          setOrchestrationStage('COMPLETED');
          // Smooth transition to results page
          await new Promise(res => setTimeout(res, 500));
          setIsOrchestrating(false);
          pollingRef.current = false;
          navigate(`/result?sessionId=${activeSessionId}`);
          return;
        }

        if (statusResponse.status === 'FAILED') {
          throw new Error(statusResponse.errorMessage || 'Unable to complete food risk assessment. Please check image clarity.');
        }

        if (statusResponse.status === 'EXPIRED') {
          throw new Error('Analysis session has expired. Please try scanning again.');
        }

        // Adaptive polling backoff up to 3.5s
        delayMs = Math.min(delayMs + 500, 3500);
      }
    } catch (err: unknown) {
      pollingRef.current = false;
      const formatted = formatApiErrorMessage(err);
      setOrchestrationError(formatted.message);
    }
  };

  const handleCancelOrchestration = () => {
    pollingRef.current = false;
    setIsOrchestrating(false);
    setOrchestrationError(null);
  };

  // ---------------------------------------------------------------------------
  // Step-by-Step Developer Modal Inspection Mode (Preserved from M5-M8)
  // ---------------------------------------------------------------------------
  const handleStartStepByStepInspection = async () => {
    if (!hasAtLeastOneImage) return;

    setErrorMessage(null);
    setIsOcrLoading(true);

    try {
      const session = await createAnalysisSession();
      setSessionId(session.sessionId);
      if (typeof window !== 'undefined') {
        sessionStorage.setItem('lastAnalysisSessionId', session.sessionId);
      }

      const ocrResult = await runOcr(
        session.sessionId,
        ingredientsImage?.file,
        nutritionImage?.file
      );

      setOcrData(ocrResult);
      setIsOcrModalOpen(true);
    } catch (err: unknown) {
      const formatted = formatApiErrorMessage(err);
      setErrorMessage(formatted.message);
    } finally {
      setIsOcrLoading(false);
    }
  };

  const handleProceedToNormalization = async () => {
    if (!sessionId || !ocrData) return;

    setErrorMessage(null);
    setIsNormalizeLoading(true);

    try {
      const normalized = await normalizeFood(sessionId, {
        ingredientText: ocrData.ingredients.rawText,
        nutritionText: ocrData.nutrition.rawText
      });

      setNormalizedData(normalized);
      setIsOcrModalOpen(false);
      setIsNormalizedModalOpen(true);
    } catch (err: unknown) {
      const formatted = formatApiErrorMessage(err);
      setErrorMessage(formatted.message);
    } finally {
      setIsNormalizeLoading(false);
    }
  };

  const handleProceedToRiskAnalysis = async () => {
    if (!sessionId) return;

    setErrorMessage(null);
    setIsRiskLoading(true);

    try {
      // Execute M7 Food Categorization, M8 Ingredient Risk, and M9 Nutrition Analysis
      const [classification, riskResult] = await Promise.all([
        classifyFood(sessionId),
        analyzeIngredientRisk(sessionId)
      ]);

      // Fire M9 nutrition analysis in background
      analyzeNutrition(sessionId).catch(e => console.warn('Nutrition evaluation warning:', e));

      setClassificationData(classification);
      setRiskData(riskResult);
      setIsNormalizedModalOpen(false);
      setIsResultModalOpen(true);
    } catch (err: unknown) {
      const formatted = formatApiErrorMessage(err);
      setErrorMessage(formatted.message);
    } finally {
      setIsRiskLoading(false);
    }
  };

  const handleViewFullAssessmentFromModal = () => {
    setIsResultModalOpen(false);
    if (sessionId) {
      navigate(`/result?sessionId=${sessionId}`);
    } else {
      navigate('/result');
    }
  };

  const handleResetScan = () => {
    pollingRef.current = false;
    if (ingredientsImage?.previewUrl && ingredientsImage.previewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(ingredientsImage.previewUrl);
    }
    if (nutritionImage?.previewUrl && nutritionImage.previewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(nutritionImage.previewUrl);
    }
    setIngredientsImage(null);
    setNutritionImage(null);
    setSessionId(null);
    setOcrData(null);
    setNormalizedData(null);
    setClassificationData(null);
    setRiskData(null);
    setIsOcrModalOpen(false);
    setIsNormalizedModalOpen(false);
    setIsResultModalOpen(false);
    setIsOrchestrating(false);
    setOrchestrationError(null);
    setErrorMessage(null);
  };

  return (
    <div className="scan-page">
      <div className="scan-container">
        {/* Page Header */}
        <div className="scan-header">
          <span className="scan-badge">STEP 1: CAPTURE PACKAGING LABELS</span>
          <h1 className="scan-title">Scan Your Food</h1>
          <p className="scan-subtitle">
            Upload clear photos of food packaging to analyze health risks, additives, and nutrition facts against WHO and FSSAI standards.
          </p>
        </div>

        {/* Validation Error Alert */}
        {errorMessage && (
          <div className="scan-validation-alert" role="alert">
            <span aria-hidden="true">⚠️</span>
            <span>{errorMessage}</span>
          </div>
        )}

        {/* Dual Upload Cards: Ingredients & Nutrition */}
        <div className="scan-cards-grid">
          {/* Card 1: Ingredients */}
          <UploadCard
            id="ingredients-card"
            title="Ingredients Label"
            subtitle="Capture the full ingredients statement, chemical additives, and allergen declarations."
            icon="🏷️"
            selectedImage={ingredientsImage}
            onImageSelected={setIngredientsImage}
            onImageRemoved={() => setIngredientsImage(null)}
          />

          {/* Card 2: Nutrition Table */}
          <UploadCard
            id="nutrition-card"
            title="Nutrition Table"
            subtitle="Capture the nutrition facts panel (calories, sodium, sugars, and fats per serving/100g)."
            icon="📊"
            selectedImage={nutritionImage}
            onImageSelected={setNutritionImage}
            onImageRemoved={() => setNutritionImage(null)}
          />
        </div>

        {/* Primary Action Button (Unified Pipeline) */}
        <div className="scan-action-section">
          <Button
            variant="primary"
            size="large"
            fullWidth
            disabled={!hasAtLeastOneImage || isOrchestrating || isOcrLoading}
            onClick={handleStartUnifiedAnalysis}
            icon={<span aria-hidden="true">⚡</span>}
          >
            {isOrchestrating
              ? 'ANALYZING FOOD PRODUCT...'
              : hasAtLeastOneImage
              ? 'SCAN & ANALYZE FOOD'
              : 'CAPTURE AT LEAST ONE IMAGE'}
          </Button>

          {!hasAtLeastOneImage && (
            <p className="scan-status-hint">
              Please capture or upload at least one packaging image (ingredients or nutrition table) to proceed.
            </p>
          )}

          {/* Step-by-Step Inspection Toggle for Developers / Reviewers */}
          <div style={{ marginTop: '0.75rem', textAlign: 'center' }}>
            <button
              type="button"
              onClick={() => setIsStepByStepMode(!isStepByStepMode)}
              style={{
                background: 'transparent',
                border: 'none',
                color: 'var(--color-text-muted, #94a3b8)',
                fontSize: '0.8125rem',
                cursor: 'pointer',
                textDecoration: 'underline'
              }}
            >
              {isStepByStepMode ? '▲ Hide Advanced Inspection Mode' : '🛠️ Advanced: Inspect Step-by-Step Modals (M5–M8)'}
            </button>

            {isStepByStepMode && (
              <div style={{ marginTop: '0.5rem' }}>
                <Button
                  variant="outline"
                  size="small"
                  disabled={!hasAtLeastOneImage || isOcrLoading}
                  onClick={handleStartStepByStepInspection}
                  icon={<span>🔍</span>}
                >
                  {isOcrLoading ? 'Extracting OCR...' : 'Run Step-by-Step Modal Review'}
                </Button>
              </div>
            )}
          </div>

          <p className="scan-privacy-reminder">
            🛡️ <strong>Privacy Protection:</strong> Uploaded images are processed strictly in temporary memory and permanently destroyed immediately following analysis.
          </p>
        </div>

        {/* Photography Tips Card */}
        <GlassCard variant="subtle" padding="medium" className="scan-tips-card">
          <h4 className="tips-card-title">Photography Tips for Accurate Scanning</h4>
          <ul className="tips-list">
            <li>Ensure bright, even lighting without heavy glare on plastic wraps.</li>
            <li>Flatten curled packaging to keep all text lines in sharp focus.</li>
            <li>Keep the camera parallel to the nutritional table to avoid distortion.</li>
          </ul>
        </GlassCard>
      </div>

      {/* M11/M12 Unified Orchestration Progress Modal */}
      <ScanProgressModal
        isOpen={isOrchestrating}
        currentStage={orchestrationStage}
        errorMessage={orchestrationError}
        onRetry={handleStartUnifiedAnalysis}
        onCancel={handleCancelOrchestration}
      />

      {/* M5 Raw OCR Review Modal (Step-by-step mode) */}
      <OcrResultModal
        isOpen={isOcrModalOpen}
        ocrData={ocrData}
        isLoadingNormalization={isNormalizeLoading}
        onProceedToNormalization={handleProceedToNormalization}
        onClose={() => setIsOcrModalOpen(false)}
        onReset={handleResetScan}
      />

      {/* M6 AI-Normalized Food Data Review Modal (Step-by-step mode) */}
      <NormalizedFoodModal
        isOpen={isNormalizedModalOpen}
        normalizedData={normalizedData}
        isLoadingAnalysis={isRiskLoading}
        onProceedToRiskAnalysis={handleProceedToRiskAnalysis}
        onClose={() => setIsNormalizedModalOpen(false)}
        onReset={handleResetScan}
      />

      {/* M7 & M8 Product Categorization & Ingredient Risk Modal (Step-by-step mode) */}
      <AnalysisResultModal
        isOpen={isResultModalOpen}
        sessionId={sessionId}
        productName={normalizedData?.productName || null}
        classification={classificationData}
        riskResult={riskData}
        onClose={() => setIsResultModalOpen(false)}
        onReset={handleResetScan}
        onViewFullAssessment={handleViewFullAssessmentFromModal}
      />
    </div>
  );
};

export default ScanPage;
