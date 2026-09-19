export type RiskStatus = 'good' | 'caution' | 'high_concern' | 'avoid';

export type ProductClassificationType =
  | 'HUMAN_FOOD'
  | 'ANIMAL_PET_FOOD'
  | 'NON_FOOD'
  | 'UNCERTAIN';

export interface IngredientAnalysisItem {
  name: string;
  category: string;
  status: RiskStatus;
  statusLabel: 'GOOD' | 'CAUTION' | 'HIGH CONCERN' | 'AVOID';
  description: string;
}

export interface NutritionItem {
  nutrient: string;
  amount: string;
  dailyValuePercent?: number;
  status: RiskStatus;
  statusLabel: string;
}

export interface ClassificationDetails {
  type: ProductClassificationType;
  title: string;
  icon: string;
  badgeClass: string;
  description: string;
  safetyAdvisory?: string;
}

export interface FoodRiskResultData {
  productName: string;
  brandName: string;
  servingSize: string;
  score: number;
  scoreMax: number;
  riskBand: 'LOW' | 'MODERATE CONCERN' | 'HIGH' | 'CRITICAL';
  classification: ProductClassificationType;
  ingredients: IngredientAnalysisItem[];
  nutrition: NutritionItem[];
  eatingGuidance: string[];
  awarenessNotice: string;
  standardsPlaceholder: string;
}

export const CLASSIFICATION_MAP: Record<ProductClassificationType, ClassificationDetails> = {
  HUMAN_FOOD: {
    type: 'HUMAN_FOOD',
    title: 'HUMAN FOOD',
    icon: '👤',
    badgeClass: 'badge-human',
    description: 'Verified packaged food intended for human consumption.'
  },
  ANIMAL_PET_FOOD: {
    type: 'ANIMAL_PET_FOOD',
    title: 'ANIMAL / PET FOOD',
    icon: '🐾',
    badgeClass: 'badge-animal',
    description: 'Formulated specifically for animals or pets. Not safe as human nutrition.',
    safetyAdvisory: 'DO NOT CONSUME: Nutritional density, bacterial allowances, and mineral ratios are formulated strictly for animal physiology.'
  },
  NON_FOOD: {
    type: 'NON_FOOD',
    title: 'NOT INTENDED FOR HUMAN CONSUMPTION',
    icon: '⚠️',
    badgeClass: 'badge-non-food',
    description: 'Household, cosmetic, or industrial chemical product.',
    safetyAdvisory: 'POISON & HAZARD WARNING: Ingestion may result in severe acute toxicity. Contact poison control immediately if consumed.'
  },
  UNCERTAIN: {
    type: 'UNCERTAIN',
    title: 'UNABLE TO DETERMINE',
    icon: '❓',
    badgeClass: 'badge-uncertain',
    description: 'Image clarity or text coverage is insufficient to verify consumption safety.',
    safetyAdvisory: 'Please recapture sharp, well-lit photos of both the ingredients statement and manufacturer classification.'
  }
};

export const MOCK_ANALYSIS_DATA: FoodRiskResultData = {
  productName: 'Classic Savory Potato Crisps',
  brandName: 'SnackCraft Artisanal',
  servingSize: '100g (Sample Evaluation)',
  score: 62,
  scoreMax: 100,
  riskBand: 'MODERATE CONCERN',
  classification: 'HUMAN_FOOD',
  ingredients: [
    {
      name: 'Potato',
      category: 'Primary Agricultural Food',
      status: 'good',
      statusLabel: 'GOOD',
      description: 'Whole agricultural root vegetable, minimally processed foundation.'
    },
    {
      name: 'Palm Oil',
      category: 'Refined Industrial Fat',
      status: 'caution',
      statusLabel: 'CAUTION',
      description: 'Refined vegetable fat high in saturated palmitic acid (approx. 50% saturated).'
    },
    {
      name: 'Salt',
      category: 'Added Mineral',
      status: 'high_concern',
      statusLabel: 'HIGH CONCERN',
      description: 'Added sodium chloride contributing 820mg sodium per 100g serving.'
    },
    {
      name: 'E621 (Monosodium Glutamate)',
      category: 'Flavor Enhancer',
      status: 'caution',
      statusLabel: 'CAUTION',
      description: 'Processed flavor enhancer used to stimulate glutamate receptors and encourage hyper-palatability.'
    },
    {
      name: 'TBHQ (Antioxidant 319)',
      category: 'Synthetic Preservative',
      status: 'caution',
      statusLabel: 'CAUTION',
      description: 'Synthetic phenolic antioxidant used to prevent rancidity in deep-frying oils.'
    }
  ],
  nutrition: [
    {
      nutrient: 'Calories',
      amount: '536 kcal',
      status: 'caution',
      statusLabel: 'MODERATE'
    },
    {
      nutrient: 'Total Fat',
      amount: '34.0 g',
      dailyValuePercent: 44,
      status: 'high_concern',
      statusLabel: 'HIGH'
    },
    {
      nutrient: 'Saturated Fat',
      amount: '15.2 g',
      dailyValuePercent: 76,
      status: 'avoid',
      statusLabel: 'AVOID'
    },
    {
      nutrient: 'Sugar',
      amount: '2.1 g',
      dailyValuePercent: 4,
      status: 'caution',
      statusLabel: 'MODERATE'
    },
    {
      nutrient: 'Sodium',
      amount: '820 mg',
      dailyValuePercent: 41,
      status: 'avoid',
      statusLabel: 'HIGH'
    },
    {
      nutrient: 'Protein',
      amount: '6.5 g',
      dailyValuePercent: 13,
      status: 'good',
      statusLabel: 'GOOD'
    }
  ],
  eatingGuidance: [
    'Treat as an occasional indulgence rather than a regular dietary staple.',
    'Elevated saturated fat and sodium levels exceed recommended daily snacks thresholds.',
    'Balance with whole fruits, vegetables, and low-sodium hydration.'
  ],
  awarenessNotice:
    'This assessment is intended to help you understand the information shown on a food label before consuming the product. It is an awareness and information tool, not a medical diagnosis or nutritional prescription.',
  standardsPlaceholder:
    'WHO / FSSAI references will be displayed here when the regulatory standards engine is implemented in future milestones.'
};
