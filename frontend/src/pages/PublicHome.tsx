import React, { useState } from 'react';
import { Link } from '../router/Router';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
import { ScoreGauge } from '../components/common/ScoreGauge';
import { RiskBadge } from '../components/common/RiskBadge';
import { Icon } from '../components/common/Icon';
import './home.css';

export const PublicHome: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'visual' | 'preview'>('visual');

  return (
    <div className="home-page public-home">
      {/* ====================================================================
          Hero Section (Visual Lock to REFERENCE 02 Top-Left)
          ==================================================================== */}
      <section className="hero-section" aria-label="Hero">
        <div className="hero-container">
          {/* Left Column: Core Value Proposition */}
          <div className="hero-content">
            <div className="hero-eyebrow">
              <span className="eyebrow-dot" />
              <span>PROGRESSIVE FOOD-TECH INTELLIGENCE</span>
            </div>

            <h1 className="hero-heading">
              KNOW WHAT <br />
              <span className="hero-heading-gradient">YOU EAT.</span>
            </h1>

            <p className="hero-subtitle">
              Understand what's inside your food before you eat it.
            </p>

            <p className="hero-description">
              Scan the ingredients and nutrition label to get a simple food-risk analysis and make a more informed choice.
            </p>

            <div className="hero-cta-group">
              <Link to="/scan" className="cta-link-wrapper">
                <Button variant="primary" size="large" icon={<Icon name="camera" size={18} color="#20C7FF" />}>
                  SCAN YOUR FOOD →
                </Button>
              </Link>

              <Link to="/how-it-works" className="cta-link-wrapper">
                <Button variant="outline" size="large" icon={<Icon name="info" size={18} />}>
                  HOW IT WORKS →
                </Button>
              </Link>
            </div>

            {/* Feature / Trust Badges (REFERENCE 02) */}
            <div className="hero-trust-badges">
              <div className="trust-badge-item">
                <Icon name="shield" size={15} color="#20C7FF" />
                <span>Science-backed</span>
              </div>
              <div className="trust-badge-item">
                <Icon name="activity" size={15} color="#20C7FF" />
                <span>Instant Analysis</span>
              </div>
              <div className="trust-badge-item">
                <Icon name="globe" size={15} color="#20C7FF" />
                <span>Global Standards</span>
              </div>
              <div className="trust-badge-item">
                <Icon name="lock" size={15} color="#20C7FF" />
                <span>Privacy First</span>
              </div>
            </div>
          </div>

          {/* Right Column: Hero Visual & Analytical Card (REFERENCE 02) */}
          <div className="hero-visual-wrapper">
            {/* View Mode Toggle */}
            <div className="hero-view-toggle">
              <button
                type="button"
                className={`toggle-tab ${activeTab === 'visual' ? 'toggle-tab--active' : ''}`}
                onClick={() => setActiveTab('visual')}
              >
                Cinematic Visual
              </button>
              <button
                type="button"
                className={`toggle-tab ${activeTab === 'preview' ? 'toggle-tab--active' : ''}`}
                onClick={() => setActiveTab('preview')}
              >
                Interactive Data Preview
              </button>
            </div>

            {activeTab === 'visual' ? (
              <div className="hero-cinematic-scene">
                {/* Volumetric Top Spotlight Beam */}
                <div className="food-spotlight-source" aria-hidden="true" />

                {/* Soft Luminous Stage Floor Reflection */}
                <div className="food-floor-reflection" aria-hidden="true" />

                {/* Organic Food Stage - Dissolves seamlessly into dark environment */}
                <div className="food-organic-stage">
                  <div className="food-ambient-halo" aria-hidden="true" />
                  <img
                    src="/images/food-hero.webp"
                    alt="Dark studio food bowl with fresh vegetables illuminated under cyan volumetric light"
                    className="food-organic-img"
                    loading="eager"
                  />
                </div>

                {/* Futuristic Holographic Glass HUD (References 01 & 02) */}
                <div className="holographic-food-hud">
                  <div className="hud-glass-panel">
                    <div className="hud-top-bar">
                      <div className="hud-brand-tag">
                        <span className="hud-pulse-node" />
                        <span>BIO-INTELLIGENCE TELEMETRY</span>
                      </div>
                      <div className="hud-circuit-id">
                        <Icon name="shield" size={13} color="#20C7FF" />
                        <span>ISO-22000 / WHO</span>
                      </div>
                    </div>

                    <div className="hud-data-row">
                      {/* Circular Telemetry Ring (Ref 01) */}
                      <div className="hud-circle-gauge">
                        <svg className="hud-svg-ring" viewBox="0 0 80 80">
                          <circle className="hud-ring-bg" cx="40" cy="40" r="34" />
                          <circle className="hud-ring-val" cx="40" cy="40" r="34" />
                        </svg>
                        <div className="hud-gauge-center">
                          <span className="hud-gauge-num">450</span>
                          <span className="hud-gauge-unit">KCAL</span>
                        </div>
                      </div>

                      {/* Stream of Live Nutrient Indicators (Ref 01) */}
                      <div className="hud-metrics-stream">
                        <div className="hud-metric-pill">
                          <span className="hud-dot dot-green" />
                          <span className="hud-metric-label">CARBS:</span>
                          <span className="hud-metric-val">48g</span>
                        </div>
                        <div className="hud-metric-pill">
                          <span className="hud-dot dot-cyan" />
                          <span className="hud-metric-label">PROT:</span>
                          <span className="hud-metric-val">24g</span>
                        </div>
                        <div className="hud-metric-pill">
                          <span className="hud-dot dot-amber" />
                          <span className="hud-metric-label">FAT:</span>
                          <span className="hud-metric-val">12g</span>
                        </div>
                        <div className="hud-metric-pill">
                          <span className="hud-dot dot-green" />
                          <span className="hud-metric-label">SODIUM:</span>
                          <span className="hud-metric-val">LOW</span>
                        </div>
                      </div>
                    </div>

                    <div className="hud-waveform-row">
                      <div className="hud-status-text">
                        <Icon name="activity" size={13} color="#10C98B" />
                        <span>NOVA GROUP 1 &bull; UNPROCESSED</span>
                      </div>
                      <div className="analyzing-pill">
                        <span className="pulse-dot" />
                        <span>VERIFIED SAFE</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              /* Interactive Food Risk Preview Card */
              <GlassCard variant="elevated" padding="large" className="preview-card">
                <div className="preview-card-header">
                  <span className="preview-tag">FOOD RISK PREVIEW</span>
                  <span className="preview-product-name">Sample Packaged Snack</span>
                </div>

                <div className="preview-gauge-row">
                  <ScoreGauge
                    score={62}
                    maxScore={100}
                    riskBand="MODERATE CONCERN"
                    size="compact"
                  />
                  <div className="preview-summary-col">
                    <span className="preview-label">Assessment</span>
                    <h4 className="preview-title">Moderate Concern</h4>
                    <p className="preview-note">
                      High sodium and saturated fats offset whole vegetable base.
                    </p>
                  </div>
                </div>

                {/* Quick ingredient breakdown preview */}
                <div className="preview-section-divider" />
                <div className="preview-mini-section">
                  <span className="preview-mini-title">Ingredient Analysis</span>
                  <div className="preview-badge-row">
                    <div className="preview-badge-item">
                      <span className="preview-item-name">Potato</span>
                      <RiskBadge status="good" label="GOOD" size="small" />
                    </div>
                    <div className="preview-badge-item">
                      <span className="preview-item-name">Palm Oil</span>
                      <RiskBadge status="caution" label="CAUTION" size="small" />
                    </div>
                    <div className="preview-badge-item">
                      <span className="preview-item-name">Salt</span>
                      <RiskBadge status="high_concern" label="HIGH CONCERN" size="small" />
                    </div>
                    <div className="preview-badge-item">
                      <span className="preview-item-name">E621</span>
                      <RiskBadge status="caution" label="CAUTION" size="small" />
                    </div>
                  </div>
                </div>

                {/* Quick nutrition table preview */}
                <div className="preview-section-divider" />
                <div className="preview-mini-section">
                  <span className="preview-mini-title">Nutrition Benchmarks</span>
                  <div className="preview-nutri-grid">
                    <div className="preview-nutri-item">
                      <span className="nutri-name">Sodium</span>
                      <RiskBadge status="avoid" label="HIGH" size="small" />
                    </div>
                    <div className="preview-nutri-item">
                      <span className="nutri-name">Fat</span>
                      <RiskBadge status="caution" label="MODERATE" size="small" />
                    </div>
                    <div className="preview-nutri-item">
                      <span className="nutri-name">Sugar</span>
                      <RiskBadge status="caution" label="MODERATE" size="small" />
                    </div>
                  </div>
                </div>
              </GlassCard>
            )}
          </div>
        </div>
      </section>

      {/* ====================================================================
          Food Safety Section (WHO Fact Sheet Highlights - REFERENCE 02 Top-Right)
          ==================================================================== */}
      <section className="food-safety-section" id="food-safety" aria-labelledby="food-safety-heading">
        <GlassCard variant="elevated" padding="large" className="food-safety-card">
          <div className="safety-header-container">
            <div className="safety-eyebrow-row">
              <span className="safety-eyebrow">
                <Icon name="globe" size={14} color="#20C7FF" />
                GLOBAL HEALTH CONTEXT &bull; WHO FACT SHEET
              </span>
              <a
                href="https://www.who.int/news-room/fact-sheets/detail/food-safety"
                target="_blank"
                rel="noopener noreferrer"
                className="safety-who-link"
              >
                <span>WHO Official Fact Sheet</span>
                <Icon name="external-link" size={13} />
              </a>
            </div>

            <h2 id="food-safety-heading" className="safety-main-title">
              Food safety
            </h2>

            <div className="safety-metadata-bar">
              <div className="safety-meta-item">
                <Icon name="calendar" size={14} color="#94A3B8" />
                <span className="meta-text">4 June 2026</span>
              </div>
              <span className="meta-divider" aria-hidden="true">&bull;</span>
              <div className="safety-meta-item">
                <Icon name="clock" size={14} color="#94A3B8" />
                <span className="meta-text">Reading time: 7 min (1998 words)</span>
              </div>
            </div>

            {/* Language translations */}
            <div className="safety-languages-wrapper">
              <span className="languages-label">Read in other languages:</span>
              <div className="language-pills-list">
                {['English', 'العربية', '中文', 'Français', 'Русский', 'Español'].map((lang, idx) => (
                  <span key={lang} className={`lang-pill ${idx === 0 ? 'lang-pill--active' : ''}`}>
                    {lang}
                  </span>
                ))}
              </div>
            </div>
          </div>

          <div className="safety-section-divider" />

          {/* Key Facts Subsection */}
          <div className="safety-facts-block">
            <div className="facts-header-row">
              <div className="facts-title-group">
                <span className="facts-mini-tag">CRITICAL DATA</span>
                <h3 className="facts-heading">Key facts</h3>
              </div>
              <span className="facts-count-badge">6 Key Pillars →</span>
            </div>

            {/* Quick Stat Highlights */}
            <div className="safety-stats-grid">
              <div className="safety-stat-box stat-red">
                <div className="stat-number">866M</div>
                <div className="stat-label">Fall ill annually (~1 in 9 people)</div>
                <div className="stat-subtext">1.52 million deaths every year</div>
              </div>
              <div className="safety-stat-box stat-amber">
                <div className="stat-number">US$ 310B</div>
                <div className="stat-label">Lost each year worldwide</div>
                <div className="stat-subtext">Productivity &amp; medical expenses</div>
              </div>
              <div className="safety-stat-box stat-cyan">
                <div className="stat-number">29%</div>
                <div className="stat-label">Burden on children &lt; 5 years</div>
                <div className="stat-subtext">143 000 deaths in 2021</div>
              </div>
              <div className="safety-stat-box stat-green">
                <div className="stat-number">One Health</div>
                <div className="stat-label">Shared responsibility</div>
                <div className="stat-subtext">Multisectoral national action</div>
              </div>
            </div>

            {/* 6 Key Facts Structured Grid */}
            <div className="facts-cards-grid">
              <div className="fact-item-card">
                <div className="fact-icon-wrapper icon-green">
                  <Icon name="leaf" size={18} color="#10C98B" />
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">NUTRITION &amp; FOOD SECURITY</span>
                  <p className="fact-item-text">
                    Food safety, nutrition and food security are inextricably linked.
                  </p>
                </div>
              </div>

              <div className="fact-item-card">
                <div className="fact-icon-wrapper icon-red">
                  <Icon name="alert-triangle" size={18} color="#FF5B61" />
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">GLOBAL CONTAMINATION TOLL</span>
                  <p className="fact-item-text">
                    An estimated 866 million – almost 1 in 9 people in the world – fall ill after eating contaminated food.
                  </p>
                </div>
              </div>

              <div className="fact-item-card">
                <div className="fact-icon-wrapper icon-amber">
                  <Icon name="scale" size={18} color="#F5A400" />
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">ECONOMIC BURDEN</span>
                  <p className="fact-item-text">
                    US$ 310 billion is lost each year in productivity and medical expenses resulting from unsafe food worldwide.
                  </p>
                </div>
              </div>

              <div className="fact-item-card">
                <div className="fact-icon-wrapper icon-cyan">
                  <Icon name="heart" size={18} color="#20C7FF" />
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">CHILD VULNERABILITY</span>
                  <p className="fact-item-text">
                    Children under 5 years of age experience 29% of the health burden due to unsafe food.
                  </p>
                </div>
              </div>

              <div className="fact-item-card">
                <div className="fact-icon-wrapper icon-purple">
                  <Icon name="activity" size={18} color="#8B5CF6" />
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">SOCIOECONOMIC DEVELOPMENT</span>
                  <p className="fact-item-text">
                    Foodborne diseases impede development by straining health systems and harming trade.
                  </p>
                </div>
              </div>

              <div className="fact-item-card">
                <div className="fact-icon-wrapper icon-blue">
                  <Icon name="globe" size={18} color="#38D7FF" />
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">SHARED RESPONSIBILITY</span>
                  <p className="fact-item-text">
                    Food safety is a shared responsibility among authorities requiring a multisectoral approach.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </GlassCard>
      </section>

      {/* ====================================================================
          How It Works Section (The 4-Step Pipeline - REFERENCE 02 Middle-Left)
          ==================================================================== */}
      <section className="how-section" aria-label="How It Works Pipeline">
        <div className="section-header-center">
          <span className="section-eyebrow">THE 4-STEP PIPELINE</span>
          <h2 className="section-title">How It Works</h2>
          <p className="section-subtitle">
            From photo capture to science-backed dietary awareness in seconds.
          </p>
        </div>

        <div className="steps-flow-container">
          {/* Step 1 */}
          <GlassCard variant="interactive" padding="medium" className="step-card">
            <div className="step-card-header">
              <span className="step-number">01</span>
              <Icon name="camera" size={20} color="#20C7FF" />
            </div>
            <h3 className="step-heading">CAPTURE</h3>
            <p className="step-text">
              Take photos of the food label using your phone camera or upload images from your gallery.
            </p>
          </GlassCard>

          <div className="step-connector" aria-hidden="true">
            <Icon name="arrow-right" size={20} color="rgba(32, 199, 255, 0.4)" />
          </div>

          {/* Step 2 */}
          <GlassCard variant="interactive" padding="medium" className="step-card">
            <div className="step-card-header">
              <span className="step-number">02</span>
              <Icon name="sparkles" size={20} color="#20C7FF" />
            </div>
            <h3 className="step-heading">EXTRACT</h3>
            <p className="step-text">
              The system reads the ingredients and nutrition information from both packaging surfaces.
            </p>
          </GlassCard>

          <div className="step-connector" aria-hidden="true">
            <Icon name="arrow-right" size={20} color="rgba(32, 199, 255, 0.4)" />
          </div>

          {/* Step 3 */}
          <GlassCard variant="interactive" padding="medium" className="step-card">
            <div className="step-card-header">
              <span className="step-number">03</span>
              <Icon name="bar-chart" size={20} color="#20C7FF" />
            </div>
            <h3 className="step-heading">ANALYZE</h3>
            <p className="step-text">
              AI and rule analysis engine evaluates chemicals, additives, NOVA levels, and WHO/FSSAI limits.
            </p>
          </GlassCard>

          <div className="step-connector" aria-hidden="true">
            <Icon name="arrow-right" size={20} color="rgba(32, 199, 255, 0.4)" />
          </div>

          {/* Step 4 */}
          <GlassCard variant="interactive" padding="medium" className="step-card">
            <div className="step-card-header">
              <span className="step-number">04</span>
              <Icon name="heart" size={20} color="#20C7FF" />
            </div>
            <h3 className="step-heading">UNDERSTAND</h3>
            <p className="step-text">
              Get a simple food-risk result and recommendations before consuming the product.
            </p>
          </GlassCard>
        </div>
      </section>

      {/* ====================================================================
          Bottom Awareness CTA Banner (REFERENCE 02)
          ==================================================================== */}
      <section className="banner-section">
        <GlassCard variant="interactive" padding="large" className="cta-banner">
          <div className="banner-content">
            <div className="banner-badge">
              <span className="eyebrow-dot" />
              <span>MOBILE-FIRST PWA</span>
            </div>
            <h2 className="banner-title">Ready to decode your food packaging?</h2>
            <p className="banner-text">
              Try the interactive scanning demonstration and explore the full food risk analysis result.
            </p>
          </div>
          <Link to="/scan" className="banner-btn-link">
            <Button variant="primary" size="large">
              SCAN A FOOD PRODUCT NOW →
            </Button>
          </Link>
        </GlassCard>
      </section>
    </div>
  );
};

export default PublicHome;
