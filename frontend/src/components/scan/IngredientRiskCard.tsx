import React from 'react';
import { EvidenceBadge } from './EvidenceBadge';
import type { IngredientRiskItem, IngredientRiskLevel, RegulatoryStatus } from '../../api/analysisApi';

interface IngredientRiskCardProps {
  item: IngredientRiskItem;
}

export const IngredientRiskCard: React.FC<IngredientRiskCardProps> = ({ item }) => {

  const getRiskBadgeConfig = (level: IngredientRiskLevel) => {
    switch (level) {
      case 'HIGH_ATTENTION':
        return {
          label: 'High Attention',
          icon: '🛑',
          bg: 'rgba(239, 68, 68, 0.14)',
          border: 'rgba(239, 68, 68, 0.4)',
          color: '#f87171'
        };
      case 'MODERATE_ATTENTION':
        return {
          label: 'Moderate Attention',
          icon: '⚠️',
          bg: 'rgba(249, 115, 22, 0.14)',
          border: 'rgba(249, 115, 22, 0.35)',
          color: '#fb923c'
        };
      case 'LOW_ATTENTION':
        return {
          label: 'Low Attention',
          icon: 'ℹ️',
          bg: 'rgba(245, 158, 11, 0.12)',
          border: 'rgba(245, 158, 11, 0.3)',
          color: '#fbbf24'
        };
      case 'NO_CONCERN':
      case 'NO_SPECIFIC_CONCERN':
        return {
          label: 'No Specific Concern',
          icon: '✓',
          bg: 'rgba(16, 185, 129, 0.12)',
          border: 'rgba(16, 185, 129, 0.3)',
          color: '#34d399'
        };
      case 'UNKNOWN':
      default:
        return {
          label: 'Uncertain Identity',
          icon: '❓',
          bg: 'rgba(148, 163, 184, 0.1)',
          border: 'rgba(148, 163, 184, 0.25)',
          color: '#94a3b8'
        };
    }
  };

  const getRegulatoryStatusBadge = (status: RegulatoryStatus) => {
    switch (status) {
      case 'PERMITTED':
        return { label: 'Permitted Food Additive/Ingredient', color: '#38bdf8', bg: 'rgba(56, 189, 248, 0.1)' };
      case 'RESTRICTED':
        return { label: 'Usage Capped / Restricted', color: '#fb923c', bg: 'rgba(249, 115, 22, 0.12)' };
      case 'BANNED':
        return { label: 'Prohibited / Banned', color: '#f87171', bg: 'rgba(239, 68, 68, 0.15)' };
      case 'UNKNOWN':
      default:
        return { label: 'Unverified Regulatory Status', color: '#94a3b8', bg: 'rgba(148, 163, 184, 0.1)' };
    }
  };

  const riskConfig = getRiskBadgeConfig(item.riskLevel);
  const regConfig = getRegulatoryStatusBadge(item.regulatoryStatus);
  const displayName = item.normalizedName || item.originalIngredient;

  return (
    <div
      style={{
        background: 'rgba(255, 255, 255, 0.02)',
        border: '1px solid rgba(255, 255, 255, 0.07)',
        borderRadius: '0.5rem',
        padding: '0.75rem 1rem',
        transition: 'all 0.15s ease',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.5rem'
      }}
    >
      {/* Header Row: Name & Main Attention Badge */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.5rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.15rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flexWrap: 'wrap' }}>
            <span style={{ fontSize: '0.9375rem', fontWeight: 700, color: '#f8fafc' }}>
              {displayName}
            </span>
            {item.additiveCode && (
              <span
                style={{
                  fontSize: '0.6875rem',
                  fontWeight: 700,
                  fontFamily: 'monospace',
                  padding: '0.1rem 0.35rem',
                  borderRadius: '0.25rem',
                  background: 'rgba(168, 85, 247, 0.15)',
                  border: '1px solid rgba(168, 85, 247, 0.3)',
                  color: '#c084fc'
                }}
              >
                {item.additiveCode}
              </span>
            )}
          </div>

          {item.originalIngredient && item.originalIngredient !== displayName && (
            <span style={{ fontSize: '0.6875rem', color: '#64748b' }}>
              OCR: &quot;{item.originalIngredient}&quot;
            </span>
          )}

          {item.functionalClass && (
            <span style={{ fontSize: '0.75rem', color: '#38bdf8', fontWeight: 500 }}>
              Class: {item.functionalClass}
            </span>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.35rem' }}>
          <span
            style={{
              fontSize: '0.6875rem',
              fontWeight: 700,
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              background: riskConfig.bg,
              border: `1px solid ${riskConfig.border}`,
              color: riskConfig.color,
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.3rem',
              whiteSpace: 'nowrap'
            }}
          >
            <span>{riskConfig.icon}</span>
            <span>{riskConfig.label}</span>
          </span>

          <EvidenceBadge status={item.evidenceStatus} size="small" />
        </div>
      </div>

      {/* Regulatory Status Tag & Sources */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.4rem', paddingTop: '0.25rem' }}>
        <span
          style={{
            fontSize: '0.6875rem',
            padding: '0.1rem 0.4rem',
            borderRadius: '0.25rem',
            background: regConfig.bg,
            color: regConfig.color,
            fontWeight: 600
          }}
        >
          {regConfig.label}
        </span>

        {item.sources && item.sources.length > 0 && (
          <div style={{ display: 'flex', gap: '0.3rem', alignItems: 'center' }}>
            <span style={{ fontSize: '0.625rem', color: '#64748b', textTransform: 'uppercase' }}>Source:</span>
            {item.sources.map((s, idx) => (
              <span
                key={idx}
                style={{
                  fontSize: '0.625rem',
                  padding: '0.05rem 0.35rem',
                  borderRadius: '0.2rem',
                  background: 'rgba(255, 255, 255, 0.05)',
                  color: '#94a3b8',
                  fontWeight: 600
                }}
              >
                {s}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Rationale Bullet Points */}
      {item.reasons && item.reasons.length > 0 && (
        <div style={{ marginTop: '0.25rem', padding: '0.45rem 0.65rem', background: 'rgba(0, 0, 0, 0.2)', borderRadius: '0.375rem' }}>
          <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.75rem', color: '#cbd5e1', lineHeight: '1.45' }}>
            {item.reasons.map((reason, idx) => (
              <li key={idx}>{reason}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};
