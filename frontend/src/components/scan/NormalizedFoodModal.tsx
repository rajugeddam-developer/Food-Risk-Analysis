import React from 'react';
import { GlassCard } from '../common/GlassCard';
import { Button } from '../common/Button';
import type { NormalizedFoodData } from '../../api/analysisApi';

interface NormalizedFoodModalProps {
  isOpen: boolean;
  normalizedData: NormalizedFoodData | null;
  isLoadingAnalysis?: boolean;
  onProceedToRiskAnalysis?: () => void;
  onClose: () => void;
  onReset: () => void;
}

export const NormalizedFoodModal: React.FC<NormalizedFoodModalProps> = ({
  isOpen,
  normalizedData,
  isLoadingAnalysis = false,
  onProceedToRiskAnalysis,
  onClose,
  onReset
}) => {
  if (!isOpen || !normalizedData) return null;

  const nutrition = normalizedData.nutrition;

  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="normalized-modal-title"
    >
      <GlassCard variant="elevated" padding="large" className="handoff-modal-card" style={{ maxWidth: '640px', maxHeight: '90vh', overflowY: 'auto' }}>
        {/* Modal Header */}
        <div className="handoff-modal-header">
          <div className="handoff-badge" style={{ borderColor: 'rgba(168, 85, 247, 0.3)', background: 'rgba(168, 85, 247, 0.1)', color: '#c084fc' }}>
            <span>MILESTONE M6 • AI NORMALIZATION COMPLETE</span>
          </div>
          <h2 id="normalized-modal-title" className="handoff-modal-title">
            {normalizedData.productName || 'Normalized Food Data'}
          </h2>
          <p className="handoff-modal-subtitle">
            {normalizedData.servingSize ? `Serving Size: ${normalizedData.servingSize}` : 'Parsed and structured from packaging evidence'}
          </p>
        </div>

        {/* Required Boundary / Health Guidance Disclaimer */}
        <div className="handoff-pipeline-notice" style={{ background: 'rgba(168, 85, 247, 0.08)', borderColor: 'rgba(168, 85, 247, 0.25)' }}>
          <span className="notice-icon" aria-hidden="true">ℹ️</span>
          <div>
            <h4 className="notice-heading" style={{ color: '#c084fc' }}>AI-Assisted Normalization</h4>
            <p className="notice-text">
              This is an AI-assisted normalization of the food label. It is not a health or medical judgement.
            </p>
          </div>
        </div>

        {/* Section 1: Ingredients & Additives */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <h4 style={{ fontSize: '0.875rem', fontWeight: 700, color: '#f8fafc', margin: 0 }}>
            Ingredients & Additives ({normalizedData.ingredients.length})
          </h4>

          {normalizedData.ingredients.length === 0 ? (
            <p style={{ fontSize: '0.8125rem', color: '#94a3b8', fontStyle: 'italic', margin: 0 }}>
              No individual ingredients detected or resolved from OCR text.
            </p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', maxHeight: '180px', overflowY: 'auto', paddingRight: '0.25rem' }}>
              {normalizedData.ingredients.map((ing, idx) => (
                <div
                  key={idx}
                  style={{
                    background: 'rgba(255, 255, 255, 0.03)',
                    border: '1px solid rgba(255, 255, 255, 0.07)',
                    borderRadius: '0.375rem',
                    padding: '0.5rem 0.75rem',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '0.5rem'
                  }}
                >
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.15rem' }}>
                    <span style={{ fontSize: '0.875rem', fontWeight: 600, color: '#f1f5f9' }}>
                      {ing.name}
                    </span>
                    {ing.rawText && ing.rawText !== ing.name && (
                      <span style={{ fontSize: '0.6875rem', color: '#64748b' }}>
                        OCR: &quot;{ing.rawText}&quot;
                      </span>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap', alignItems: 'center' }}>
                    {ing.isAdditive && (
                      <span
                        style={{
                          fontSize: '0.6875rem',
                          background: 'rgba(245, 158, 11, 0.15)',
                          color: '#fbbf24',
                          border: '1px solid rgba(245, 158, 11, 0.3)',
                          padding: '0.15rem 0.45rem',
                          borderRadius: '0.25rem',
                          fontWeight: 600
                        }}
                      >
                        {ing.additiveCode || 'Additive'}
                      </span>
                    )}
                    {ing.uncertain && (
                      <span
                        style={{
                          fontSize: '0.6875rem',
                          background: 'rgba(239, 68, 68, 0.15)',
                          color: '#fca5a5',
                          border: '1px solid rgba(239, 68, 68, 0.3)',
                          padding: '0.15rem 0.45rem',
                          borderRadius: '0.25rem'
                        }}
                        title="Low OCR confidence or ambiguous spelling"
                      >
                        Uncertain
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Section 2: Nutrition Facts */}
        {nutrition && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h4 style={{ fontSize: '0.875rem', fontWeight: 700, color: '#f8fafc', margin: 0 }}>
                Nutrition Facts
              </h4>
              <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>
                Basis: {nutrition.basis || 'per 100g'}
              </span>
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))',
                gap: '0.5rem'
              }}
            >
              {[
                { label: 'Calories', val: nutrition.energyKcal != null ? `${nutrition.energyKcal} kcal` : '—' },
                { label: 'Total Fat', val: nutrition.totalFatG != null ? `${nutrition.totalFatG} g` : '—' },
                { label: 'Saturated Fat', val: nutrition.saturatedFatG != null ? `${nutrition.saturatedFatG} g` : '—' },
                { label: 'Trans Fat', val: nutrition.transFatG != null ? `${nutrition.transFatG} g` : '—' },
                { label: 'Carbohydrates', val: nutrition.carbohydrateG != null ? `${nutrition.carbohydrateG} g` : '—' },
                { label: 'Total Sugars', val: nutrition.totalSugarsG != null ? `${nutrition.totalSugarsG} g` : '—' },
                { label: 'Added Sugars', val: nutrition.addedSugarsG != null ? `${nutrition.addedSugarsG} g` : '—' },
                { label: 'Protein', val: nutrition.proteinG != null ? `${nutrition.proteinG} g` : '—' },
                { label: 'Sodium', val: nutrition.sodiumMg != null ? `${nutrition.sodiumMg} mg` : '—' },
                { label: 'Dietary Fiber', val: nutrition.fiberG != null ? `${nutrition.fiberG} g` : '—' }
              ].map((item, idx) => (
                <div
                  key={idx}
                  style={{
                    background: 'rgba(255, 255, 255, 0.02)',
                    border: '1px solid rgba(255, 255, 255, 0.06)',
                    borderRadius: '0.375rem',
                    padding: '0.45rem 0.6rem',
                    display: 'flex',
                    flexDirection: 'column'
                  }}
                >
                  <span style={{ fontSize: '0.6875rem', color: '#94a3b8' }}>{item.label}</span>
                  <span style={{ fontSize: '0.875rem', fontWeight: 700, color: item.val === '—' ? '#64748b' : '#f8fafc' }}>
                    {item.val}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Section 3: Uncertainties / Anomalies */}
        {normalizedData.uncertainties && normalizedData.uncertainties.length > 0 && (
          <div style={{ background: 'rgba(245, 158, 11, 0.06)', border: '1px solid rgba(245, 158, 11, 0.2)', borderRadius: '0.375rem', padding: '0.6rem 0.85rem' }}>
            <h5 style={{ fontSize: '0.75rem', fontWeight: 700, color: '#fbbf24', margin: '0 0 0.35rem' }}>
              ⚠️ Normalization Notes & Discrepancies
            </h5>
            <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.75rem', color: '#cbd5e1', lineHeight: '1.45' }}>
              {normalizedData.uncertainties.map((note, idx) => (
                <li key={idx}>{note}</li>
              ))}
            </ul>
          </div>
        )}

        {/* Action Row */}
        <div className="handoff-actions-row" style={{ marginTop: '0.5rem', flexWrap: 'wrap' }}>
          <Button variant="ghost" size="medium" onClick={onReset}>
            Scan Another Product
          </Button>
          {onProceedToRiskAnalysis ? (
            <Button
              variant="primary"
              size="medium"
              disabled={isLoadingAnalysis}
              onClick={onProceedToRiskAnalysis}
            >
              {isLoadingAnalysis ? 'EVALUATING RISK...' : 'RUN RISK EVALUATION (M7 & M8) ➜'}
            </Button>
          ) : (
            <Button variant="primary" size="medium" onClick={onClose}>
              Done
            </Button>
          )}
        </div>
      </GlassCard>
    </div>
  );
};
