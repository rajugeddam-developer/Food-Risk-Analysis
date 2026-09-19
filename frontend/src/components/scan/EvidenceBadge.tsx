import React from 'react';
import type { EvidenceStatus } from '../../api/analysisApi';

interface EvidenceBadgeProps {
  status: EvidenceStatus | string;
  size?: 'small' | 'medium';
}

export const EvidenceBadge: React.FC<EvidenceBadgeProps> = ({ status, size = 'small' }) => {
  const getBadgeConfig = () => {
    switch (status) {
      case 'SUPPORTED':
        return {
          label: 'Verified Regulatory Evidence',
          icon: '✓',
          bg: 'rgba(16, 185, 129, 0.12)',
          border: 'rgba(16, 185, 129, 0.3)',
          color: '#34d399'
        };
      case 'PARTIALLY_SUPPORTED':
        return {
          label: 'Partially Documented',
          icon: 'ℹ️',
          bg: 'rgba(56, 189, 248, 0.12)',
          border: 'rgba(56, 189, 248, 0.3)',
          color: '#38bdf8'
        };
      case 'INSUFFICIENT':
        return {
          label: 'Insufficient Label Evidence',
          icon: '⚠️',
          bg: 'rgba(245, 158, 11, 0.12)',
          border: 'rgba(245, 158, 11, 0.3)',
          color: '#fbbf24'
        };
      case 'UNKNOWN':
      default:
        return {
          label: 'Uncertain Identity',
          icon: '❓',
          bg: 'rgba(148, 163, 184, 0.12)',
          border: 'rgba(148, 163, 184, 0.25)',
          color: '#94a3b8'
        };
    }
  };

  const config = getBadgeConfig();
  const padding = size === 'small' ? '0.15rem 0.45rem' : '0.25rem 0.65rem';
  const fontSize = size === 'small' ? '0.6875rem' : '0.75rem';

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '0.3rem',
        padding,
        fontSize,
        fontWeight: 600,
        borderRadius: '9999px',
        backgroundColor: config.bg,
        border: `1px solid ${config.border}`,
        color: config.color,
        letterSpacing: '0.01em',
        whiteSpace: 'nowrap'
      }}
      title={`Evidence Status: ${status}`}
    >
      <span aria-hidden="true">{config.icon}</span>
      <span>{config.label}</span>
    </span>
  );
};
