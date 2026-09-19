import React from 'react';
import { BrandOrbs } from './BrandOrbs';
import './styles.css';

interface BrandOrbsSceneProps {
  variant?: 'aura' | 'gemini' | 'react' | 'openai';
  className?: string;
  isBackground?: boolean;
}

export const BrandOrbsScene: React.FC<BrandOrbsSceneProps> = ({
  variant = 'aura',
  className = '',
  isBackground = false
}) => {
  return (
    <div
      className={`shader-frame ${isBackground ? 'shader-frame-hero-bg' : ''} ${className}`}
      aria-hidden="true"
    >
      <BrandOrbs
        variant={variant}
        size="medium"
        mode="dark"
        speed={1.00}
      />
    </div>
  );
};

export default BrandOrbsScene;
