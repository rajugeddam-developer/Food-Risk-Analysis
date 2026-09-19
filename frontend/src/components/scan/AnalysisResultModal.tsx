import React from 'react';
import { GlassCard } from '../common/GlassCard';
import { Button } from '../common/Button';
import { FoodClassificationCard } from './FoodClassificationCard';
import { IngredientRiskList } from './IngredientRiskList';
import type {
  FoodClassificationResult,
  IngredientRiskAnalysisResult
} from '../../api/analysisApi';

interface AnalysisResultModalProps {
  isOpen: boolean;
  sessionId?: string | null;
  productName: string | null;
  classification: FoodClassificationResult | null;
  riskResult: IngredientRiskAnalysisResult | null;
  onClose: () => void;
  onReset: () => void;
  onViewFullAssessment?: () => void;
}

export const AnalysisResultModal: React.FC<AnalysisResultModalProps> = ({
  isOpen,
  productName,
  classification,
  riskResult,
  onClose,
  onReset,
  onViewFullAssessment
}) => {
  if (!isOpen || (!classification && !riskResult)) return null;

  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="analysis-report-title"
    >
      <GlassCard
        variant="elevated"
        padding="large"
        className="handoff-modal-card"
        style={{
          maxWidth: '720px',
          maxHeight: '92vh',
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
          gap: '1.25rem'
        }}
      >
        {/* Header */}
        <div className="handoff-modal-header">
          <div
            className="handoff-badge"
            style={{
              borderColor: 'rgba(56, 189, 248, 0.35)',
              background: 'rgba(56, 189, 248, 0.1)',
              color: '#38bdf8'
            }}
          >
            <span>MILESTONES M7 &amp; M8 • VERIFIED FOOD EVALUATION</span>
          </div>
          <h2 id="analysis-report-title" className="handoff-modal-title">
            {productName || 'Food Product Analysis'}
          </h2>
          <p className="handoff-modal-subtitle">
            Intent classification &amp; verified ingredient evaluation via FSSAI and WHO standards
          </p>
        </div>

        {/* Milestone M7: Intent & Category Classification */}
        {classification && (
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '0.5rem' }}>
              <span style={{ fontSize: '0.6875rem', fontWeight: 800, color: '#38bdf8', letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                M7 • Product Categorization
              </span>
            </div>
            <FoodClassificationCard classification={classification} />
          </div>
        )}

        {/* Milestone M8: Ingredient Risk & Additive Evaluation */}
        {riskResult && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <span style={{ fontSize: '0.6875rem', fontWeight: 800, color: '#a855f7', letterSpacing: '0.06em', textTransform: 'uppercase' }}>
                M8 • Ingredient &amp; Additive Risk Analysis
              </span>
            </div>
            <IngredientRiskList analysisResult={riskResult} />
          </div>
        )}

        {/* Actions Row */}
        <div className="handoff-actions-row" style={{ marginTop: '0.5rem', display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          {onViewFullAssessment && (
            <Button variant="primary" size="medium" onClick={onViewFullAssessment} icon={<span>📊</span>}>
              View Full Assessment Report
            </Button>
          )}
          <Button variant="outline" size="medium" onClick={onClose}>
            Close
          </Button>
          <Button variant="ghost" size="medium" onClick={onReset}>
            Scan Another Product
          </Button>
        </div>
      </GlassCard>
    </div>
  );
};

export default AnalysisResultModal;
