import React from 'react';
import { type ProductClassificationType, CLASSIFICATION_MAP } from '../../mock/food-data';

interface ClassificationBadgeProps {
  classification: ProductClassificationType;
  showDetails?: boolean;
}

export const ClassificationBadge: React.FC<ClassificationBadgeProps> = ({
  classification,
  showDetails = true
}) => {
  const item = CLASSIFICATION_MAP[classification] || CLASSIFICATION_MAP.HUMAN_FOOD;

  return (
    <div className={`classification-card ${item.badgeClass}`}>
      <div className="classification-header">
        <span className="classification-icon" aria-hidden="true">
          {item.icon}
        </span>
        <div className="classification-title-wrap">
          <span className="classification-subtitle">PRODUCT CLASSIFICATION</span>
          <h4 className="classification-title">{item.title}</h4>
        </div>
      </div>

      {showDetails && (
        <div className="classification-body">
          <p className="classification-desc">{item.description}</p>
          {item.safetyAdvisory && (
            <div className="classification-advisory">
              <span className="advisory-icon">⚠️</span>
              <p className="advisory-text">{item.safetyAdvisory}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default ClassificationBadge;
