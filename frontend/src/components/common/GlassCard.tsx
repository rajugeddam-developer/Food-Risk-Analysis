import React, { type ReactNode, type CSSProperties } from 'react';

interface GlassCardProps {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
  variant?: 'default' | 'elevated' | 'subtle' | 'interactive';
  padding?: 'none' | 'small' | 'medium' | 'large';
  onClick?: () => void;
}

export const GlassCard: React.FC<GlassCardProps> = ({
  children,
  className = '',
  style,
  variant = 'default',
  padding = 'medium',
  onClick
}) => {
  const padClass = {
    none: 'glass-card--pad-none',
    small: 'glass-card--pad-sm',
    medium: 'glass-card--pad-md',
    large: 'glass-card--pad-lg'
  }[padding];

  const varClass = {
    default: 'glass-card--default',
    elevated: 'glass-card--elevated',
    subtle: 'glass-card--subtle',
    interactive: 'glass-card--interactive'
  }[variant];

  return (
    <div
      className={`glass-card ${varClass} ${padClass} ${className}`}
      style={style}
      onClick={onClick}
    >
      {children}
    </div>
  );
};

export default GlassCard;
