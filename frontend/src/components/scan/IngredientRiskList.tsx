import React, { useState, useMemo } from 'react';
import { IngredientRiskCard } from './IngredientRiskCard';
import type { IngredientRiskAnalysisResult } from '../../api/analysisApi';

interface IngredientRiskListProps {
  analysisResult: IngredientRiskAnalysisResult;
}

type FilterTab = 'ALL' | 'ATTENTION' | 'ADDITIVES' | 'NO_CONCERN' | 'UNCERTAIN';

export const IngredientRiskList: React.FC<IngredientRiskListProps> = ({ analysisResult }) => {
  const [activeTab, setActiveTab] = useState<FilterTab>('ALL');

  const { summary, items } = analysisResult;

  const attentionTotal =
    summary.highAttentionIngredients +
    summary.moderateAttentionIngredients +
    summary.lowAttentionIngredients;

  const filteredItems = useMemo(() => {
    switch (activeTab) {
      case 'ATTENTION':
        return items.filter(
          (i) =>
            i.riskLevel === 'HIGH_ATTENTION' ||
            i.riskLevel === 'MODERATE_ATTENTION' ||
            i.riskLevel === 'LOW_ATTENTION'
        );
      case 'ADDITIVES':
        return items.filter((i) => Boolean(i.additiveCode));
      case 'NO_CONCERN':
        return items.filter((i) => i.riskLevel === 'NO_CONCERN' || i.riskLevel === 'NO_SPECIFIC_CONCERN');
      case 'UNCERTAIN':
        return items.filter((i) => i.riskLevel === 'UNKNOWN' || i.evidenceStatus === 'INSUFFICIENT');
      case 'ALL':
      default:
        return items;
    }
  }, [items, activeTab]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      {/* High-Level Informational Metric Tiles */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
          gap: '0.6rem'
        }}
      >
        <div
          style={{
            background: 'rgba(255, 255, 255, 0.03)',
            border: '1px solid rgba(255, 255, 255, 0.07)',
            borderRadius: '0.5rem',
            padding: '0.6rem 0.8rem',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          <span style={{ fontSize: '0.6875rem', color: '#94a3b8' }}>Total Evaluated</span>
          <span style={{ fontSize: '1.25rem', fontWeight: 800, color: '#f8fafc' }}>
            {summary.totalIngredients}
          </span>
        </div>

        <div
          style={{
            background: summary.highAttentionIngredients > 0 ? 'rgba(239, 68, 68, 0.08)' : 'rgba(255, 255, 255, 0.03)',
            border: `1px solid ${summary.highAttentionIngredients > 0 ? 'rgba(239, 68, 68, 0.25)' : 'rgba(255, 255, 255, 0.07)'}`,
            borderRadius: '0.5rem',
            padding: '0.6rem 0.8rem',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          <span style={{ fontSize: '0.6875rem', color: summary.highAttentionIngredients > 0 ? '#fca5a5' : '#94a3b8' }}>
            High Attention
          </span>
          <span style={{ fontSize: '1.25rem', fontWeight: 800, color: summary.highAttentionIngredients > 0 ? '#f87171' : '#cbd5e1' }}>
            {summary.highAttentionIngredients}
          </span>
        </div>

        <div
          style={{
            background: summary.moderateAttentionIngredients > 0 ? 'rgba(249, 115, 22, 0.08)' : 'rgba(255, 255, 255, 0.03)',
            border: `1px solid ${summary.moderateAttentionIngredients > 0 ? 'rgba(249, 115, 22, 0.25)' : 'rgba(255, 255, 255, 0.07)'}`,
            borderRadius: '0.5rem',
            padding: '0.6rem 0.8rem',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          <span style={{ fontSize: '0.6875rem', color: summary.moderateAttentionIngredients > 0 ? '#fdba74' : '#94a3b8' }}>
            Moderate Attention
          </span>
          <span style={{ fontSize: '1.25rem', fontWeight: 800, color: summary.moderateAttentionIngredients > 0 ? '#fb923c' : '#cbd5e1' }}>
            {summary.moderateAttentionIngredients}
          </span>
        </div>

        <div
          style={{
            background: summary.lowAttentionIngredients > 0 ? 'rgba(245, 158, 11, 0.08)' : 'rgba(255, 255, 255, 0.03)',
            border: `1px solid ${summary.lowAttentionIngredients > 0 ? 'rgba(245, 158, 11, 0.25)' : 'rgba(255, 255, 255, 0.07)'}`,
            borderRadius: '0.5rem',
            padding: '0.6rem 0.8rem',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          <span style={{ fontSize: '0.6875rem', color: summary.lowAttentionIngredients > 0 ? '#fde68a' : '#94a3b8' }}>
            Low Attention
          </span>
          <span style={{ fontSize: '1.25rem', fontWeight: 800, color: summary.lowAttentionIngredients > 0 ? '#fbbf24' : '#cbd5e1' }}>
            {summary.lowAttentionIngredients}
          </span>
        </div>

        <div
          style={{
            background: 'rgba(16, 185, 129, 0.08)',
            border: '1px solid rgba(16, 185, 129, 0.2)',
            borderRadius: '0.5rem',
            padding: '0.6rem 0.8rem',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          <span style={{ fontSize: '0.6875rem', color: '#6ee7b7' }}>No Concern</span>
          <span style={{ fontSize: '1.25rem', fontWeight: 800, color: '#34d399' }}>
            {summary.noConcernIngredients}
          </span>
        </div>

        <div
          style={{
            background: 'rgba(168, 85, 247, 0.08)',
            border: '1px solid rgba(168, 85, 247, 0.2)',
            borderRadius: '0.5rem',
            padding: '0.6rem 0.8rem',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          <span style={{ fontSize: '0.6875rem', color: '#d8b4fe' }}>Additives</span>
          <span style={{ fontSize: '1.25rem', fontWeight: 800, color: '#c084fc' }}>
            {summary.additivesDetected}
          </span>
        </div>
      </div>

      {/* Filter Tabs */}
      <div
        style={{
          display: 'flex',
          gap: '0.4rem',
          flexWrap: 'wrap',
          borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
          paddingBottom: '0.5rem'
        }}
      >
        {[
          { id: 'ALL' as FilterTab, label: `All (${items.length})` },
          { id: 'ATTENTION' as FilterTab, label: `Attention Needed (${attentionTotal})` },
          { id: 'ADDITIVES' as FilterTab, label: `Additives (${summary.additivesDetected})` },
          { id: 'NO_CONCERN' as FilterTab, label: `No Specific Concern (${summary.noConcernIngredients})` },
          { id: 'UNCERTAIN' as FilterTab, label: `Uncertain (${summary.uncertainIngredients + summary.unknownIngredients})` }
        ].map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            style={{
              padding: '0.35rem 0.75rem',
              borderRadius: '0.375rem',
              fontSize: '0.75rem',
              fontWeight: 600,
              background: activeTab === tab.id ? 'rgba(56, 189, 248, 0.15)' : 'transparent',
              color: activeTab === tab.id ? '#38bdf8' : '#94a3b8',
              border: `1px solid ${activeTab === tab.id ? 'rgba(56, 189, 248, 0.35)' : 'transparent'}`,
              cursor: 'pointer',
              transition: 'all 0.15s ease'
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* List of Evaluated Ingredients */}
      {filteredItems.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '2rem 1rem', color: '#94a3b8', fontSize: '0.875rem' }}>
          No ingredients match the selected filter.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          {filteredItems.map((item, idx) => (
            <IngredientRiskCard key={idx} item={item} />
          ))}
        </div>
      )}

      {/* Regulatory & Health Educational Disclaimer */}
      <div
        style={{
          background: 'rgba(255, 255, 255, 0.02)',
          border: '1px solid rgba(255, 255, 255, 0.06)',
          borderRadius: '0.375rem',
          padding: '0.65rem 0.85rem',
          fontSize: '0.6875rem',
          color: '#94a3b8',
          lineHeight: '1.45',
          marginTop: '0.5rem'
        }}
      >
        ℹ️ <strong>Educational Note:</strong> Ingredient assessments are based exclusively on verified rules published by FSSAI and WHO/Codex Alimentarius. Attention levels highlight specific dietary qualities (e.g. saturated fat, sodium, sweetening agents) for personal awareness and do not constitute a medical diagnosis or food safety certification.
      </div>
    </div>
  );
};
