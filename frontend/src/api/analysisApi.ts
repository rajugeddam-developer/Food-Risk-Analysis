/**
 * API client for transient food analysis sessions, offline OCR extraction, and AI normalization.
 *
 * Privacy guarantee:
 * - Communicates strictly with the backend Spring Boot API.
 * - No Gemini API keys or external AI calls are present in the frontend.
 * - Zero local or permanent database persistence of image blobs.
 */
import { getStoredToken } from './userApi';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || 'http://localhost:8080';

export interface AnalysisSessionResponse {
  sessionId: string;
  sessionToken: string;
  status: string;
  expiresAt: string;
  createdAt: string;
}

export interface OcrLabelResult {
  rawText: string | null;
  confidence: number | null;
  processingTimeMs: number;
  present: boolean;
}

export interface OcrAnalysisResponse {
  sessionId: string;
  status: string;
  ingredients: OcrLabelResult;
  nutrition: OcrLabelResult;
  totalProcessingTimeMs: number;
}

export interface NormalizeRequest {
  ingredientText?: string | null;
  nutritionText?: string | null;
}

export interface NormalizedIngredient {
  name: string;
  rawText: string;
  isAdditive: boolean;
  additiveCode: string | null;
  uncertain: boolean;
}

export interface NormalizedNutrition {
  basis: string | null;
  energyKcal: number | null;
  proteinG: number | null;
  carbohydrateG: number | null;
  totalSugarsG: number | null;
  addedSugarsG: number | null;
  totalFatG: number | null;
  saturatedFatG: number | null;
  transFatG: number | null;
  sodiumMg: number | null;
  fiberG: number | null;
  rawEntries: string[];
}

export interface NormalizedFoodData {
  productName: string | null;
  servingSize: string | null;
  servingSizeGrams: number | null;
  ingredients: NormalizedIngredient[];
  nutrition: NormalizedNutrition | null;
  uncertainties: string[];
}

export type FoodCategory = 'HUMAN_FOOD' | 'PET_FOOD' | 'ANIMAL_FEED' | 'NON_FOOD' | 'UNKNOWN';
export type ClassificationCertainty = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
export type ClassificationReasonCode =
  | 'EXPLICIT_HUMAN_FOOD_MARKER'
  | 'EXPLICIT_PET_FOOD_MARKER'
  | 'EXPLICIT_ANIMAL_FEED_MARKER'
  | 'EXPLICIT_NON_FOOD_MARKER'
  | 'CONFLICTING_EVIDENCE'
  | 'INSUFFICIENT_INFORMATION'
  | 'UNKNOWN_PRODUCT';

export interface FoodClassificationResult {
  category: FoodCategory;
  certainty: ClassificationCertainty;
  confidence: number | null;
  reasonCode: ClassificationReasonCode;
  reason: string;
  evidence: string[];
  warnings: string[];
}

export type IngredientRiskLevel =
  | 'NO_CONCERN'
  | 'NO_SPECIFIC_CONCERN'
  | 'POSITIVE'
  | 'LOW_ATTENTION'
  | 'MODERATE_ATTENTION'
  | 'HIGH_ATTENTION'
  | 'UNKNOWN';

export type RegulatoryStatus = 'PERMITTED' | 'RESTRICTED' | 'BANNED' | 'PROHIBITED' | 'UNKNOWN';
export type EvidenceStatus = 'STRONG' | 'MODERATE' | 'LIMITED' | 'MIXED' | 'SUPPORTED' | 'PARTIALLY_SUPPORTED' | 'INSUFFICIENT';

export interface IngredientRiskItem {
  originalIngredient: string;
  normalizedName: string | null;
  riskLevel: IngredientRiskLevel;
  reasons: string[];
  sources?: string[];
  sourceIds?: string[];
  evidenceStatus: EvidenceStatus;
  additiveCode: string | null;
  functionalClass: string | null;
  regulatoryStatus: RegulatoryStatus;
  summary?: string;
}

export interface IngredientRiskSummary {
  totalIngredients: number;
  identifiedIngredients: number;
  uncertainIngredients: number;
  additivesDetected: number;
  highAttentionIngredients: number;
  moderateAttentionIngredients: number;
  lowAttentionIngredients: number;
  noConcernIngredients: number;
  unknownIngredients: number;
}

export interface IngredientRiskAnalysisResult {
  sessionId: string;
  summary: IngredientRiskSummary;
  items: IngredientRiskItem[];
  evaluatedAt: string;
}

// ---------------------------------------------------------------------------
// Standardized API Error Handling (Milestone M13)
// ---------------------------------------------------------------------------

export interface StandardErrorResponse {
  timestamp?: string;
  status?: number;
  error?: string;
  code?: string;
  message?: string;
  path?: string;
  requestId?: string;
  errors?: string[];
}

export class ApiError extends Error {
  public readonly status: number;
  public readonly code?: string;
  public readonly requestId?: string;
  public readonly originalMessage?: string;

  constructor(status: number, message: string, code?: string, requestId?: string, originalMessage?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
    this.originalMessage = originalMessage;
  }
}

export function formatApiErrorMessage(error: unknown): { message: string; code?: string; requestId?: string } {
  if (error instanceof ApiError) {
    return {
      message: error.message,
      code: error.code,
      requestId: error.requestId
    };
  }

  if (error instanceof TypeError && error.message.toLowerCase().includes('failed to fetch')) {
    return {
      message: "We couldn't connect to the server. Check your internet connection and try again."
    };
  }

  if (error instanceof Error) {
    return {
      message: error.message || 'An unexpected error occurred. Please try again.'
    };
  }

  return {
    message: 'An unexpected error occurred. Please try again.'
  };
}

async function parseErrorResponse(response: Response): Promise<ApiError> {
  const requestId = response.headers.get('X-Request-ID') || undefined;
  let parsed: StandardErrorResponse = {};
  try {
    parsed = await response.json();
  } catch {
    // Non-JSON response
  }

  const code = parsed.code;
  let safeMessage = parsed.message;

  // Sanitize message if it leaks backend technical details
  if (safeMessage && (
    safeMessage.includes('Exception') ||
    safeMessage.includes('jdbc:') ||
    safeMessage.includes('org.postgresql') ||
    safeMessage.includes('org.hibernate') ||
    safeMessage.includes('NullPointerException') ||
    safeMessage.includes('SQLException')
  )) {
    safeMessage = undefined;
  }

  if (!safeMessage) {
    switch (response.status) {
      case 400:
        safeMessage = "Invalid request. Please check the provided food label images.";
        break;
      case 401:
        safeMessage = "Authentication required. Please sign in and try again.";
        break;
      case 403:
        safeMessage = "Access denied. You don't have permission to perform this action.";
        break;
      case 404:
        safeMessage = "The analysis session was not found. Please start a new scan.";
        break;
      case 408:
      case 504:
        safeMessage = "The analysis took too long to complete. Please try again.";
        break;
      case 410:
        safeMessage = "Your analysis session has expired. Please initiate a new scan.";
        break;
      case 413:
        safeMessage = "The image is too large. Please capture a smaller image.";
        break;
      case 429:
        safeMessage = "Too many requests. Please wait a moment and try again.";
        break;
      case 502:
        safeMessage = "Label normalization service returned an invalid response. Please try again.";
        break;
      case 503:
        safeMessage = "The service is temporarily unavailable. Please try again shortly.";
        break;
      case 500:
      default:
        safeMessage = "Something went wrong while analysing this product. Please try again.";
        break;
    }
  }

  return new ApiError(response.status, safeMessage, code, requestId || parsed.requestId, parsed.message);
}

/**
 * Creates a new transient food analysis session (15-minute TTL).
 */
export async function createAnalysisSession(): Promise<AnalysisSessionResponse> {
  const token = getStoredToken();
  const headers: HeadersInit = {
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/session`, {
    method: 'POST',
    headers
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<AnalysisSessionResponse>;
}

/**
 * Submits packaging label images for offline OCR text extraction.
 */
export async function runOcr(
  sessionId: string,
  ingredientFile?: File | null,
  nutritionFile?: File | null
): Promise<OcrAnalysisResponse> {
  const token = getStoredToken();
  const formData = new FormData();

  if (ingredientFile) {
    formData.append('ingredientImage', ingredientFile);
  }
  if (nutritionFile) {
    formData.append('nutritionImage', nutritionFile);
  }

  const headers: HeadersInit = {
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/${sessionId}/ocr`, {
    method: 'POST',
    headers,
    body: formData
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<OcrAnalysisResponse>;
}

/**
 * Normalizes raw OCR text into structured food information via Gemini AI.
 */
export async function normalizeFood(
  sessionId: string,
  request: NormalizeRequest
): Promise<NormalizedFoodData> {
  const token = getStoredToken();
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/${sessionId}/normalize`, {
    method: 'POST',
    headers,
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<NormalizedFoodData>;
}

/**
 * Evaluates food product categorization and consumption intent (M7).
 */
export async function classifyFood(sessionId: string): Promise<FoodClassificationResult> {
  const token = getStoredToken();
  const headers: HeadersInit = {
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/${sessionId}/classify`, {
    method: 'POST',
    headers
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<FoodClassificationResult>;
}

/**
 * Evaluates ingredients and additives against verified FSSAI/WHO rules (M8).
 */
export async function analyzeIngredientRisk(sessionId: string): Promise<IngredientRiskAnalysisResult> {
  const token = getStoredToken();
  const headers: HeadersInit = {
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/${sessionId}/ingredient-risk`, {
    method: 'POST',
    headers
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<IngredientRiskAnalysisResult>;
}

// ---------------------------------------------------------------------------
// Milestone M9: Nutrition Analysis & WHO/FSSAI Standards Types
// ---------------------------------------------------------------------------

export type NutrientType =
  | 'ENERGY'
  | 'TOTAL_FAT'
  | 'SATURATED_FAT'
  | 'TRANS_FAT'
  | 'CARBOHYDRATES'
  | 'TOTAL_SUGARS'
  | 'ADDED_SUGARS'
  | 'PROTEIN'
  | 'FIBRE'
  | 'SODIUM';

export type NutritionBasis = 'PER_100G' | 'PER_100ML' | 'PER_SERVING' | 'PER_PACKAGE' | 'UNKNOWN';
export type ReferenceType =
  | 'REGULATORY_LIMIT'
  | 'DIETARY_GUIDELINE'
  | 'NUTRITION_REFERENCE'
  | 'PROJECT_HEURISTIC'
  | 'PRODUCT_CLASSIFICATION_THRESHOLD'
  | 'DIETARY_RECOMMENDATION'
  | 'SCORING_THRESHOLD'
  | 'CLINICAL_LIMIT';
export type NutrientValueState = 'DETECTED_VALUE' | 'EXPLICIT_ZERO' | 'NOT_DECLARED';
export type ComparisonStatus = 'WITHIN_REFERENCE' | 'ABOVE_REFERENCE' | 'INSUFFICIENT_DATA' | 'NO_REFERENCE';
export type NutritionSeverity = 'LOW' | 'MODERATE' | 'HIGH' | 'UNKNOWN';
export type DataCompleteness = 'COMPLETE' | 'PARTIAL' | 'INSUFFICIENT';

export interface NutritionFinding {
  nutrient: NutrientType;
  observedValue: number | null;
  observedUnit: string;
  declaredBasis: NutritionBasis;
  servingSizeGrams: number | null;
  normalizedValue: number | null;
  normalizedBasis: NutritionBasis;
  referenceValue: number | null;
  referenceUnit: string;
  referenceBasis: NutritionBasis;
  referenceType: ReferenceType;
  valueState: NutrientValueState;
  status: ComparisonStatus;
  severity: NutritionSeverity;
  reason: string;
  sourceIds: string[];
}

export interface NutritionAnalysisResult {
  sessionId: string;
  dataCompleteness: DataCompleteness;
  declaredBasis: NutritionBasis;
  servingSizeGrams: number | null;
  findings: NutritionFinding[];
  positiveIndicators: string[];
  attentionIndicators: string[];
  missingNutrients: string[];
  referenceSources: string[];
  nutritionReferenceVersion: string;
  evaluatedAt: string;
}

/**
 * Evaluates normalized nutrition facts against authoritative WHO and FSSAI standards (M9).
 */
export async function analyzeNutrition(sessionId: string): Promise<NutritionAnalysisResult> {
  const token = getStoredToken();
  const headers: HeadersInit = {
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/${sessionId}/nutrition`, {
    method: 'POST',
    headers
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<NutritionAnalysisResult>;
}

// ---------------------------------------------------------------------------
// Milestone M10: Scoring Synthesis & Food Awareness Guidance Types
// ---------------------------------------------------------------------------

export type OverallFoodStatus =
  | 'GOOD_CHOICE'
  | 'NEEDS_ATTENTION'
  | 'HIGH_ATTENTION'
  | 'NOT_INTENDED_FOR_HUMAN_CONSUMPTION'
  | 'INSUFFICIENT_DATA';

export type HumanConsumptionStatus =
  | 'HUMAN_FOOD'
  | 'NOT_INTENDED_FOR_HUMAN_CONSUMPTION'
  | 'UNKNOWN';

export type AssessmentReliability = 'HIGH' | 'MEDIUM' | 'LOW';

export interface ScoreImpact {
  factor: string;
  impact: number;
  reason: string;
  source: string;
}

export interface PopulationGuidance {
  generalPopulation: string;
  children: string;
  specialDietaryNeeds: string;
}

export type AgeGroup = 'CHILDREN' | 'ADULTS' | 'OLDER_ADULTS';
export type AgeAttentionLevel = 'ACCEPTABLE' | 'MODERATE_ATTENTION' | 'HIGHER_ATTENTION';

export interface AgeGroupAwareness {
  ageGroup: AgeGroup;
  attentionLevel: AgeAttentionLevel;
  summary: string;
  contributingFactors: string[];
  sourceIds: string[];
}

export type ScoreEligibility = 'RATED' | 'PARTIALLY_RATED' | 'UNRATED';

export interface FoodRiskAssessment {
  sessionId: string;
  productCategory: FoodCategory;
  productCategoryReason: string;
  humanConsumptionStatus: HumanConsumptionStatus;
  overallScore: number | null;
  overallStatus: OverallFoodStatus;
  scoreEligibility?: ScoreEligibility;
  assessmentReliability: AssessmentReliability;
  nutritionDataCompleteness: DataCompleteness;
  classificationReliability: ClassificationCertainty;
  scoreBreakdown: ScoreImpact[];
  ingredientSummary: IngredientRiskSummary | null;
  items?: IngredientRiskItem[];
  nutritionSummary: NutritionAnalysisResult | null;
  keyConcerns: string[];
  positiveIndicators: string[];
  awarenessGuidance: string[];
  populationGuidance: PopulationGuidance;
  ageGroupAwareness?: AgeGroupAwareness[];
  sources: string[];
  limitations: string[];
  nutritionReferenceVersion: string;
  scoringRuleVersion: string;
  assessedAt: string;
}

export interface ExplainFindingRequest {
  itemName: string;
  itemType?: string;
}

export interface ExplainFindingResponse {
  itemName: string;
  explanation: string;
  sourceIds: string[];
  aiGenerated: boolean;
  explainedAt: string;
}

/**
 * Retrieves the synthesized Food Awareness Assessment and Food Awareness Score (M10).
 */
export async function getFoodRiskAssessment(sessionId: string): Promise<FoodRiskAssessment> {
  const token = getStoredToken();
  const headers: HeadersInit = {
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/${sessionId}/assessment`, {
    method: 'GET',
    headers
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<FoodRiskAssessment>;
}

/**
 * Requests an educational explanation for a verified finding with AI guardrails and fallback.
 */
export async function explainFinding(sessionId: string, itemName: string, itemType?: string): Promise<ExplainFindingResponse> {
  const token = getStoredToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/${sessionId}/explain`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ itemName, itemType })
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<ExplainFindingResponse>;
}

export interface AnalysisStatusResponse {
  sessionId: string;
  status: 'CREATED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'EXPIRED';
  currentStage: string;
  errorMessage: string | null;
  expiresAt: string;
}

/**
 * Starts full end-to-end analysis orchestrator (M11/M12).
 */
export async function startFoodAnalysis(
  sessionId: string,
  ingredientFile?: File | null,
  nutritionFile?: File | null
): Promise<AnalysisStatusResponse> {
  const token = getStoredToken();
  const formData = new FormData();
  if (ingredientFile) {
    formData.append('ingredientImage', ingredientFile);
  }
  if (nutritionFile) {
    formData.append('nutritionImage', nutritionFile);
  }

  const headers: HeadersInit = {
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/${sessionId}/analyze`, {
    method: 'POST',
    headers,
    body: formData
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<AnalysisStatusResponse>;
}

/**
 * Polls the current processing status and stage of an analysis session (M11/M12).
 */
export async function getAnalysisStatus(sessionId: string): Promise<AnalysisStatusResponse> {
  const token = getStoredToken();
  const headers: HeadersInit = {
    Accept: 'application/json'
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/api/analysis/${sessionId}/status`, {
    method: 'GET',
    headers
  });

  if (!response.ok) {
    throw await parseErrorResponse(response);
  }

  return response.json() as Promise<AnalysisStatusResponse>;
}

