import React from 'react';
import { GlassCard } from '../common/GlassCard';
import { Button } from '../common/Button';
import type { OcrAnalysisResponse } from '../../api/analysisApi';

interface OcrResultModalProps {
  isOpen: boolean;
  ocrData: OcrAnalysisResponse | null;
  isLoadingNormalization?: boolean;
  onProceedToNormalization: () => void;
  onClose: () => void;
  onReset: () => void;
}

export const OcrResultModal: React.FC<OcrResultModalProps> = ({
  isOpen,
  ocrData,
  isLoadingNormalization = false,
  onProceedToNormalization,
  onClose,
  onReset
}) => {
  if (!isOpen || !ocrData) return null;

  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="ocr-modal-title"
    >
      <GlassCard variant="elevated" padding="large" className="handoff-modal-card">
        {/* Header */}
        <div className="handoff-modal-header">
          <div className="handoff-badge" style={{ borderColor: 'rgba(56, 189, 248, 0.3)', background: 'rgba(56, 189, 248, 0.1)', color: '#38bdf8' }}>
            <span>MILESTONE M5 • OFFLINE OCR COMPLETE</span>
          </div>
          <h2 id="ocr-modal-title" className="handoff-modal-title">
            Raw OCR Result
          </h2>
          <p className="handoff-modal-subtitle">
            Raw text extracted directly from packaging surfaces via offline Tesseract OCR.
          </p>
        </div>

        {/* Informational Warning / Raw disclaimer */}
        <div className="handoff-pipeline-notice" style={{ background: 'rgba(234, 179, 8, 0.08)', borderColor: 'rgba(234, 179, 8, 0.25)' }}>
          <span className="notice-icon" aria-hidden="true">⚠️</span>
          <div>
            <h4 className="notice-heading" style={{ color: '#eab308' }}>Unverified Raw Text</h4>
            <p className="notice-text">
              The text below is directly produced by the OCR engine and may contain typos, misread characters, or broken lines. It is not normalized, verified, or evaluated for health safety.
            </p>
          </div>
        </div>

        {/* OCR Content Display */}
        <div className="handoff-surfaces-grid">
          {/* Surface 1: Ingredients OCR */}
          <div className="handoff-surface-card" style={{ gap: '0.5rem' }}>
            <div className="handoff-surface-header">
              <span className="handoff-surface-icon" aria-hidden="true">🏷️</span>
              <div>
                <h4 className="handoff-surface-title">Ingredients OCR Text</h4>
                <span className="handoff-source-badge">
                  {ocrData.ingredients.present
                    ? `${ocrData.ingredients.processingTimeMs} ms • ${ocrData.ingredients.confidence ? Math.round(ocrData.ingredients.confidence) + '% conf' : 'Tesseract'}`
                    : 'Not provided'}
                </span>
              </div>
            </div>
            <div
              style={{
                background: 'rgba(0, 0, 0, 0.4)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '0.375rem',
                padding: '0.75rem',
                maxHeight: '160px',
                overflowY: 'auto',
                fontFamily: 'monospace',
                fontSize: '0.8125rem',
                color: '#e2e8f0',
                whiteSpace: 'pre-wrap',
                lineHeight: '1.4'
              }}
            >
              {ocrData.ingredients.present
                ? (ocrData.ingredients.rawText || 'No text detected')
                : <span style={{ color: '#64748b', fontStyle: 'italic' }}>Ingredients image was omitted.</span>}
            </div>
          </div>

          {/* Surface 2: Nutrition OCR */}
          <div className="handoff-surface-card" style={{ gap: '0.5rem' }}>
            <div className="handoff-surface-header">
              <span className="handoff-surface-icon" aria-hidden="true">📊</span>
              <div>
                <h4 className="handoff-surface-title">Nutrition Table OCR Text</h4>
                <span className="handoff-source-badge">
                  {ocrData.nutrition.present
                    ? `${ocrData.nutrition.processingTimeMs} ms • ${ocrData.nutrition.confidence ? Math.round(ocrData.nutrition.confidence) + '% conf' : 'Tesseract'}`
                    : 'Not provided'}
                </span>
              </div>
            </div>
            <div
              style={{
                background: 'rgba(0, 0, 0, 0.4)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '0.375rem',
                padding: '0.75rem',
                maxHeight: '160px',
                overflowY: 'auto',
                fontFamily: 'monospace',
                fontSize: '0.8125rem',
                color: '#e2e8f0',
                whiteSpace: 'pre-wrap',
                lineHeight: '1.4'
              }}
            >
              {ocrData.nutrition.present
                ? (ocrData.nutrition.rawText || 'No text detected')
                : <span style={{ color: '#64748b', fontStyle: 'italic' }}>Nutrition image was omitted.</span>}
            </div>
          </div>
        </div>

        {/* Performance & Status metadata */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.75rem', color: '#94a3b8' }}>
          <span>Total OCR Time: <strong>{ocrData.totalProcessingTimeMs} ms</strong></span>
          <span>Status: <strong style={{ color: '#10b981' }}>{ocrData.status}</strong></span>
        </div>

        {/* Action Row */}
        <div className="handoff-actions-row">
          <Button variant="ghost" size="medium" onClick={onClose} disabled={isLoadingNormalization}>
            Close
          </Button>
          <Button variant="outline" size="medium" onClick={onReset} disabled={isLoadingNormalization}>
            Scan New Image
          </Button>
          <Button
            variant="primary"
            size="medium"
            onClick={onProceedToNormalization}
            disabled={isLoadingNormalization}
            icon={<span aria-hidden="true">✨</span>}
          >
            {isLoadingNormalization ? 'Normalizing with AI...' : 'Proceed to Normalization'}
          </Button>
        </div>
      </GlassCard>
    </div>
  );
};
