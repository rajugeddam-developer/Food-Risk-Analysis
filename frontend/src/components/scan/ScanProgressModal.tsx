import React from 'react';
import { GlassCard } from '../common/GlassCard';
import { Button } from '../common/Button';
import { Icon } from '../common/Icon';

interface ScanProgressModalProps {
  isOpen: boolean;
  currentStage?: string | null;
  errorMessage?: string | null;
  onRetry?: () => void;
  onCancel?: () => void;
}

interface StageDefinition {
  id: string;
  stageKey: string[];
  title: string;
  description: string;
}

const ORCHESTRATION_STAGES: StageDefinition[] = [
  {
    id: 'stage-1',
    stageKey: ['IMAGE_VALIDATION', 'OCR_PROCESSING'],
    title: 'Reading label images',
    description: 'Scanning ingredients statement and nutrition facts panel via offline OCR'
  },
  {
    id: 'stage-2',
    stageKey: ['AI_NORMALIZATION'],
    title: 'Structuring ingredients & nutrition',
    description: 'Parsing additives, INS/E-numbers, and nutritional values without fabricated zeros'
  },
  {
    id: 'stage-3',
    stageKey: ['PRODUCT_CLASSIFICATION'],
    title: 'Understanding the product',
    description: 'Verifying human consumption safety and detecting animal feed or non-food markers'
  },
  {
    id: 'stage-4',
    stageKey: ['INGREDIENT_RISK_ANALYSIS'],
    title: 'Checking ingredient risks',
    description: 'Evaluating additives and ingredients against authoritative FSSAI & WHO standards'
  },
  {
    id: 'stage-5',
    stageKey: ['NUTRITION_ANALYSIS'],
    title: 'Checking nutrition facts',
    description: 'Comparing sodium, sugars, and trans fat concentrations to dietary reference benchmarks'
  },
  {
    id: 'stage-6',
    stageKey: ['SCORE_SYNTHESIS', 'COMPLETED'],
    title: 'Preparing Food Awareness Score',
    description: 'Synthesizing composite score with anti-double-counting and population guidance'
  }
];

export const ScanProgressModal: React.FC<ScanProgressModalProps> = ({
  isOpen,
  currentStage,
  errorMessage,
  onRetry,
  onCancel
}) => {
  if (!isOpen) return null;

  // Determine active stage index
  const resolvedStage = currentStage || 'IMAGE_VALIDATION';
  let activeIndex = 0;
  for (let i = 0; i < ORCHESTRATION_STAGES.length; i++) {
    if (ORCHESTRATION_STAGES[i].stageKey.includes(resolvedStage)) {
      activeIndex = i;
      break;
    }
  }

  const isCompleted = resolvedStage === 'COMPLETED';
  const progressPercent = isCompleted
    ? 100
    : Math.min(Math.round(((activeIndex + 0.5) / ORCHESTRATION_STAGES.length) * 100), 95);

  const activeStage = ORCHESTRATION_STAGES[activeIndex];

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="scan-modal-title">
      <GlassCard variant="elevated" padding="large" className="scan-progress-card">
        {errorMessage ? (
          /* Error / Failure State */
          <div className="scan-progress-error-view">
            <div className="scan-progress-icon-badge scan-progress-icon-badge--error" aria-hidden="true">
              <Icon name="alert-triangle" size={26} color="#f87171" />
            </div>
            <h3 id="scan-modal-title" className="scan-progress-title" style={{ color: '#f87171' }}>
              Analysis Incomplete
            </h3>
            <p className="scan-progress-error-message">{errorMessage}</p>

            <div className="scan-progress-error-tips">
              <span className="error-tips-heading">Tips for best scanning results:</span>
              <ul className="error-tips-list">
                <li>Capture well-lit photos without heavy plastic glare or reflection</li>
                <li>Ensure the ingredients statement and nutrition facts table are sharp and readable</li>
                <li>Flatten curved pouches or bottle labels before taking the photo</li>
              </ul>
            </div>

            <div className="scan-progress-error-actions">
              {onRetry && (
                <Button variant="primary" size="medium" onClick={onRetry} icon={<Icon name="refresh" size={16} />}>
                  Try Again
                </Button>
              )}
              {onCancel && (
                <Button variant="ghost" size="medium" onClick={onCancel}>
                  Cancel
                </Button>
              )}
            </div>
          </div>
        ) : (
          /* Active Processing State */
          <>
            <div className="scan-progress-header">
              <span className="scan-progress-icon-badge" aria-hidden="true">
                <span className="scan-radar-pulse" />
                <Icon name="sparkles" size={24} color="#20c9ff" />
              </span>
              <h3 id="scan-modal-title" className="scan-progress-title">
                Analyzing Food Product
              </h3>
              <p className="scan-progress-sim-notice">
                Evaluating against authoritative FSSAI &amp; WHO standards
              </p>
            </div>

            {/* Progress Bar */}
            <div className="scan-progress-bar-track">
              <div
                className="scan-progress-bar-fill"
                style={{ width: `${progressPercent}%`, transition: 'width 0.4s ease-out' }}
              />
            </div>
            <div className="scan-progress-percent">{progressPercent}%</div>

            {/* Active Stage Details */}
            <div className="scan-stage-details">
              <span className="scan-stage-counter">
                STAGE {activeIndex + 1} OF {ORCHESTRATION_STAGES.length}
              </span>
              <h4 className="scan-stage-title">{activeStage.title}</h4>
              <p className="scan-stage-sub">{activeStage.description}</p>
            </div>

            {/* Stage Checklist */}
            <div className="scan-stages-checklist">
              {ORCHESTRATION_STAGES.map((st, idx) => {
                const isStepComplete = idx < activeIndex || isCompleted;
                const isStepActive = idx === activeIndex && !isCompleted;
                return (
                  <div
                    key={st.id}
                    className={`scan-step-item ${
                      isStepComplete
                        ? 'scan-step--completed'
                        : isStepActive
                        ? 'scan-step--active'
                        : 'scan-step--pending'
                    }`}
                  >
                    <span className="scan-step-dot">
                      {isStepComplete ? <Icon name="check" size={12} color="#03060a" /> : idx + 1}
                    </span>
                    <span className="scan-step-text">{st.title}</span>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </GlassCard>
    </div>
  );
};

export default ScanProgressModal;
