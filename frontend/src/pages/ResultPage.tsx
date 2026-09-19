import React, { useState, useEffect } from 'react';
import { useRouter } from '../router/Router';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
import { Icon } from '../components/common/Icon';
import { ScoreGauge } from '../components/common/ScoreGauge';
import {
  getFoodRiskAssessment,
  explainFinding,
  formatApiErrorMessage,
  type FoodRiskAssessment,
  type OverallFoodStatus,
  type IngredientRiskItem,
  type NutritionFinding,
  type ExplainFindingResponse,
  type AgeGroupAwareness
} from '../api/analysisApi';
import './result.css';

// ---------------------------------------------------------------------------
// Authoritative Daily Nutritional Benchmarks (WHO / Codex / FSSAI Guidelines)
// ---------------------------------------------------------------------------
interface DailyBenchmark {
  standardDaily: number;
  unit: string;
  isLimit: boolean;
}

const DAILY_BENCHMARKS: Record<string, DailyBenchmark> = {
  ENERGY: { standardDaily: 2000, unit: 'kcal', isLimit: true },
  CALORIES: { standardDaily: 2000, unit: 'kcal', isLimit: true },
  TOTAL_FAT: { standardDaily: 70, unit: 'g', isLimit: true },
  FAT: { standardDaily: 70, unit: 'g', isLimit: true },
  SATURATED_FAT: { standardDaily: 20, unit: 'g', isLimit: true },
  TRANS_FAT: { standardDaily: 2, unit: 'g', isLimit: true },
  CARBOHYDRATES: { standardDaily: 260, unit: 'g', isLimit: true },
  CARBOHYDRATE: { standardDaily: 260, unit: 'g', isLimit: true },
  TOTAL_SUGARS: { standardDaily: 50, unit: 'g', isLimit: true },
  SUGAR: { standardDaily: 50, unit: 'g', isLimit: true },
  SUGARS: { standardDaily: 50, unit: 'g', isLimit: true },
  ADDED_SUGARS: { standardDaily: 25, unit: 'g', isLimit: true },
  PROTEIN: { standardDaily: 50, unit: 'g', isLimit: false },
  SODIUM: { standardDaily: 2000, unit: 'mg', isLimit: true },
  SALT: { standardDaily: 5, unit: 'g', isLimit: true },
  FIBRE: { standardDaily: 30, unit: 'g', isLimit: false },
  FIBER: { standardDaily: 30, unit: 'g', isLimit: false },
  DIETARY_FIBRE: { standardDaily: 30, unit: 'g', isLimit: false },
};

function getDailyPercentage(nutrient: string, observedValue: number | null): { pct: number; levelClass: string; text: string } | null {
  if (observedValue === null || observedValue === undefined || isNaN(observedValue)) {
    return null;
  }
  const key = nutrient.toUpperCase().replace(/\s+/g, '_');
  const benchmark = DAILY_BENCHMARKS[key];
  if (!benchmark) return null;

  const pct = Math.round((observedValue / benchmark.standardDaily) * 100);
  let levelClass = 'stat-pct--safe';
  if (benchmark.isLimit) {
    if (pct > 25) {
      levelClass = 'stat-pct--high';
    } else if (pct > 12) {
      levelClass = 'stat-pct--med';
    }
  } else {
    // Beneficial nutrient (e.g. protein, fibre)
    if (pct >= 20) {
      levelClass = 'stat-pct--safe';
    } else if (pct >= 10) {
      levelClass = 'stat-pct--med';
    } else {
      levelClass = 'stat-pct--safe';
    }
  }

  return { pct, levelClass, text: `${pct}% DV` };
}

// ---------------------------------------------------------------------------
// High-Fidelity Mock Assessment for Standalone / Demo Preview Mode
// ---------------------------------------------------------------------------
const DEMO_ASSESSMENTS: Record<string, FoodRiskAssessment> = {
  SAMPLE_CRISPS: {
    sessionId: 'demo-sample-crisps',
    productCategory: 'HUMAN_FOOD',
    productCategoryReason: 'Identified as packaged potato chips with standard food ingredients',
    humanConsumptionStatus: 'HUMAN_FOOD',
    overallScore: 62,
    overallStatus: 'NEEDS_ATTENTION',
    assessmentReliability: 'HIGH',
    nutritionDataCompleteness: 'COMPLETE',
    classificationReliability: 'HIGH',
    scoreBreakdown: [
      {
        factor: 'High Saturated Fat',
        impact: -15,
        reason: 'Saturated fat concentration (12.5g/100g) exceeds WHO dietary recommendation (>10g/100g)',
        source: 'WHO Dietary Guidelines'
      },
      {
        factor: 'High Sodium',
        impact: -15,
        reason: 'Sodium concentration (620mg/100g) exceeds WHO guideline benchmark (>600mg/100g)',
        source: 'WHO Free Sugars & Sodium Guideline'
      },
      {
        factor: 'Palm Oil (Refined)',
        impact: -10,
        reason: 'Highly refined tropical fat containing high levels of palmitic saturated fatty acids',
        source: 'FSSAI Guidance on Cooking Oils'
      },
      {
        factor: 'SODIUM_OVERLAP',
        impact: 5,
        reason: 'Anti-Double-Counting Composite: Deduplicated overlapping penalties between salt in ingredients and sodium in nutrition table',
        source: 'Food Risk Scoring Framework v1.0'
      },
      {
        factor: 'Source of Dietary Fibre',
        impact: 3,
        reason: 'Beneficial dietary fibre (3.2g/100g) provides satiety and digestive benefits',
        source: 'Codex Alimentarius'
      }
    ],
    ingredientSummary: {
      totalIngredients: 7,
      identifiedIngredients: 7,
      uncertainIngredients: 0,
      additivesDetected: 3,
      highAttentionIngredients: 0,
      moderateAttentionIngredients: 1,
      lowAttentionIngredients: 2,
      noConcernIngredients: 4,
      unknownIngredients: 0
    },
    nutritionSummary: {
      sessionId: 'demo-sample-crisps',
      dataCompleteness: 'COMPLETE',
      declaredBasis: 'PER_100G',
      servingSizeGrams: 30,
      findings: [
        {
          nutrient: 'ENERGY',
          observedValue: 536,
          observedUnit: 'kcal',
          declaredBasis: 'PER_100G',
          servingSizeGrams: 30,
          normalizedValue: 536,
          normalizedBasis: 'PER_100G',
          referenceValue: null,
          referenceUnit: 'kcal',
          referenceBasis: 'PER_100G',
          referenceType: 'PROJECT_HEURISTIC',
          valueState: 'DETECTED_VALUE',
          status: 'NO_REFERENCE',
          severity: 'LOW',
          reason: 'Caloric density 536 kcal per 100g',
          sourceIds: ['FSSAI']
        },
        {
          nutrient: 'TOTAL_FAT',
          observedValue: 34.0,
          observedUnit: 'g',
          declaredBasis: 'PER_100G',
          servingSizeGrams: 30,
          normalizedValue: 34.0,
          normalizedBasis: 'PER_100G',
          referenceValue: 17.5,
          referenceUnit: 'g',
          referenceBasis: 'PER_100G',
          referenceType: 'DIETARY_GUIDELINE',
          valueState: 'DETECTED_VALUE',
          status: 'ABOVE_REFERENCE',
          severity: 'HIGH',
          reason: 'High total fat content (34.0g/100g), exceeding benchmark of 17.5g/100g',
          sourceIds: ['WHO']
        },
        {
          nutrient: 'SATURATED_FAT',
          observedValue: 12.5,
          observedUnit: 'g',
          declaredBasis: 'PER_100G',
          servingSizeGrams: 30,
          normalizedValue: 12.5,
          normalizedBasis: 'PER_100G',
          referenceValue: 5.0,
          referenceUnit: 'g',
          referenceBasis: 'PER_100G',
          referenceType: 'DIETARY_GUIDELINE',
          valueState: 'DETECTED_VALUE',
          status: 'ABOVE_REFERENCE',
          severity: 'HIGH',
          reason: 'High saturated fatty acid content (12.5g/100g), exceeding benchmark of 5g/100g',
          sourceIds: ['WHO']
        },
        {
          nutrient: 'TRANS_FAT',
          observedValue: 0.1,
          observedUnit: 'g',
          declaredBasis: 'PER_100G',
          servingSizeGrams: 30,
          normalizedValue: 0.1,
          normalizedBasis: 'PER_100G',
          referenceValue: 0.2,
          referenceUnit: 'g',
          referenceBasis: 'PER_100G',
          referenceType: 'PRODUCT_CLASSIFICATION_THRESHOLD',
          valueState: 'DETECTED_VALUE',
          status: 'WITHIN_REFERENCE',
          severity: 'LOW',
          reason: 'Trans fat within configured product classification threshold (<0.2g/100g); compliant with FSSAI guidance',
          sourceIds: ['FSSAI']
        },
        {
          nutrient: 'TOTAL_SUGARS',
          observedValue: 1.8,
          observedUnit: 'g',
          declaredBasis: 'PER_100G',
          servingSizeGrams: 30,
          normalizedValue: 1.8,
          normalizedBasis: 'PER_100G',
          referenceValue: 5.0,
          referenceUnit: 'g',
          referenceBasis: 'PER_100G',
          referenceType: 'DIETARY_GUIDELINE',
          valueState: 'DETECTED_VALUE',
          status: 'WITHIN_REFERENCE',
          severity: 'LOW',
          reason: 'Low total sugars concentration (1.8g/100g)',
          sourceIds: ['WHO']
        },
        {
          nutrient: 'SODIUM',
          observedValue: 620,
          observedUnit: 'mg',
          declaredBasis: 'PER_100G',
          servingSizeGrams: 30,
          normalizedValue: 620,
          normalizedBasis: 'PER_100G',
          referenceValue: 600,
          referenceUnit: 'mg',
          referenceBasis: 'PER_100G',
          referenceType: 'DIETARY_GUIDELINE',
          valueState: 'DETECTED_VALUE',
          status: 'ABOVE_REFERENCE',
          severity: 'HIGH',
          reason: 'High sodium content (620mg/100g), exceeding benchmark of 600mg/100g',
          sourceIds: ['WHO']
        },
        {
          nutrient: 'FIBRE',
          observedValue: 3.2,
          observedUnit: 'g',
          declaredBasis: 'PER_100G',
          servingSizeGrams: 30,
          normalizedValue: 3.2,
          normalizedBasis: 'PER_100G',
          referenceValue: 3.0,
          referenceUnit: 'g',
          referenceBasis: 'PER_100G',
          referenceType: 'NUTRITION_REFERENCE',
          valueState: 'DETECTED_VALUE',
          status: 'WITHIN_REFERENCE',
          severity: 'LOW',
          reason: 'Good source of dietary fibre (3.2g/100g benchmark: 3.0g)',
          sourceIds: ['CODEX']
        }
      ],
      positiveIndicators: [
        'Compliant trans fat level (<0.2g/100g mandatory limit)',
        'Contains dietary fibre (3.2g/100g)',
        'Low total sugars (<5g/100g)'
      ],
      attentionIndicators: [
        'High sodium concentration (620mg/100g)',
        'Elevated saturated fat level (12.5g/100g)'
      ],
      missingNutrients: [],
      referenceSources: ['WHO Free Sugars & Sodium Guideline', 'FSSAI Food Safety Regulations 2022', 'Codex Alimentarius'],
      nutritionReferenceVersion: '2026.09',
      evaluatedAt: '2026-09-10T10:00:00Z'
    },
    keyConcerns: [
      'Elevated sodium density (620mg/100g): contributing over 30% of daily recommended sodium in one standard bag',
      'High saturated fat content (12.5g/100g): derived predominantly from refined palm oil',
      'Ultra-processed formulation with flavor enhancers and acidity regulators'
    ],
    positiveIndicators: [
      'Zero industrial trans fats detected (within mandatory FSSAI limit)',
      'Natural agricultural base (potatoes) providing 3.2g dietary fibre per 100g',
      'Free from artificial azo dyes or prohibited colorants'
    ],
    awarenessGuidance: [
      'Best enjoyed as an occasional savory treat rather than an everyday dietary staple.',
      'Pair with hydrating foods (fresh fruit, cucumbers, water) to balance elevated sodium intake.',
      'Check serving size: eating the entire package easily exceeds single-meal sodium benchmarks.'
    ],
    populationGuidance: {
      generalPopulation: 'Consume in moderation. High sodium and saturated fat density make this snack best suited for occasional consumption.',
      children: 'Limit portion sizes for children and adolescents to prevent early desensitization to high-sodium palates.',
      specialDietaryNeeds: 'Individuals with hypertension, congestive heart disease, or sodium-restricted diets should strictly limit or avoid this product.'
    },
    items: [
      {
        originalIngredient: 'Potatoes (Agricultural Produce)',
        normalizedName: 'Potato',
        riskLevel: 'NO_CONCERN',
        reasons: ['Whole agricultural root vegetable providing potassium and dietary fibre.'],
        sourceIds: ['CODEX'],
        evidenceStatus: 'SUPPORTED',
        additiveCode: null,
        functionalClass: null,
        regulatoryStatus: 'PERMITTED',
        summary: 'Agricultural root vegetable providing potassium and dietary fibre.'
      },
      {
        originalIngredient: 'Refined Palm Oil',
        normalizedName: 'Palm Oil',
        riskLevel: 'LOW_ATTENTION',
        reasons: ['Highly refined tropical oil containing high proportion of palmitic saturated fatty acid.'],
        sourceIds: ['FSSAI', 'WHO'],
        evidenceStatus: 'SUPPORTED',
        additiveCode: null,
        functionalClass: 'Cooking Medium / Fat',
        regulatoryStatus: 'PERMITTED',
        summary: 'Rich in saturated fatty acids; WHO guidance recommends moderating intake.'
      },
      {
        originalIngredient: 'Iodized Salt',
        normalizedName: 'Salt',
        riskLevel: 'NO_CONCERN',
        reasons: ['Primary contributor of sodium; evaluated quantitatively against declared sodium concentration.'],
        sourceIds: ['WHO', 'FSSAI'],
        evidenceStatus: 'SUPPORTED',
        additiveCode: null,
        functionalClass: 'Seasoning',
        regulatoryStatus: 'PERMITTED',
        summary: 'Culinary mineral seasoning; dietary impact evaluated quantitatively based on total sodium.'
      },
      {
        originalIngredient: 'Citric Acid (INS 330)',
        normalizedName: 'Citric Acid',
        riskLevel: 'NO_CONCERN',
        reasons: ['Naturally occurring organic acid commonly used to adjust acidity; permitted under GMP with no specific concern.'],
        sourceIds: ['FSSAI', 'CODEX', 'JECFA'],
        evidenceStatus: 'SUPPORTED',
        additiveCode: 'INS 330',
        functionalClass: 'Acidity Regulator',
        regulatoryStatus: 'PERMITTED',
        summary: 'Permitted acidity regulator with no specific concern identified at typical usage levels.'
      },
      {
        originalIngredient: 'Monosodium Glutamate (INS 621)',
        normalizedName: 'Monosodium Glutamate (MSG)',
        riskLevel: 'NO_CONCERN',
        reasons: ['Naturally derived or fermented umami flavor enhancer; evaluated by JECFA with ADI not specified.'],
        sourceIds: ['JECFA', 'CODEX', 'FSSAI'],
        evidenceStatus: 'SUPPORTED',
        additiveCode: 'INS 621',
        functionalClass: 'Flavor Enhancer',
        regulatoryStatus: 'PERMITTED',
        summary: 'Evaluated by JECFA with ADI not specified; permitted under Good Manufacturing Practice.'
      },
      {
        originalIngredient: 'Silicon Dioxide (INS 551)',
        normalizedName: 'Silicon Dioxide',
        riskLevel: 'NO_CONCERN',
        reasons: ['Inert mineral-derived anti-caking agent ensuring free flow of seasoning powders; permitted under GMP limits.'],
        sourceIds: ['FSSAI', 'CODEX'],
        evidenceStatus: 'SUPPORTED',
        additiveCode: 'INS 551',
        functionalClass: 'Anti-Caking Agent',
        regulatoryStatus: 'PERMITTED',
        summary: 'Inert anti-caking agent permitted under Good Manufacturing Practice limits.'
      }
    ],
    sources: [
      'WHO Guideline: Sodium intake for adults and children (2012)',
      'FSSAI Food Safety and Standards (Packaging and Labelling) Regulations (2022)',
      'Codex Alimentarius: Guidelines on Nutrition Labelling (CAC/GL 2-1985)'
    ],
    limitations: [
      'Analysis evaluated strictly against declared packaged nutrition facts and ingredient statement.',
      'Does not account for individual metabolic differences, concurrent meal consumption, or physical activity levels.'
    ],
    nutritionReferenceVersion: '2026.09',
    scoringRuleVersion: '1.0',
    assessedAt: '2026-09-10T10:00:00Z'
  },
  PET_FOOD_EXAMPLE: {
    sessionId: 'demo-pet-food',
    productCategory: 'PET_FOOD',
    productCategoryReason: 'Explicit packaging markers identify formulation as canine companion nutrition',
    humanConsumptionStatus: 'NOT_INTENDED_FOR_HUMAN_CONSUMPTION',
    overallScore: null,
    overallStatus: 'NOT_INTENDED_FOR_HUMAN_CONSUMPTION',
    assessmentReliability: 'HIGH',
    nutritionDataCompleteness: 'PARTIAL',
    classificationReliability: 'HIGH',
    scoreBreakdown: [],
    ingredientSummary: null,
    items: [],
    nutritionSummary: null,
    keyConcerns: [
      'CRITICAL: Formulated strictly for animal physiology (pet food). DO NOT CONSUME as human food.',
      'Mineral ratios, bone meal, and ash content are formulated for canine digestion and may cause acute renal stress in humans.'
    ],
    positiveIndicators: [],
    awarenessGuidance: [
      'Keep stored securely away from kitchen pantries and out of reach of young children.',
      'If accidentally consumed by a human in significant quantity, consult medical advice or poison control.'
    ],
    populationGuidance: {
      generalPopulation: 'NOT SAFE FOR HUMAN CONSUMPTION. Do not ingest.',
      children: 'HIGH RISK: Ensure children do not mistake pet kibbles or treats for breakfast cereal or human snacks.',
      specialDietaryNeeds: 'Not food. Strictly prohibited.'
    },
    sources: ['AAFCO Official Publication (2023)', 'FSSAI Pet Food Guidelines'],
    limitations: ['Non-human food product. All human nutritional assessment permanently withheld.'],
    nutritionReferenceVersion: '2026.09',
    scoringRuleVersion: '1.0',
    assessedAt: '2026-09-10T10:00:00Z'
  },
  NON_FOOD_EXAMPLE: {
    sessionId: 'demo-non-food',
    productCategory: 'NON_FOOD',
    productCategoryReason: 'Identified as domestic surface cleaner / disinfectant surfactant formulation',
    humanConsumptionStatus: 'NOT_INTENDED_FOR_HUMAN_CONSUMPTION',
    overallScore: null,
    overallStatus: 'NOT_INTENDED_FOR_HUMAN_CONSUMPTION',
    assessmentReliability: 'HIGH',
    nutritionDataCompleteness: 'INSUFFICIENT',
    classificationReliability: 'HIGH',
    scoreBreakdown: [],
    ingredientSummary: null,
    items: [],
    nutritionSummary: null,
    keyConcerns: [
      'POISON HAZARD: Industrial chemical / cleaning preparation. Ingestion is acutely toxic.',
      'Contains surfactants or chemical agents unsafe for biological ingestion.'
    ],
    positiveIndicators: [],
    awarenessGuidance: [
      'DANGER: Ingestion may result in chemical burns, gastrointestinal distress, or severe poisoning.',
      'In case of accidental ingestion, do not induce vomiting and seek emergency medical assistance immediately.'
    ],
    populationGuidance: {
      generalPopulation: 'POISON WARNING: Strictly not food. Keep in original packaging with safety seals.',
      children: 'Store in locked cabinets out of reach of toddlers and children.',
      specialDietaryNeeds: 'Not food. Strictly prohibited.'
    },
    sources: ['Globally Harmonized System of Classification and Labelling of Chemicals (GHS)'],
    limitations: ['Non-food chemical item. All nutritional evaluation permanently withheld.'],
    nutritionReferenceVersion: '2026.09',
    scoringRuleVersion: '1.0',
    assessedAt: '2026-09-10T10:00:00Z'
  }
};

export const ResultPage: React.FC = () => {
  const { navigate, currentPath } = useRouter();

  // Active Tab state
  const [activeTab, setActiveTab] = useState<'overview' | 'ingredients' | 'nutrition' | 'breakdown' | 'standards'>('overview');
  const [ingredientFilter, setIngredientFilter] = useState<'all' | 'attention' | 'additives' | 'permitted' | 'unknown'>('all');

  // Know More (Explanation) state
  const [explainingItem, setExplainingItem] = useState<string | null>(null);
  const [explanationLoading, setExplanationLoading] = useState<boolean>(false);
  const [explanationResult, setExplanationResult] = useState<ExplainFindingResponse | null>(null);
  const [explanationError, setExplanationError] = useState<string | null>(null);

  // Live Assessment State
  const [assessment, setAssessment] = useState<FoodRiskAssessment | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [shareToast, setShareToast] = useState<string | null>(null);

  // Demo selector state
  const [demoKey, setDemoKey] = useState<string>('SAMPLE_CRISPS');
  const [isDemoMode, setIsDemoMode] = useState<boolean>(false);

  // Load session assessment
  useEffect(() => {
    let isMounted = true;

    async function loadAssessment() {
      setIsLoading(true);
      setFetchError(null);

      // Extract sessionId from URL query params or currentPath or sessionStorage
      let targetSessionId: string | null = null;
      if (typeof window !== 'undefined' && window.location.search) {
        const urlParams = new URLSearchParams(window.location.search);
        targetSessionId = urlParams.get('sessionId');
      }
      if (!targetSessionId && currentPath && currentPath.includes('?')) {
        const urlParams = new URLSearchParams(currentPath.substring(currentPath.indexOf('?')));
        targetSessionId = urlParams.get('sessionId');
      }
      if (!targetSessionId && typeof window !== 'undefined') {
        targetSessionId = sessionStorage.getItem('lastAnalysisSessionId');
      }

      if (targetSessionId) {
        try {
          const liveData = await getFoodRiskAssessment(targetSessionId);
          if (isMounted) {
            setAssessment(liveData);
            setIsDemoMode(false);
            setIsLoading(false);
          }
          return;
        } catch (err: unknown) {
          console.warn('Could not fetch live assessment:', err);
          // If live fetch failed (e.g. session expired or offline), fallback to demo with notice
          if (isMounted) {
            const errInfo = formatApiErrorMessage(err);
            setFetchError(`${errInfo.message} Displaying sample preview.`);
            setAssessment(DEMO_ASSESSMENTS.SAMPLE_CRISPS);
            setIsDemoMode(true);
            setIsLoading(false);
          }
          return;
        }
      }

      // Standalone access fallback to demo sample
      if (isMounted) {
        setAssessment(DEMO_ASSESSMENTS.SAMPLE_CRISPS);
        setIsDemoMode(true);
        setIsLoading(false);
      }
    }

    loadAssessment();

    return () => {
      isMounted = false;
    };
  }, [currentPath]);

  const handleSwitchDemoState = (key: string) => {
    setDemoKey(key);
    if (DEMO_ASSESSMENTS[key]) {
      setAssessment(DEMO_ASSESSMENTS[key]);
    }
  };

  const handleShareClick = async () => {
    const title = 'Food Awareness Assessment';
    const text = assessment?.overallScore !== null && assessment?.overallScore !== undefined
      ? `Food Risk Assessment: Score ${assessment.overallScore}/100 (${assessment.overallStatus}) on Food Risk Analysis PWA.`
      : 'Check out the food safety assessment on Food Risk Analysis PWA.';

    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title,
          text,
          url: window.location.href
        });
      } catch {
        // User dismiss
      }
    } else if (typeof navigator !== 'undefined' && navigator.clipboard) {
      await navigator.clipboard.writeText(window.location.href);
      setShareToast('Assessment link copied to clipboard!');
      setTimeout(() => setShareToast(null), 2500);
    }
  };

  const handlePrint = () => {
    if (typeof window !== 'undefined') {
      window.print();
    }
  };

  // Helper mappings
  const currentData = assessment || DEMO_ASSESSMENTS.SAMPLE_CRISPS;
  const isNonHumanFood =
    currentData.humanConsumptionStatus === 'NOT_INTENDED_FOR_HUMAN_CONSUMPTION' ||
    currentData.productCategory === 'PET_FOOD' ||
    currentData.productCategory === 'ANIMAL_FEED' ||
    currentData.productCategory === 'NON_FOOD';

  const getProductName = () => {
    if (currentData.productCategoryReason) {
      const match = currentData.productCategoryReason.match(/Identified as ([^.]+)/i);
      if (match && match[1]) return match[1].trim();
    }
    if (currentData.items && currentData.items.length > 0) {
      const firstValid = currentData.items.find(i => i.normalizedName && !i.normalizedName.toLowerCase().includes('water'));
      if (firstValid?.normalizedName) {
        return `${firstValid.normalizedName}-Based Food`;
      }
    }
    return currentData.productCategory === 'HUMAN_FOOD'
      ? 'Packaged Food Product'
      : currentData.productCategory.replace(/_/g, ' ');
  };

  // Map overall status to score gauge band
  const getRiskBand = (_status: OverallFoodStatus, score: number | null): 'LOW' | 'MODERATE CONCERN' | 'HIGH' | 'CRITICAL' => {
    if (isNonHumanFood) return 'CRITICAL';
    if (score === null) return 'HIGH';
    if (score >= 80) return 'LOW';
    if (score >= 50) return 'MODERATE CONCERN';
    return 'CRITICAL';
  };

  const getStatusDisplay = (status: OverallFoodStatus) => {
    switch (status) {
      case 'GOOD_CHOICE':
        return { label: 'GOOD CHOICE', color: '#10b981', bg: 'rgba(16, 185, 129, 0.15)', icon: '🟢' };
      case 'NEEDS_ATTENTION':
        return { label: 'NEEDS ATTENTION', color: '#f59e0b', bg: 'rgba(245, 158, 11, 0.15)', icon: '🟡' };
      case 'HIGH_ATTENTION':
        return { label: 'HIGH ATTENTION', color: '#ef4444', bg: 'rgba(239, 68, 68, 0.15)', icon: '🔴' };
      case 'NOT_INTENDED_FOR_HUMAN_CONSUMPTION':
        return { label: 'NOT FOR HUMAN CONSUMPTION', color: '#f87171', bg: 'rgba(220, 38, 38, 0.25)', icon: '🛑' };
      case 'INSUFFICIENT_DATA':
      default:
        return { label: 'INSUFFICIENT DATA', color: '#94a3b8', bg: 'rgba(148, 163, 184, 0.15)', icon: '⚪' };
    }
  };

  const statusMeta = getStatusDisplay(currentData.overallStatus);

  const handleExplain = async (itemName: string, itemType?: string) => {
    if (!itemName) return;
    setExplainingItem(itemName);
    setExplanationLoading(true);
    setExplanationError(null);
    setExplanationResult(null);

    if (isDemoMode) {
      setTimeout(() => {
        setExplanationResult({
          itemName,
          explanation: `Demonstration finding explanation: ${itemName} is evaluated based on standard food additive and nutritional safety references under FSSAI and WHO guidance.`,
          sourceIds: ['FSSAI', 'WHO'],
          aiGenerated: false,
          explainedAt: new Date().toISOString()
        });
        setExplanationLoading(false);
      }, 400);
      return;
    }

    try {
      const res = await explainFinding(currentData.sessionId, itemName, itemType);
      setExplanationResult(res);
    } catch (err: unknown) {
      const errInfo = formatApiErrorMessage(err);
      setExplanationError(errInfo.message);
    } finally {
      setExplanationLoading(false);
    }
  };

  // Live evaluated ingredients from assessment
  const rawIngredients: IngredientRiskItem[] = currentData?.items || [];

  // Filter ingredients
  const filteredIngredients = rawIngredients.filter(item => {
    if (ingredientFilter === 'attention') {
      return item.riskLevel === 'HIGH_ATTENTION' || item.riskLevel === 'MODERATE_ATTENTION';
    }
    if (ingredientFilter === 'additives') {
      return Boolean(item.additiveCode);
    }
    if (ingredientFilter === 'permitted') {
      return item.regulatoryStatus === 'PERMITTED';
    }
    if (ingredientFilter === 'unknown') {
      return item.riskLevel === 'UNKNOWN';
    }
    return true;
  });

  if (isLoading && !assessment && !isDemoMode) {
    return (
      <div className="result-page">
        <div className="result-container" style={{ textAlign: 'center', padding: '6rem 1rem' }}>
          <div style={{
            margin: '0 auto 1.5rem',
            width: 48,
            height: 48,
            border: '4px solid rgba(255,255,255,0.1)',
            borderTopColor: 'var(--color-accent-teal, #2dd4bf)',
            borderRadius: '50%',
            animation: 'spin 1s linear infinite'
          }} />
          <h2 style={{ color: 'var(--color-text-primary, #f8fafc)', fontSize: '1.5rem', marginBottom: '0.5rem' }}>
            Loading Food Risk Assessment...
          </h2>
          <p style={{ color: 'var(--color-text-muted, #94a3b8)' }}>
            Retrieving verified safety evaluation and nutrition findings
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="result-page">
      <div className="result-container">
        {/* Header Breadcrumb / Title */}
        <div className="result-header">
          <div className="result-badge">
            <span className="result-badge-dot" />
            <span>FOOD AWARENESS REPORT</span>
          </div>
          <h1 className="result-title">
            Food Risk <span className="text-gradient-cyan">Assessment</span>
          </h1>
          <p className="result-product-meta">
            {currentData.productCategoryReason || 'Packaged Consumable Product'}
            <span className="meta-separator">•</span>
            Assessed via FSSAI &amp; WHO Standards
          </p>
        </div>

        {/* Demo Mode Notice Banner */}
        {isDemoMode && (
          <div className="demo-preview-banner">
            <div className="demo-banner-content">
              <Icon name="sparkles" size={18} color="#20c9ff" className="demo-banner-icon" />
              <div className="demo-banner-text">
                <strong>Interactive Demo Preview:</strong> Viewing high-fidelity verification dataset.
                You can switch between human food, pet food, and non-food test scenarios below.
              </div>
            </div>
            <div className="demo-state-chips">
              <button
                type="button"
                className={`demo-chip ${demoKey === 'SAMPLE_CRISPS' ? 'demo-chip--active' : ''}`}
                onClick={() => handleSwitchDemoState('SAMPLE_CRISPS')}
              >
                Savory Crisps (Human Food)
              </button>
              <button
                type="button"
                className={`demo-chip ${demoKey === 'PET_FOOD_EXAMPLE' ? 'demo-chip--active' : ''}`}
                onClick={() => handleSwitchDemoState('PET_FOOD_EXAMPLE')}
              >
                Pet Food Warning
              </button>
              <button
                type="button"
                className={`demo-chip ${demoKey === 'NON_FOOD_EXAMPLE' ? 'demo-chip--active' : ''}`}
                onClick={() => handleSwitchDemoState('NON_FOOD_EXAMPLE')}
              >
                Chemical Warning
              </button>
            </div>
          </div>
        )}

        {fetchError && (
          <div className="scan-validation-alert" role="alert" style={{ marginBottom: '1.5rem' }}>
            <Icon name="alert-triangle" size={18} color="#f87171" />
            <span>{fetchError}</span>
          </div>
        )}

        {shareToast && (
          <div className="result-toast" role="status">
            <span>{shareToast}</span>
          </div>
        )}

        {/* HIGH-VISIBILITY HUMAN CONSUMPTION STATUS BANNER */}
        {isNonHumanFood ? (
          <div className="consumption-warning-banner" role="alert">
            <div className="warning-banner-icon">
              <Icon name="alert-triangle" size={28} color="#ef4444" />
            </div>
            <div className="warning-banner-body">
              <h3 className="warning-banner-title">NOT INTENDED FOR HUMAN CONSUMPTION</h3>
              <p className="warning-banner-desc">
                {currentData.productCategory === 'PET_FOOD'
                  ? 'CRITICAL WARNING: This product is formulated specifically for animal physiology (pet food). Nutritional ratios, ash content, and bacterial allowances are not safe for human biology.'
                  : currentData.productCategory === 'NON_FOOD'
                  ? 'POISON & CHEMICAL HAZARD: Packaging indicates a household chemical, detergent, or cosmetic product. Swallowing may cause acute chemical toxicity.'
                  : 'This product has been classified as animal feed or non-food. Do NOT consume as human nutrition.'}
              </p>
            </div>
          </div>
        ) : (
          <div className="consumption-verified-banner" role="status">
            <span className="verified-icon">
              <Icon name="check" size={18} color="#10b981" />
            </span>
            <span className="verified-text">
              <strong>Verified Packaged Human Food:</strong> Formulated and packaged for human dietary consumption.
            </span>
          </div>
        )}

        {/* TOP SUMMARY CARD: SCORE GAUGE & ATTENTION STATUS */}
        <GlassCard variant="elevated" padding="large" className="result-summary-card">
          <div className="result-summary-grid">
            {/* Score Ring Gauge */}
            <div className="result-gauge-col">
              {currentData.overallScore !== null && currentData.overallScore !== undefined ? (
                <>
                  <ScoreGauge
                    score={currentData.overallScore}
                    maxScore={100}
                    riskBand={getRiskBand(currentData.overallStatus, currentData.overallScore)}
                    size="normal"
                  />
                  <div className="result-score-meta">
                    <span className="result-score-caption">Food Awareness Score (0–100)</span>
                    <span
                      className="result-status-pill"
                      style={{ color: statusMeta.color, background: statusMeta.bg, border: `1px solid ${statusMeta.color}40` }}
                    >
                      {statusMeta.label}
                    </span>
                  </div>
                </>
              ) : (
                <div className="score-withheld-box">
                  <div className="score-withheld-icon">
                    {currentData.scoreEligibility === 'UNRATED' ? (
                      <Icon name="file-text" size={36} color="#94a3b8" />
                    ) : (
                      <Icon name="alert-triangle" size={36} color="#fbbf24" />
                    )}
                  </div>
                  <h3 className="score-withheld-title">
                    {currentData.scoreEligibility === 'UNRATED' ? 'UNRATED' : 'Score Withheld'}
                  </h3>
                  <p className="score-withheld-reason">
                    {currentData.scoreEligibility === 'UNRATED'
                      ? 'Nutrition facts are undeclared or missing. Overall score is withheld to avoid false assurance without nutritional data.'
                      : 'Human nutritional scores are not applicable to non-human or unverified products.'}
                  </p>
                </div>
              )}
            </div>

            {/* Assessment Identity & Reliability Metrics */}
            <div className="result-classification-col">
              <div className="identity-header">
                <div className="category-pill-row">
                  <span className="category-badge-pill">
                    CATEGORY: {currentData.productCategory.replace(/_/g, ' ')}
                  </span>
                  <span className="reliability-badge-pill">
                    RELIABILITY: {currentData.assessmentReliability}
                  </span>
                  {currentData.nutritionDataCompleteness && (
                    <span className="completeness-badge-pill">
                      DATA: {currentData.nutritionDataCompleteness}
                    </span>
                  )}
                </div>
                <h2 className="summary-product-name">
                  {getProductName()}
                </h2>
              </div>

              {/* Quick Guidance Box */}
              <div className="result-guidance-box">
                <h4 className="guidance-title">Consumer Eating Guidance</h4>
                <ul className="guidance-list">
                  {currentData.awarenessGuidance.map((item, idx) => (
                    <li key={idx} className="guidance-item">
                      <span className="guidance-bullet" aria-hidden="true">•</span>
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        </GlassCard>

        {/* KEY CONCERNS & POSITIVE HIGHLIGHTS (HIGH-PRIORITY TILES) */}
        <div className="highlights-grid">
          {/* Key Concerns Card */}
          <GlassCard variant="default" padding="medium" className="concern-card">
            <div className="highlight-header">
              <Icon name="alert-triangle" size={18} color="#f87171" className="highlight-icon" />
              <h4 className="highlight-title">Key Attention Flags</h4>
            </div>
            {currentData.keyConcerns.length > 0 ? (
              <ul className="highlight-list">
                {currentData.keyConcerns.map((c, idx) => (
                  <li key={idx} className="highlight-item highlight-item--concern">
                    {c}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="highlight-empty">No critical hazards detected in this formulation.</p>
            )}
          </GlassCard>

          {/* Positive Indicators Card */}
          <GlassCard variant="default" padding="medium" className="positive-card">
            <div className="highlight-header">
              <Icon name="sparkles" size={18} color="#34d399" className="highlight-icon" />
              <h4 className="highlight-title">Positive Attributes</h4>
            </div>
            {currentData.positiveIndicators.length > 0 ? (
              <ul className="highlight-list">
                {currentData.positiveIndicators.map((p, idx) => (
                  <li key={idx} className="highlight-item highlight-item--positive">
                    {p}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="highlight-empty">No positive nutritional indicators declared.</p>
            )}
          </GlassCard>
        </div>

        {/* TABBED DETAILED BREAKDOWN */}
        <div className="result-tabs-container">
          <div className="result-tab-bar" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'overview'}
              className={`result-tab-btn ${activeTab === 'overview' ? 'result-tab-btn--active' : ''}`}
              onClick={() => setActiveTab('overview')}
            >
              Overview &amp; Guidance
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'ingredients'}
              className={`result-tab-btn ${activeTab === 'ingredients' ? 'result-tab-btn--active' : ''}`}
              onClick={() => setActiveTab('ingredients')}
            >
              Ingredients &amp; Additives ({rawIngredients.length})
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'nutrition'}
              className={`result-tab-btn ${activeTab === 'nutrition' ? 'result-tab-btn--active' : ''}`}
              onClick={() => setActiveTab('nutrition')}
            >
              Nutrition Facts ({currentData.nutritionSummary?.findings.length || 0})
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'breakdown'}
              className={`result-tab-btn ${activeTab === 'breakdown' ? 'result-tab-btn--active' : ''}`}
              onClick={() => setActiveTab('breakdown')}
            >
              Score Breakdown
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'standards'}
              className={`result-tab-btn ${activeTab === 'standards' ? 'result-tab-btn--active' : ''}`}
              onClick={() => setActiveTab('standards')}
            >
              WHO &amp; FSSAI Standards
            </button>
          </div>

          {/* TAB 1: OVERVIEW & POPULATION GUIDANCE */}
          {activeTab === 'overview' && (
            <GlassCard variant="default" padding="large" className="analysis-tab-card">
              <div className="analysis-card-header">
                <span className="analysis-card-icon" aria-hidden="true">👥</span>
                <div>
                  <h3 className="analysis-card-title">Actionable Demographic Guidance</h3>
                  <p className="analysis-card-subtitle">
                    Non-medical nutritional guidance based on WHO, ICMR, and FSSAI population health benchmarks.
                  </p>
                </div>
              </div>

              {currentData.ageGroupAwareness && currentData.ageGroupAwareness.length > 0 ? (
                <div className="age-awareness-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '1rem', marginTop: '1rem', marginBottom: '1.5rem' }}>
                  {currentData.ageGroupAwareness.map((ag: AgeGroupAwareness) => {
                    const isHigh = ag.attentionLevel === 'HIGHER_ATTENTION';
                    const isMod = ag.attentionLevel === 'MODERATE_ATTENTION';
                    const badgeBg = isHigh ? 'rgba(239, 68, 68, 0.15)' : isMod ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)';
                    const badgeColor = isHigh ? '#ef4444' : isMod ? '#f59e0b' : '#10b981';
                    const icon = ag.ageGroup === 'CHILDREN' ? '👶' : ag.ageGroup === 'OLDER_ADULTS' ? '🧓' : '👤';
                    const title = ag.ageGroup === 'CHILDREN' ? 'Children' : ag.ageGroup === 'OLDER_ADULTS' ? 'Older Adults' : 'Adults';

                    return (
                      <div key={ag.ageGroup} className="age-group-card" style={{
                        background: 'rgba(255, 255, 255, 0.03)',
                        border: `1px solid ${isHigh ? 'rgba(239, 68, 68, 0.3)' : isMod ? 'rgba(245, 158, 11, 0.3)' : 'rgba(255, 255, 255, 0.08)'}`,
                        borderRadius: '12px',
                        padding: '1.25rem'
                      }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span style={{ fontSize: '1.3rem' }}>{icon}</span>
                            <strong style={{ fontSize: '1rem', color: '#f8fafc' }}>{title}</strong>
                          </div>
                          <span style={{
                            background: badgeBg,
                            color: badgeColor,
                            padding: '3px 8px',
                            borderRadius: '999px',
                            fontSize: '0.75rem',
                            fontWeight: 600
                          }}>
                            {ag.attentionLevel.replace(/_/g, ' ')}
                          </span>
                        </div>
                        <p style={{ color: '#cbd5e1', fontSize: '0.88rem', lineHeight: '1.45', marginBottom: '0.75rem' }}>
                          {ag.summary}
                        </p>
                        {ag.contributingFactors && ag.contributingFactors.length > 0 && (
                          <div style={{ marginBottom: '0.5rem' }}>
                            <span style={{ fontSize: '0.72rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Key Factors:</span>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '4px' }}>
                              {ag.contributingFactors.map((f, i) => (
                                <span key={i} style={{ background: 'rgba(255,255,255,0.06)', padding: '2px 6px', borderRadius: '4px', fontSize: '0.75rem', color: '#e2e8f0' }}>
                                  {f}
                                </span>
                              ))}
                            </div>
                          </div>
                        )}
                        {ag.sourceIds && ag.sourceIds.length > 0 && (
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                            Sources: {ag.sourceIds.join(', ')}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="population-guidance-grid">
                  <div className="population-tile">
                    <div className="pop-icon">👤</div>
                    <h5 className="pop-title">General Population</h5>
                    <p className="pop-desc">{currentData.populationGuidance.generalPopulation}</p>
                  </div>
                  <div className="population-tile">
                    <div className="pop-icon">👶</div>
                    <h5 className="pop-title">Children &amp; Adolescents</h5>
                    <p className="pop-desc">{currentData.populationGuidance.children}</p>
                  </div>
                  <div className="population-tile">
                    <div className="pop-icon">🩺</div>
                    <h5 className="pop-title">Special Dietary Needs</h5>
                    <p className="pop-desc">{currentData.populationGuidance.specialDietaryNeeds}</p>
                  </div>
                </div>
              )}

              <div className="limitations-box">
                <h5 className="limitations-heading">Analysis Scope &amp; Evidence Boundaries</h5>
                <ul className="limitations-list">
                  {currentData.limitations.map((lim, idx) => (
                    <li key={idx}>{lim}</li>
                  ))}
                </ul>
              </div>
            </GlassCard>
          )}

          {/* TAB 2: INGREDIENT & ADDITIVE EVALUATION */}
          {activeTab === 'ingredients' && (
            <GlassCard variant="default" padding="large" className="analysis-tab-card">
              <div className="analysis-card-header">
                <span className="analysis-card-icon" aria-hidden="true">🏷️</span>
                <div>
                  <h3 className="analysis-card-title">Verified Ingredient &amp; Additive Analysis</h3>
                  <p className="analysis-card-subtitle">
                    Evaluation of chemical additives, functional classes, and regulatory permissions under FSSAI &amp; WHO rules.
                  </p>
                </div>
              </div>

              {/* Filter Chips */}
              <div className="ingredient-filter-chips">
                <button
                  type="button"
                  className={`filter-chip ${ingredientFilter === 'all' ? 'filter-chip--active' : ''}`}
                  onClick={() => setIngredientFilter('all')}
                >
                  All Ingredients ({rawIngredients.length})
                </button>
                <button
                  type="button"
                  className={`filter-chip ${ingredientFilter === 'attention' ? 'filter-chip--active' : ''}`}
                  onClick={() => setIngredientFilter('attention')}
                >
                  Attention Items
                </button>
                <button
                  type="button"
                  className={`filter-chip ${ingredientFilter === 'additives' ? 'filter-chip--active' : ''}`}
                  onClick={() => setIngredientFilter('additives')}
                >
                  E/INS Additives
                </button>
                <button
                  type="button"
                  className={`filter-chip ${ingredientFilter === 'permitted' ? 'filter-chip--active' : ''}`}
                  onClick={() => setIngredientFilter('permitted')}
                >
                  Permitted Substances
                </button>
                <button
                  type="button"
                  className={`filter-chip ${ingredientFilter === 'unknown' ? 'filter-chip--active' : ''}`}
                  onClick={() => setIngredientFilter('unknown')}
                >
                  Unknown / Unverified
                </button>
              </div>

              {/* Ingredients List */}
              <div className="ingredients-table-wrap">
                {rawIngredients.length === 0 ? (
                  <div className="empty-ingredients-state" style={{ textAlign: 'center', padding: '3rem 1rem', color: '#94a3b8' }}>
                    <div style={{ fontSize: '2.5rem', marginBottom: '0.75rem' }}>🔍</div>
                    <h4 style={{ color: '#f8fafc', marginBottom: '0.25rem' }}>No ingredients detected</h4>
                    <p style={{ margin: 0, fontSize: '0.9rem' }}>No ingredient or additive text was identified in the captured packaging label.</p>
                  </div>
                ) : filteredIngredients.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '2rem 1rem', color: '#94a3b8' }}>
                    <p style={{ margin: 0 }}>No ingredients match the selected filter.</p>
                  </div>
                ) : (
                  filteredIngredients.map((item, idx) => {
                    const isUnknown = item.riskLevel === 'UNKNOWN';
                    const isPositive = item.riskLevel === 'POSITIVE';
                    const itemSources = item.sourceIds || item.sources || [];
                    const displayName = item.normalizedName || item.originalIngredient;

                    return (
                      <div
                        key={idx}
                        className="ingredient-item-card"
                        style={isUnknown ? { borderLeft: '4px solid #94a3b8', background: 'rgba(148, 163, 184, 0.04)' } : undefined}
                      >
                        <div className="ingredient-top-row">
                          <div className="ingredient-main-title">
                            <strong className="ingredient-name">{displayName}</strong>
                            {item.additiveCode && (
                              <span className="additive-code-badge">{item.additiveCode}</span>
                            )}
                            {item.functionalClass && (
                              <span className="functional-class-tag">{item.functionalClass}</span>
                            )}
                          </div>

                          <div className="ingredient-badges-group">
                            <span className={`risk-pill risk-pill--${item.riskLevel.toLowerCase()}`}>
                              {item.riskLevel.replace(/_/g, ' ')}
                            </span>
                            <span className={`reg-pill reg-pill--${item.regulatoryStatus.toLowerCase()}`}>
                              {item.regulatoryStatus === 'PROHIBITED' && (itemSources.includes('FSSAI') || itemSources.includes('India'))
                                ? 'PROHIBITED (India / FSSAI)'
                                : item.regulatoryStatus}
                            </span>
                          </div>
                        </div>

                        {/* Concise 1-line deterministic summary */}
                        {item.summary && (
                          <p className="ingredient-summary-line" style={{ color: isUnknown ? '#94a3b8' : isPositive ? '#34d399' : '#2dd4bf', fontWeight: 500, margin: '0.35rem 0 0.25rem 0', fontSize: '0.9rem' }}>
                            {item.summary}
                          </p>
                        )}

                        {isUnknown ? (
                          <p className="ingredient-reason-text" style={{ color: '#94a3b8', fontStyle: 'italic', margin: '0.35rem 0' }}>
                            We couldn't confidently verify this ingredient against the available knowledge base.
                          </p>
                        ) : (
                          item.reasons && item.reasons.length > 0 && (
                            <p className="ingredient-reason-text" style={{ margin: '0.35rem 0' }}>
                              {item.reasons.join('. ')}
                            </p>
                          )
                        )}

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px', marginTop: '0.6rem' }}>
                          {itemSources.length > 0 ? (
                            <div className="ingredient-citation-row" style={{ margin: 0 }}>
                              <span className="citation-label">Evidence Source:</span>
                              <span className="citation-text">{itemSources.join(', ')}</span>
                            </div>
                          ) : <div />}

                          <button
                            type="button"
                            className="btn-know-more"
                            onClick={() => handleExplain(displayName, 'INGREDIENT')}
                            style={{
                              background: 'rgba(45, 212, 191, 0.12)',
                              border: '1px solid rgba(45, 212, 191, 0.3)',
                              color: '#2dd4bf',
                              borderRadius: '6px',
                              padding: '4px 10px',
                              fontSize: '0.8rem',
                              cursor: 'pointer',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px',
                              fontWeight: 500
                            }}
                          >
                            <span>💡</span> Know More
                          </button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </GlassCard>
          )}

          {/* TAB 3: NUTRITION ANALYSIS TABLE */}
          {activeTab === 'nutrition' && (
            <GlassCard variant="default" padding="large" className="analysis-tab-card">
              <div className="analysis-card-header">
                <span className="analysis-card-icon" aria-hidden="true">📊</span>
                <div>
                  <h3 className="analysis-card-title">Nutrition Facts &amp; Dietary Benchmarks</h3>
                  <p className="analysis-card-subtitle">
                    Quantitative concentrations evaluated against WHO and FSSAI maximum reference thresholds.
                  </p>
                </div>
              </div>

              {currentData.nutritionSummary ? (
                <>
                  <div className="nutrition-basis-notice">
                    Declared Basis: <strong>{currentData.nutritionSummary.declaredBasis.replace(/_/g, ' ')}</strong>
                    {currentData.nutritionSummary.servingSizeGrams && (
                      <span> (Serving size: {currentData.nutritionSummary.servingSizeGrams}g)</span>
                    )}
                  </div>

                  {/* Highlight Macro KPI Cards (Energy, Fat, Sugar, Sodium) */}
                  {(() => {
                    const findings = currentData.nutritionSummary.findings;
                    const energyFinding = findings.find(f => f.nutrient === 'ENERGY');
                    const fatFinding = findings.find(f => f.nutrient === 'TOTAL_FAT');
                    const sugarFinding = findings.find(f => f.nutrient === 'TOTAL_SUGARS' || f.nutrient === 'ADDED_SUGARS');
                    const sodiumFinding = findings.find(f => f.nutrient === 'SODIUM');

                    const energyDv = energyFinding ? getDailyPercentage('ENERGY', energyFinding.observedValue) : null;
                    const fatDv = fatFinding ? getDailyPercentage('TOTAL_FAT', fatFinding.observedValue) : null;
                    const sugarDv = sugarFinding ? getDailyPercentage('TOTAL_SUGARS', sugarFinding.observedValue) : null;
                    const sodiumDv = sodiumFinding ? getDailyPercentage('SODIUM', sodiumFinding.observedValue) : null;

                    return (
                      <div className="macro-kpi-grid">
                        <div className="macro-kpi-card">
                          <div className="macro-kpi-header">
                            <span className="macro-kpi-title">Calories</span>
                            {energyDv && (
                              <span className={`macro-kpi-pill ${energyDv.levelClass}`}>
                                {energyDv.text}
                              </span>
                            )}
                          </div>
                          <div className="macro-kpi-value-row">
                            <span className="macro-kpi-value">{energyFinding?.observedValue ?? 'N/A'}</span>
                            <span className="macro-kpi-unit">{energyFinding?.observedUnit || 'kcal'}</span>
                          </div>
                          <span className="macro-kpi-dv-sub">WHO Benchmark: 2,000 kcal / day</span>
                        </div>

                        <div className="macro-kpi-card">
                          <div className="macro-kpi-header">
                            <span className="macro-kpi-title">Total Fat</span>
                            {fatDv && (
                              <span className={`macro-kpi-pill ${fatDv.levelClass}`}>
                                {fatDv.text}
                              </span>
                            )}
                          </div>
                          <div className="macro-kpi-value-row">
                            <span className="macro-kpi-value">{fatFinding?.observedValue ?? 'N/A'}</span>
                            <span className="macro-kpi-unit">{fatFinding?.observedUnit || 'g'}</span>
                          </div>
                          <span className="macro-kpi-dv-sub">Target: &lt; 70g daily</span>
                        </div>

                        <div className="macro-kpi-card">
                          <div className="macro-kpi-header">
                            <span className="macro-kpi-title">Total Sugars</span>
                            {sugarDv && (
                              <span className={`macro-kpi-pill ${sugarDv.levelClass}`}>
                                {sugarDv.text}
                              </span>
                            )}
                          </div>
                          <div className="macro-kpi-value-row">
                            <span className="macro-kpi-value">{sugarFinding?.observedValue ?? 'N/A'}</span>
                            <span className="macro-kpi-unit">{sugarFinding?.observedUnit || 'g'}</span>
                          </div>
                          <span className="macro-kpi-dv-sub">WHO Max: 50g free sugars</span>
                        </div>

                        <div className="macro-kpi-card">
                          <div className="macro-kpi-header">
                            <span className="macro-kpi-title">Sodium</span>
                            {sodiumDv && (
                              <span className={`macro-kpi-pill ${sodiumDv.levelClass}`}>
                                {sodiumDv.text}
                              </span>
                            )}
                          </div>
                          <div className="macro-kpi-value-row">
                            <span className="macro-kpi-value">{sodiumFinding?.observedValue ?? 'N/A'}</span>
                            <span className="macro-kpi-unit">{sodiumFinding?.observedUnit || 'mg'}</span>
                          </div>
                          <span className="macro-kpi-dv-sub">WHO Max: 2,000 mg / day</span>
                        </div>
                      </div>
                    );
                  })()}

                  <div className="nutrition-grid-modern">
                    {currentData.nutritionSummary.findings.map((f: NutritionFinding, idx: number) => {
                      const isHigh = f.status === 'ABOVE_REFERENCE';
                      const isWithin = f.status === 'WITHIN_REFERENCE';
                      const dvData = getDailyPercentage(f.nutrient, f.observedValue);

                      return (
                        <div key={idx} className={`nutrition-stat-card ${isHigh ? 'nutrition-stat-card--high' : ''}`}>
                          <div className="stat-card-header">
                            <span className="stat-nutrient-name">{f.nutrient.replace(/_/g, ' ')}</span>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                              {dvData && (
                                <span className={`stat-percentage-badge ${dvData.levelClass}`}>
                                  {dvData.text}
                                </span>
                              )}
                              <span className={`stat-status-badge ${isHigh ? 'stat-badge--above' : isWithin ? 'stat-badge--within' : 'stat-badge--neutral'}`}>
                                {f.status.replace(/_/g, ' ')}
                              </span>
                            </div>
                          </div>

                          <div className="stat-value-row">
                            <span className="stat-number">{f.observedValue !== null ? f.observedValue : 'N/A'}</span>
                            <span className="stat-unit">{f.observedUnit}</span>
                          </div>

                          {dvData && (
                            <div className="stat-progress-track">
                              <div
                                className={`stat-progress-fill ${dvData.levelClass}`}
                                style={{ width: `${Math.min(dvData.pct, 100)}%` }}
                              />
                            </div>
                          )}

                          {f.referenceValue !== null && (
                            <div className="stat-reference-row">
                              <span>Reference Benchmark:</span>
                              <strong>{f.referenceValue} {f.referenceUnit}</strong>
                            </div>
                          )}

                          <p className="stat-reason-sub">{f.reason}</p>

                          <div style={{ marginTop: '0.6rem', textAlign: 'right' }}>
                            <button
                              type="button"
                              className="btn-know-more"
                              onClick={() => handleExplain(f.nutrient.replace(/_/g, ' '), 'NUTRIENT')}
                              style={{
                                background: 'rgba(45, 212, 191, 0.12)',
                                border: '1px solid rgba(45, 212, 191, 0.3)',
                                color: '#2dd4bf',
                                borderRadius: '6px',
                                padding: '3px 8px',
                                fontSize: '0.75rem',
                                cursor: 'pointer',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '3px',
                                fontWeight: 500
                              }}
                            >
                              <span>💡</span> Know More
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </>
              ) : (
                <p className="highlight-empty">No nutritional panel extracted for this product.</p>
              )}
            </GlassCard>
          )}

          {/* TAB 4: WHY THIS SCORE? (EXPLAINABLE ANTI-DOUBLE-COUNTING BREAKDOWN) */}
          {activeTab === 'breakdown' && (
            <GlassCard variant="default" padding="large" className="analysis-tab-card">
              <div className="analysis-card-header">
                <span className="analysis-card-icon" aria-hidden="true">⚖️</span>
                <div>
                  <h3 className="analysis-card-title">Score Explainability &amp; Anti-Double-Counting</h3>
                  <p className="analysis-card-subtitle">
                    Deterministic score calculation starting at 100 points, factoring penalties and deduplicating composite overlaps.
                  </p>
                </div>
              </div>

              {currentData.overallScore !== null ? (
                <div className="score-waterfall-wrapper">
                  <div className="waterfall-step waterfall-step--baseline">
                    <span className="step-label">Baseline Health Score</span>
                    <span className="step-points">+100</span>
                  </div>

                  <div className="waterfall-deductions">
                    {currentData.scoreBreakdown.map((item, idx) => {
                      const isOverlap = item.factor.includes('OVERLAP');
                      const isBonus = item.impact > 0;
                      return (
                        <div
                          key={idx}
                          className={`waterfall-step ${
                            isOverlap
                              ? 'waterfall-step--overlap'
                              : isBonus
                              ? 'waterfall-step--bonus'
                              : 'waterfall-step--penalty'
                          }`}
                        >
                          <div className="step-info">
                            <strong className="step-factor">
                              {isOverlap ? `🔄 Anti-Double-Counting: ${item.factor}` : item.factor}
                            </strong>
                            <p className="step-desc">{item.reason}</p>
                            <span className="step-source">Source: {item.source}</span>
                          </div>
                          <div className="step-impact-col">
                            <span className={`impact-badge ${isBonus ? 'impact-badge--bonus' : 'impact-badge--penalty'}`}>
                              {item.impact > 0 ? `+${item.impact}` : item.impact} pts
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  <div className="waterfall-step waterfall-step--final">
                    <span className="step-label">Final Food Awareness Score</span>
                    <span className="step-points step-points--final">{currentData.overallScore} / 100</span>
                  </div>
                </div>
              ) : (
                <p className="highlight-empty">Score breakdown is withheld for non-human or unverified products.</p>
              )}
            </GlassCard>
          )}

          {/* TAB 5: WHO & FSSAI OFFICIAL STANDARDS */}
          {activeTab === 'standards' && (
            <GlassCard variant="default" padding="large" className="analysis-tab-card">
              <div className="analysis-card-header">
                <span className="analysis-card-icon" aria-hidden="true">🏛️</span>
                <div>
                  <h3 className="analysis-card-title">Official Standards &amp; Reference Datasets</h3>
                  <p className="analysis-card-subtitle">
                    Regulatory benchmarks and rule versions applied during this evaluation.
                  </p>
                </div>
              </div>

              <div className="standards-version-banner">
                <div className="version-pill">
                  <span>Nutrition Engine Reference:</span>
                  <strong>v{currentData.nutritionReferenceVersion || '2026.09'}</strong>
                </div>
                <div className="version-pill">
                  <span>Scoring Rules Engine:</span>
                  <strong>v{currentData.scoringRuleVersion || '1.0'}</strong>
                </div>
              </div>

              <div className="standards-citations-list">
                {currentData.sources.map((src, idx) => (
                  <div key={idx} className="citation-card">
                    <Icon name="file-text" size={20} color="#20c9ff" className="citation-bullet" />
                    <div>
                      <strong className="citation-name">{src}</strong>
                      <p className="citation-desc">
                        Official regulatory guidance benchmark incorporated into deterministic rule evaluation.
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </GlassCard>
          )}
        </div>

        {/* PRIMARY ACTION BUTTONS */}
        <div className="result-actions-row">
          <Button
            variant="primary"
            size="medium"
            onClick={() => navigate('/scan')}
            icon={<Icon name="camera" size={18} />}
          >
            SCAN ANOTHER FOOD
          </Button>

          <Button
            variant="outline"
            size="medium"
            onClick={handleShareClick}
            icon={<Icon name="external-link" size={18} />}
          >
            SHARE ASSESSMENT
          </Button>

          <Button
            variant="outline"
            size="medium"
            onClick={handlePrint}
            icon={<Icon name="file-text" size={18} />}
          >
            PRINT REPORT
          </Button>
        </div>

        {/* PERSISTENT EDUCATIONAL AWARENESS BANNER */}
        <GlassCard variant="subtle" padding="large" className="result-disclaimer-card">
          <div className="disclaimer-header-row">
            <Icon name="shield" size={24} color="#20c9ff" className="disclaimer-icon" />
            <div>
              <h4 className="disclaimer-heading">EDUCATIONAL AWARENESS &amp; TRANSPARENCY NOTICE</h4>
              <p className="disclaimer-copy">
                This Food Risk Analysis report is generated strictly for consumer educational awareness and dietary literacy. It is grounded in publicly available FSSAI, WHO, and Codex Alimentarius guidelines. This tool does not diagnose, treat, or prevent any medical condition and does not substitute for personalized clinical advice from qualified healthcare professionals.
              </p>
            </div>
          </div>
        </GlassCard>

        {/* KNOW MORE (AI-GROUNDED EDUCATIONAL EXPLANATION) MODAL */}
        {explainingItem && (
          <div className="know-more-backdrop" style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(3, 6, 10, 0.85)',
            backdropFilter: 'blur(16px)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1rem'
          }}>
            <div className="know-more-modal" style={{
              background: '#080d16',
              border: '1px solid rgba(180, 205, 220, 0.28)',
              borderRadius: '16px',
              maxWidth: '520px',
              width: '100%',
              padding: '1.75rem',
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.8), 0 0 40px rgba(32, 201, 255, 0.15)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h4 style={{ margin: 0, color: '#f8fafc', fontSize: '1.2rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Icon name="info" size={20} color="#20c9ff" /> Know More: {explainingItem}
                </h4>
                <button
                  type="button"
                  onClick={() => setExplainingItem(null)}
                  style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '1.5rem', cursor: 'pointer', lineHeight: 1, display: 'flex', alignItems: 'center' }}
                  aria-label="Close modal"
                >
                  <Icon name="close" size={18} />
                </button>
              </div>

              {explanationLoading ? (
                <div style={{ textAlign: 'center', padding: '2rem 1rem' }}>
                  <div style={{
                    margin: '0 auto 1rem',
                    width: 36,
                    height: 36,
                    border: '3px solid rgba(255,255,255,0.1)',
                    borderTopColor: '#2dd4bf',
                    borderRadius: '50%',
                    animation: 'spin 1s linear infinite'
                  }} />
                  <p style={{ color: '#94a3b8', margin: 0 }}>Consulting authoritative food safety evidence...</p>
                </div>
              ) : explanationError ? (
                <div style={{ padding: '1rem', background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.25)', borderRadius: '8px', color: '#fca5a5' }}>
                  <p style={{ margin: 0 }}>{explanationError}</p>
                </div>
              ) : explanationResult ? (
                <div>
                  <div style={{ display: 'flex', gap: '8px', marginBottom: '0.75rem' }}>
                    <span style={{
                      background: explanationResult.aiGenerated ? 'rgba(45, 212, 191, 0.15)' : 'rgba(148, 163, 184, 0.15)',
                      color: explanationResult.aiGenerated ? '#2dd4bf' : '#cbd5e1',
                      padding: '2px 8px',
                      borderRadius: '4px',
                      fontSize: '0.75rem',
                      fontWeight: 600
                    }}>
                      {explanationResult.aiGenerated ? 'AI Science Explanation' : 'Knowledge Base Verified'}
                    </span>
                    <span style={{ background: 'rgba(255, 255, 255, 0.06)', color: '#94a3b8', padding: '2px 8px', borderRadius: '4px', fontSize: '0.75rem' }}>
                      Non-Medical Education
                    </span>
                  </div>
                  <p style={{ color: '#e2e8f0', lineHeight: 1.6, fontSize: '0.95rem', margin: '0.75rem 0' }}>
                    {explanationResult.explanation}
                  </p>
                  {explanationResult.sourceIds && explanationResult.sourceIds.length > 0 && (
                    <div style={{ marginTop: '1rem', paddingTop: '0.75rem', borderTop: '1px solid rgba(255,255,255,0.08)', fontSize: '0.8rem', color: '#94a3b8' }}>
                      <strong>Authoritative Sources: </strong>
                      <span>{explanationResult.sourceIds.join(', ')}</span>
                    </div>
                  )}
                </div>
              ) : null}

              <div style={{ marginTop: '1.5rem', textAlign: 'right' }}>
                <Button
                  variant="outline"
                  size="small"
                  onClick={() => setExplainingItem(null)}
                >
                  Close
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* DEDICATED EXECUTIVE PRINT & PDF REPORT */}
      <div className="print-executive-report" aria-hidden="true">
        {/* 1. Official Document Header */}
        <div className="print-header">
          <div className="print-header-brand">
            <div className="print-brand-badge">FOOD RISK ANALYSIS &bull; KNOW WHAT YOU EAT</div>
            <h1 className="print-report-title">Product Safety &amp; Nutritional Assessment Report</h1>
            <p className="print-report-subtitle">
              Independent Food Literacy, Additive Safety &amp; Nutritional Quality Evaluation Grounded in FSSAI, WHO, and Codex Alimentarius Standards
            </p>
          </div>
          <div className="print-header-meta">
            <div><strong>Report ID:</strong> {currentData.sessionId || 'N/A'}</div>
            <div><strong>Generated:</strong> {new Date().toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</div>
            <div><strong>Engine:</strong> Google Gemini 3.5 AI Vision + Regulatory Standards Engine</div>
            <div><strong>Status:</strong> {statusMeta.label}</div>
          </div>
        </div>

        {/* 2. Product Summary & Overall Score Card */}
        <div className="print-section print-summary-box">
          <div className="print-product-info">
            <div className="print-pill-row">
              <span className="print-badge-category">CATEGORY: {currentData.productCategory.replace(/_/g, ' ')}</span>
              <span className="print-badge-reliability">RELIABILITY: {currentData.assessmentReliability}</span>
              {currentData.nutritionDataCompleteness && (
                <span className="print-badge-completeness">DATA: {currentData.nutritionDataCompleteness}</span>
              )}
            </div>
            <h2 className="print-product-name">{getProductName()}</h2>
            <p className="print-product-desc">{currentData.productCategoryReason || 'Packaged consumable product'}</p>
          </div>
          <div className="print-score-box">
            <div className="print-score-value">
              {currentData.overallScore !== null ? currentData.overallScore : 'N/A'}
              <span className="print-score-max">/100</span>
            </div>
            <div className={`print-score-status print-status--${currentData.overallStatus.toLowerCase()}`}>
              {statusMeta.label}
            </div>
            <div className="print-score-caption">Food Awareness Score</div>
          </div>
        </div>

        {/* 3. Safety Highlights: Key Attention Flags & Positive Attributes */}
        <div className="print-section print-highlights-grid">
          <div className="print-highlight-col">
            <h3 className="print-subheading print-subheading--concern">Key Attention Flags ({currentData.keyConcerns.length})</h3>
            {currentData.keyConcerns.length > 0 ? (
              <ul className="print-bullet-list">
                {currentData.keyConcerns.map((c, i) => (
                  <li key={i} className="print-bullet-item print-bullet--concern">&bull; {c}</li>
                ))}
              </ul>
            ) : (
              <p className="print-empty-text">No critical hazards or excessive risk factors detected.</p>
            )}
          </div>
          <div className="print-highlight-col">
            <h3 className="print-subheading print-subheading--positive">Positive Attributes ({currentData.positiveIndicators.length})</h3>
            {currentData.positiveIndicators.length > 0 ? (
              <ul className="print-bullet-list">
                {currentData.positiveIndicators.map((p, i) => (
                  <li key={i} className="print-bullet-item print-bullet--positive">&bull; {p}</li>
                ))}
              </ul>
            ) : (
              <p className="print-empty-text">No distinct positive nutritional indicators declared.</p>
            )}
          </div>
        </div>

        {/* 4. Core Macronutrient Highlights (% Daily Value Table/Grid) */}
        {currentData.nutritionSummary && (
          <div className="print-section">
            <h3 className="print-section-title">Essential Macronutrient Highlights &amp; % Daily Value (% DV)</h3>
            <div className="print-macro-grid">
              {(() => {
                const findings = currentData.nutritionSummary.findings || [];
                const kpis = [
                  { label: 'Calories', nutrient: 'ENERGY', defaultUnit: 'kcal', benchmark: '2,000 kcal / day' },
                  { label: 'Total Fat', nutrient: 'TOTAL_FAT', defaultUnit: 'g', benchmark: '< 70g daily' },
                  { label: 'Saturated Fat', nutrient: 'SATURATED_FAT', defaultUnit: 'g', benchmark: '< 20g daily' },
                  { label: 'Total Sugars', nutrient: 'TOTAL_SUGARS', defaultUnit: 'g', benchmark: '< 50g daily' },
                  { label: 'Added Sugars', nutrient: 'ADDED_SUGARS', defaultUnit: 'g', benchmark: '< 25g daily' },
                  { label: 'Sodium', nutrient: 'SODIUM', defaultUnit: 'mg', benchmark: '< 2,000mg daily' },
                  { label: 'Dietary Fibre', nutrient: 'FIBRE', defaultUnit: 'g', benchmark: '30g target' },
                  { label: 'Protein', nutrient: 'PROTEIN', defaultUnit: 'g', benchmark: '50g target' }
                ];

                return kpis.map(kpi => {
                  const f = findings.find(item => item.nutrient === kpi.nutrient);
                  const dv = f ? getDailyPercentage(kpi.nutrient, f.observedValue) : null;
                  return (
                    <div key={kpi.label} className="print-macro-card">
                      <div className="print-macro-card-title">{kpi.label}</div>
                      <div className="print-macro-card-val">
                        {f && f.observedValue !== null ? f.observedValue : 'N/A'} {f ? f.observedUnit : kpi.defaultUnit}
                      </div>
                      {dv ? (
                        <div className={`print-macro-card-dv ${dv.levelClass}`}>
                          <strong>{dv.text}</strong> of Standard Daily Benchmark
                        </div>
                      ) : (
                        <div className="print-macro-card-dv-sub">{kpi.benchmark}</div>
                      )}
                    </div>
                  );
                });
              })()}
            </div>
          </div>
        )}

        {/* 5. Complete Nutrition Facts Evaluation Table */}
        {currentData.nutritionSummary && (
          <div className="print-section print-table-section">
            <h3 className="print-section-title">Complete Nutrition Facts &amp; Dietary Reference Standards</h3>
            <p className="print-section-sub">
              Declared Basis: <strong>{currentData.nutritionSummary.declaredBasis.replace(/_/g, ' ')}</strong>
              {currentData.nutritionSummary.servingSizeGrams && (
                <span> | Serving Size: {currentData.nutritionSummary.servingSizeGrams}g</span>
              )}
            </p>
            <table className="print-table">
              <thead>
                <tr>
                  <th>Nutrient</th>
                  <th>Observed Value</th>
                  <th>% Daily Value (% DV)</th>
                  <th>Reference Limit</th>
                  <th>Evaluation Status</th>
                  <th>Scientific Rationale &amp; Source</th>
                </tr>
              </thead>
              <tbody>
                {currentData.nutritionSummary.findings.map((f, idx) => {
                  const dv = getDailyPercentage(f.nutrient, f.observedValue);
                  return (
                    <tr key={idx}>
                      <td className="print-td-bold">{f.nutrient.replace(/_/g, ' ')}</td>
                      <td className="print-td-num">{f.observedValue !== null ? `${f.observedValue} ${f.observedUnit}` : 'N/A'}</td>
                      <td className="print-td-num">{dv ? dv.text : '—'}</td>
                      <td className="print-td-num">{f.referenceValue !== null ? `${f.referenceValue} ${f.referenceUnit}` : 'None Set'}</td>
                      <td>
                        <span className={`print-badge print-badge--${f.status.toLowerCase().replace(/_/g, '-')}`}>
                          {f.status.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="print-td-reason">{f.reason || 'Evaluated against dietary guidelines.'} ({f.sourceIds?.join(', ') || 'FSSAI/WHO'})</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* 6. Complete Ingredients Statement & Additive INS Registry */}
        <div className="print-section print-table-section">
          <h3 className="print-section-title">
            Full Ingredients Statement &amp; Additive Registry ({rawIngredients.length} Ingredients, {currentData.ingredientSummary?.additivesDetected ?? 0} Additives)
          </h3>
          <table className="print-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Ingredient Name</th>
                <th>Additive Code (INS/E)</th>
                <th>Functional Class</th>
                <th>Risk / Attention Level</th>
                <th>Regulatory Status</th>
              </tr>
            </thead>
            <tbody>
              {rawIngredients.map((item, idx) => (
                <tr key={idx}>
                  <td className="print-td-num">{idx + 1}</td>
                  <td className="print-td-bold">{item.originalIngredient || item.normalizedName}</td>
                  <td>{item.additiveCode ? <strong className="print-code-pill">{item.additiveCode}</strong> : '—'}</td>
                  <td>{item.functionalClass || 'Food Ingredient'}</td>
                  <td>
                    <span className={`print-badge print-badge--${item.riskLevel.toLowerCase().replace(/_/g, '-')}`}>
                      {item.riskLevel.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td>{item.regulatoryStatus || 'PERMITTED'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* 7. Demographic Guidance */}
        {currentData.ageGroupAwareness && currentData.ageGroupAwareness.length > 0 && (
          <div className="print-section">
            <h3 className="print-section-title">Demographic &amp; Age-Group Eating Guidance</h3>
            <div className="print-demo-grid">
              {currentData.ageGroupAwareness.map((ag, idx) => (
                <div key={idx} className="print-demo-card">
                  <div className="print-demo-header">
                    <strong className="print-demo-title">{ag.ageGroup.replace(/_/g, ' ')}</strong>
                    <span className={`print-badge print-badge--${ag.attentionLevel.toLowerCase().replace(/_/g, '-')}`}>
                      {ag.attentionLevel.replace(/_/g, ' ')}
                    </span>
                  </div>
                  <p className="print-demo-summary">{ag.summary}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 8. Explainable Score Waterfall Breakdown */}
        {currentData.overallScore !== null && currentData.scoreBreakdown && currentData.scoreBreakdown.length > 0 && (
          <div className="print-section">
            <h3 className="print-section-title">Explainable Score Calculation &amp; Anti-Double-Counting</h3>
            <table className="print-table">
              <thead>
                <tr>
                  <th>Factor / Evaluation Step</th>
                  <th>Impact</th>
                  <th>Scientific Reasoning &amp; Regulatory Basis</th>
                  <th>Authority</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td className="print-td-bold">Starting Health Baseline</td>
                  <td className="print-td-num"><strong>100</strong></td>
                  <td>Default baseline score before product penalties and nutritional adjustments.</td>
                  <td>System</td>
                </tr>
                {currentData.scoreBreakdown.map((item, idx) => (
                  <tr key={idx}>
                    <td className="print-td-bold">{item.factor.replace(/_/g, ' ')}</td>
                    <td className="print-td-num" style={{ color: item.impact > 0 ? '#166534' : '#991b1b', fontWeight: 700 }}>
                      {item.impact > 0 ? `+${item.impact}` : item.impact}
                    </td>
                    <td>{item.reason}</td>
                    <td>{item.source || 'FSSAI/WHO'}</td>
                  </tr>
                ))}
                <tr className="print-total-row">
                  <td className="print-td-bold">Final Food Awareness Score</td>
                  <td className="print-td-num"><strong>{currentData.overallScore} / 100</strong></td>
                  <td colSpan={2}><strong>{statusMeta.label}</strong> &mdash; Determined via deterministic penalty weighting and anti-double-counting composite rules.</td>
                </tr>
              </tbody>
            </table>
          </div>
        )}

        {/* 9. Official Regulatory Disclaimer */}
        <div className="print-section print-disclaimer-box">
          <h4>EDUCATIONAL AWARENESS &amp; TRANSPARENCY NOTICE (FSSAI / WHO / CODEX ALIMENTARIUS)</h4>
          <p>
            This Food Risk Analysis report is generated strictly for consumer educational awareness and dietary literacy. It is grounded in publicly available FSSAI, WHO, and Codex Alimentarius guidelines. This tool does not diagnose, treat, or prevent any medical condition and does not substitute for personalized clinical advice from qualified healthcare professionals.
          </p>
        </div>
      </div>
    </div>
  );
};

export default ResultPage;
