import React from 'react';
import { GlassCard } from '../common/GlassCard';
import { Button } from '../common/Button';
import type { CapturedImage } from './scanTypes';

interface M5HandoffModalProps {
  isOpen: boolean;
  ingredientsImage: CapturedImage | null;
  nutritionImage: CapturedImage | null;
  onClose: () => void;
  onReset: () => void;
}

export const M5HandoffModal: React.FC<M5HandoffModalProps> = ({
  isOpen,
  ingredientsImage,
  nutritionImage,
  onClose,
  onReset
}) => {
  if (!isOpen || !ingredientsImage || !nutritionImage) return null;

  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="handoff-modal-title"
    >
      <GlassCard variant="elevated" padding="large" className="handoff-modal-card">
        {/* Modal Header */}
        <div className="handoff-modal-header">
          <div className="handoff-badge">
            <span>MILESTONE M4 • CAPTURE COMPLETE</span>
          </div>
          <h2 id="handoff-modal-title" className="handoff-modal-title">
            Packaging Surfaces Ready for Analysis
          </h2>
          <p className="handoff-modal-subtitle">
            Both packaging surfaces have been successfully captured, validated, and held in temporary client memory.
          </p>
        </div>

        {/* Captured Surfaces Summary */}
        <div className="handoff-surfaces-grid">
          {/* Surface 1: Ingredients */}
          <div className="handoff-surface-card">
            <div className="handoff-surface-header">
              <span className="handoff-surface-icon" aria-hidden="true">🏷️</span>
              <div>
                <h4 className="handoff-surface-title">Ingredients Label</h4>
                <span className="handoff-source-badge">
                  {ingredientsImage.capturedVia === 'camera' ? '📷 Rear Camera' : '📁 Gallery Upload'}
                </span>
              </div>
            </div>
            <div className="handoff-thumbnail-frame">
              <img
                src={ingredientsImage.previewUrl}
                alt="Ingredients Label preview"
                className="handoff-thumbnail-img"
              />
            </div>
            <div className="handoff-surface-meta">
              <span className="handoff-meta-name" title={ingredientsImage.name}>
                {ingredientsImage.name}
              </span>
              <div className="handoff-meta-tags">
                <span className="handoff-meta-tag">{ingredientsImage.sizeFormatted}</span>
                {ingredientsImage.dimensions && (
                  <span className="handoff-meta-tag">
                    {ingredientsImage.dimensions.width} × {ingredientsImage.dimensions.height}
                  </span>
                )}
                <span className="handoff-meta-tag">{ingredientsImage.mimeType.split('/')[1]?.toUpperCase()}</span>
              </div>
            </div>
          </div>

          {/* Surface 2: Nutrition */}
          <div className="handoff-surface-card">
            <div className="handoff-surface-header">
              <span className="handoff-surface-icon" aria-hidden="true">📊</span>
              <div>
                <h4 className="handoff-surface-title">Nutrition Table</h4>
                <span className="handoff-source-badge">
                  {nutritionImage.capturedVia === 'camera' ? '📷 Rear Camera' : '📁 Gallery Upload'}
                </span>
              </div>
            </div>
            <div className="handoff-thumbnail-frame">
              <img
                src={nutritionImage.previewUrl}
                alt="Nutrition Table preview"
                className="handoff-thumbnail-img"
              />
            </div>
            <div className="handoff-surface-meta">
              <span className="handoff-meta-name" title={nutritionImage.name}>
                {nutritionImage.name}
              </span>
              <div className="handoff-meta-tags">
                <span className="handoff-meta-tag">{nutritionImage.sizeFormatted}</span>
                {nutritionImage.dimensions && (
                  <span className="handoff-meta-tag">
                    {nutritionImage.dimensions.width} × {nutritionImage.dimensions.height}
                  </span>
                )}
                <span className="handoff-meta-tag">{nutritionImage.mimeType.split('/')[1]?.toUpperCase()}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Milestone M5 OCR Pipeline Notice */}
        <div className="handoff-pipeline-notice">
          <div className="notice-icon" aria-hidden="true">🔄</div>
          <div className="notice-content">
            <h5 className="notice-heading">Next Stage: Milestone M5 (OCR Integration)</h5>
            <p className="notice-text">
              In Milestone M5, these client-side images will feed into the OCR text extraction engine to transcribe raw ingredient lists and nutritional tables. No AI models (Gemini), risk scoring, or permanent uploads were executed in M4.
            </p>
          </div>
        </div>

        {/* Privacy Assurance */}
        <p className="handoff-privacy-text">
          🛡️ <strong>Privacy Protection:</strong> All images remain strictly within temporary browser memory (Object URLs) and will be discarded upon session termination or reset.
        </p>

        {/* Action Buttons */}
        <div className="handoff-actions-row">
          <Button variant="outline" size="medium" onClick={onReset}>
            Reset & Scan New
          </Button>
          <Button variant="primary" size="medium" onClick={onClose}>
            Back to Preview
          </Button>
        </div>
      </GlassCard>
    </div>
  );
};

export default M5HandoffModal;
