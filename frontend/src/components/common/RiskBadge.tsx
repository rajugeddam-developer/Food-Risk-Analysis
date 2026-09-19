import React from 'react';
import { type RiskStatus } from '../../mock/food-data';

interface RiskBadgeProps {
  status: RiskStatus;
  label?: string;
  size?: 'small' | 'medium';
  showDot?: boolean;
}

const STATUS_ICONS: Record<RiskStatus, string> = {
  good: '🟢',
  caution: '🟡',
  high_concern: '🟠',
  avoid: '🔴'
};

const DEFAULT_LABELS: Record<RiskStatus, string> = {
  good: 'GOOD',
  caution: 'CAUTION',
  high_concern: 'HIGH CONCERN',
  avoid: 'AVOID'
};

export const RiskBadge: React.FC<RiskBadgeProps> = ({
  status,
  label,
  size = 'small',
  showDot = true
}) => {
  const displayLabel = label || DEFAULT_LABELS[status];
  const sizeClass = size === 'small' ? 'risk-badge--sm' : 'risk-badge--md';

  return (
    <span className={`risk-badge risk-badge--${status} ${sizeClass}`}>
      {showDot && <span className="risk-badge-icon">{STATUS_ICONS[status]}</span>}
      <span className="risk-badge-label">{displayLabel}</span>
    </span>
  );
};

export default RiskBadge;
