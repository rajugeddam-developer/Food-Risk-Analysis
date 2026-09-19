import React from 'react';

interface ScoreGaugeProps {
  score: number;
  maxScore?: number;
  riskBand: 'LOW' | 'MODERATE CONCERN' | 'HIGH' | 'CRITICAL';
  size?: 'normal' | 'compact';
}

export const ScoreGauge: React.FC<ScoreGaugeProps> = ({
  score,
  maxScore = 100,
  riskBand,
  size = 'normal'
}) => {
  const radius = size === 'compact' ? 52 : 72;
  const strokeWidth = size === 'compact' ? 10 : 12;
  const normalizedRadius = radius - strokeWidth / 2;
  const circumference = normalizedRadius * 2 * Math.PI;
  const strokeDashoffset = circumference - (score / maxScore) * circumference;

  // Determine color based on risk score (lower score = healthier, higher score = higher risk)
  // or 0-100 where 0 is pristine and 100 is critical risk
  const getBandColor = () => {
    switch (riskBand) {
      case 'LOW':
        return '#10b981'; // Emerald
      case 'MODERATE CONCERN':
        return '#f59e0b'; // Amber
      case 'HIGH':
        return '#f97316'; // Orange
      case 'CRITICAL':
        return '#ef4444'; // Red
      default:
        return '#38bdf8';
    }
  };

  const bandColor = getBandColor();
  const dimension = radius * 2;

  return (
    <div className={`score-gauge ${size === 'compact' ? 'score-gauge--compact' : ''}`}>
      <div className="score-gauge-ring-wrapper" style={{ width: dimension, height: dimension }}>
        <svg
          height={dimension}
          width={dimension}
          className="score-gauge-svg"
          aria-hidden="true"
        >
          {/* Background track */}
          <circle
            stroke="rgba(255, 255, 255, 0.08)"
            fill="transparent"
            strokeWidth={strokeWidth}
            r={normalizedRadius}
            cx={radius}
            cy={radius}
          />
          {/* Animated score progress */}
          <circle
            stroke={bandColor}
            fill="transparent"
            strokeWidth={strokeWidth}
            strokeDasharray={`${circumference} ${circumference}`}
            style={{
              strokeDashoffset,
              transition: 'stroke-dashoffset 1.2s cubic-bezier(0.4, 0, 0.2, 1)',
              filter: `drop-shadow(0 0 8px ${bandColor}55)`
            }}
            strokeLinecap="round"
            r={normalizedRadius}
            cx={radius}
            cy={radius}
            transform={`rotate(-90 ${radius} ${radius})`}
          />
        </svg>

        <div className="score-gauge-center">
          <span className="score-gauge-number">{score}</span>
          <span className="score-gauge-max">/{maxScore}</span>
        </div>
      </div>

      <div className="score-gauge-band" style={{ color: bandColor, borderColor: `${bandColor}40` }}>
        <span className="score-gauge-dot" style={{ backgroundColor: bandColor }} />
        <span>{riskBand}</span>
      </div>
    </div>
  );
};

export default ScoreGauge;
