import React from 'react';
import { GlassCard } from '../common/GlassCard';
import type { FoodClassificationResult, FoodCategory } from '../../api/analysisApi';

interface FoodClassificationCardProps {
  classification: FoodClassificationResult;
}

export const FoodClassificationCard: React.FC<FoodClassificationCardProps> = ({ classification }) => {
  const getCategoryTheme = (cat: FoodCategory) => {
    switch (cat) {
      case 'HUMAN_FOOD':
        return {
          label: 'Human Food Product',
          icon: '🍽️',
          border: 'rgba(16, 185, 129, 0.35)',
          bg: 'rgba(16, 185, 129, 0.1)',
          textColor: '#34d399',
          glow: '0 0 16px rgba(16, 185, 129, 0.15)'
        };
      case 'PET_FOOD':
        return {
          label: 'Pet Food / Animal Companion',
          icon: '🐾',
          border: 'rgba(245, 158, 11, 0.4)',
          bg: 'rgba(245, 158, 11, 0.12)',
          textColor: '#fbbf24',
          glow: '0 0 16px rgba(245, 158, 11, 0.2)'
        };
      case 'ANIMAL_FEED':
        return {
          label: 'Livestock / Animal Feed',
          icon: '🌾',
          border: 'rgba(234, 88, 12, 0.4)',
          bg: 'rgba(234, 88, 12, 0.12)',
          textColor: '#fb923c',
          glow: '0 0 16px rgba(234, 88, 12, 0.2)'
        };
      case 'NON_FOOD':
        return {
          label: 'Non-Food Item / Household',
          icon: '🛑',
          border: 'rgba(239, 68, 68, 0.4)',
          bg: 'rgba(239, 68, 68, 0.12)',
          textColor: '#f87171',
          glow: '0 0 16px rgba(239, 68, 68, 0.2)'
        };
      case 'UNKNOWN':
      default:
        return {
          label: 'Category Unverified',
          icon: '❓',
          border: 'rgba(148, 163, 184, 0.3)',
          bg: 'rgba(148, 163, 184, 0.08)',
          textColor: '#94a3b8',
          glow: 'none'
        };
    }
  };

  const theme = getCategoryTheme(classification.category);
  const isNonHuman = classification.category !== 'HUMAN_FOOD' && classification.category !== 'UNKNOWN';

  return (
    <GlassCard variant="elevated" padding="large" style={{ border: `1px solid ${theme.border}`, boxShadow: theme.glow }}>
      {/* Top Category Badge & Certainty */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span
            style={{
              fontSize: '0.75rem',
              fontWeight: 800,
              letterSpacing: '0.04em',
              textTransform: 'uppercase',
              padding: '0.3rem 0.75rem',
              borderRadius: '9999px',
              background: theme.bg,
              border: `1px solid ${theme.border}`,
              color: theme.textColor,
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem'
            }}
          >
            <span>{theme.icon}</span>
            <span>{theme.label}</span>
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Certainty:</span>
          <span
            style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              padding: '0.15rem 0.5rem',
              borderRadius: '0.25rem',
              background: 'rgba(255, 255, 255, 0.06)',
              color: classification.certainty === 'HIGH' ? '#34d399' : classification.certainty === 'MEDIUM' ? '#38bdf8' : '#fbbf24'
            }}
          >
            {classification.certainty}
            {classification.confidence != null && ` (${Math.round(classification.confidence * 100)}%)`}
          </span>
        </div>
      </div>

      {/* Main Rationale */}
      <div style={{ marginBottom: '1rem' }}>
        <h4 style={{ fontSize: '1rem', fontWeight: 700, color: '#f8fafc', margin: '0 0 0.4rem' }}>
          Product Intent Evaluation
        </h4>
        <p style={{ fontSize: '0.875rem', color: '#cbd5e1', lineHeight: '1.5', margin: 0 }}>
          {classification.reason}
        </p>
      </div>

      {/* Non-Human Consumption Warning Banner */}
      {isNonHuman && (
        <div
          style={{
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.35)',
            borderRadius: '0.5rem',
            padding: '0.75rem 1rem',
            marginBottom: '1rem',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '0.6rem'
          }}
          role="alert"
        >
          <span style={{ fontSize: '1.2rem', flexShrink: 0 }}>⚠️</span>
          <div>
            <h5 style={{ fontSize: '0.8125rem', fontWeight: 700, color: '#fca5a5', margin: '0 0 0.2rem' }}>
              Notice: Not Intended for Human Consumption
            </h5>
            <p style={{ fontSize: '0.75rem', color: '#fecaca', margin: 0, lineHeight: '1.4' }}>
              Package indicators establish that this product is formulated specifically as {theme.label.toLowerCase()}. Nutritional and ingredient risk guidelines apply exclusively to human food standards.
            </p>
          </div>
        </div>
      )}

      {/* Supporting Evidence Chips */}
      {classification.evidence && classification.evidence.length > 0 && (
        <div style={{ marginBottom: '0.75rem' }}>
          <span style={{ fontSize: '0.6875rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: '#94a3b8', display: 'block', marginBottom: '0.4rem' }}>
            Supporting Evidence Extracted
          </span>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem' }}>
            {classification.evidence.map((ev, idx) => (
              <span
                key={idx}
                style={{
                  fontSize: '0.6875rem',
                  padding: '0.2rem 0.5rem',
                  borderRadius: '0.375rem',
                  background: 'rgba(255, 255, 255, 0.05)',
                  border: '1px solid rgba(255, 255, 255, 0.08)',
                  color: '#e2e8f0'
                }}
              >
                🔍 {ev}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Warnings / Advisory List */}
      {classification.warnings && classification.warnings.length > 0 && (
        <div style={{ marginTop: '0.75rem', padding: '0.6rem 0.8rem', background: 'rgba(245, 158, 11, 0.06)', border: '1px solid rgba(245, 158, 11, 0.2)', borderRadius: '0.375rem' }}>
          <h5 style={{ fontSize: '0.6875rem', fontWeight: 700, color: '#fbbf24', textTransform: 'uppercase', letterSpacing: '0.05em', margin: '0 0 0.3rem' }}>
            Label Ambiguities &amp; Advisories
          </h5>
          <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.75rem', color: '#cbd5e1', lineHeight: '1.45' }}>
            {classification.warnings.map((w, idx) => (
              <li key={idx}>{w}</li>
            ))}
          </ul>
        </div>
      )}
    </GlassCard>
  );
};
