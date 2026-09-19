import React from 'react';
import { Link } from '../router/Router';
import { BrandOrbsScene } from '../components/brand-orbs/BrandOrbsScene';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
import { ScoreGauge } from '../components/common/ScoreGauge';
import { RiskBadge } from '../components/common/RiskBadge';
import './home.css';

export const HomePage: React.FC = () => {
  return (
    <div className="home-page">
      {/* Hero Section */}
      <section className="hero-section">
        {/* Brand Orbs Dimensional Background (Positioned behind content) */}
        <div className="hero-orb-backdrop" aria-hidden="true">
          <BrandOrbsScene variant="aura" isBackground />
        </div>

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
              <Button variant="primary" size="large" icon={<span aria-hidden="true">📸</span>}>
                SCAN YOUR FOOD
              </Button>
            </Link>

            <Link to="/how-it-works" className="cta-link-wrapper">
              <Button variant="outline" size="large" icon={<span aria-hidden="true">ℹ️</span>}>
                HOW IT WORKS
              </Button>
            </Link>
          </div>
        </div>

        {/* Hero Interactive Preview Card */}
        <div className="hero-preview-wrapper">
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
        </div>
      </section>

      {/* Food Safety Section (WHO Fact Sheet Highlights) */}
      <section className="food-safety-section" id="food-safety" aria-labelledby="food-safety-heading">
        <GlassCard variant="elevated" padding="large" className="food-safety-card">
          <div className="safety-header-container">
            <div className="safety-eyebrow-row">
              <span className="safety-eyebrow">
                <span className="safety-eyebrow-icon" aria-hidden="true">🌐</span>
                GLOBAL HEALTH CONTEXT &bull; WHO FACT SHEET
              </span>
              <a
                href="https://www.who.int/news-room/fact-sheets/detail/food-safety"
                target="_blank"
                rel="noopener noreferrer"
                className="safety-who-link"
              >
                <span>WHO Official Fact Sheet</span>
                <span aria-hidden="true">&nearr;</span>
              </a>
            </div>

            <h2 id="food-safety-heading" className="safety-main-title">
              Food safety
            </h2>

            <div className="safety-metadata-bar">
              <div className="safety-meta-item">
                <span className="meta-icon" aria-hidden="true">📅</span>
                <span className="meta-text">4 June 2026</span>
              </div>
              <span className="meta-divider" aria-hidden="true">&bull;</span>
              <div className="safety-meta-item">
                <span className="meta-icon" aria-hidden="true">⏱️</span>
                <span className="meta-text">Reading time: 7 min (1998 words)</span>
              </div>
            </div>

            {/* Language translations */}
            <div className="safety-languages-wrapper">
              <span className="languages-label">Read in other languages:</span>
              <div className="language-pills-list">
                <a
                  href="https://www.who.int/ar/news-room/fact-sheets/detail/food-safety"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="lang-pill"
                  dir="rtl"
                  lang="ar"
                  title="WHO Food Safety - العربية"
                >
                  العربية
                </a>
                <a
                  href="https://www.who.int/zh/news-room/fact-sheets/detail/food-safety"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="lang-pill"
                  lang="zh"
                  title="WHO Food Safety - 中文"
                >
                  中文
                </a>
                <a
                  href="https://www.who.int/fr/news-room/fact-sheets/detail/food-safety"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="lang-pill"
                  lang="fr"
                  title="WHO Food Safety - Français"
                >
                  Français
                </a>
                <a
                  href="https://www.who.int/ru/news-room/fact-sheets/detail/food-safety"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="lang-pill"
                  lang="ru"
                  title="WHO Food Safety - Русский"
                >
                  Русский
                </a>
                <a
                  href="https://www.who.int/es/news-room/fact-sheets/detail/food-safety"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="lang-pill"
                  lang="es"
                  title="WHO Food Safety - Español"
                >
                  Español
                </a>
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
              <span className="facts-count-badge">6 Key Pillars</span>
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
                  <span aria-hidden="true">🌱</span>
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
                  <span aria-hidden="true">⚠️</span>
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">GLOBAL CONTAMINATION TOLL</span>
                  <p className="fact-item-text">
                    An estimated 866 million – almost 1 in 9 people in the world – fall ill after eating contaminated food and 1.52 million die every year.
                  </p>
                </div>
              </div>

              <div className="fact-item-card">
                <div className="fact-icon-wrapper icon-amber">
                  <span aria-hidden="true">💵</span>
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
                  <span aria-hidden="true">👶</span>
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">CHILD VULNERABILITY</span>
                  <p className="fact-item-text">
                    Children under 5 years of age experience 29% of the health burden due to unsafe food, with 143 000 deaths in 2021.
                  </p>
                </div>
              </div>

              <div className="fact-item-card">
                <div className="fact-icon-wrapper icon-purple">
                  <span aria-hidden="true">🏥</span>
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">SOCIOECONOMIC DEVELOPMENT</span>
                  <p className="fact-item-text">
                    Foodborne diseases impede socioeconomic development by straining health-care systems and harming national economies, tourism, and trade.
                  </p>
                </div>
              </div>

              <div className="fact-item-card">
                <div className="fact-icon-wrapper icon-blue">
                  <span aria-hidden="true">🤝</span>
                </div>
                <div className="fact-item-body">
                  <span className="fact-item-category">SHARED RESPONSIBILITY</span>
                  <p className="fact-item-text">
                    Food safety is a shared responsibility among different national authorities and requires a multisectoral, one health approach.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </GlassCard>
      </section>

      {/* How It Works Section */}
      <section className="how-section">
        <div className="section-header-center">
          <span className="section-eyebrow">THE 4-STEP PIPELINE</span>
          <h2 className="section-title">How It Works</h2>
          <p className="section-subtitle">
            From photo capture to science-backed dietary awareness in seconds.
          </p>
        </div>

        <div className="steps-grid">
          <GlassCard variant="default" padding="medium" className="step-card">
            <span className="step-number">01</span>
            <h3 className="step-heading">CAPTURE</h3>
            <p className="step-text">
              Take photos of the food label using your phone camera or upload images from your gallery.
            </p>
          </GlassCard>

          <GlassCard variant="default" padding="medium" className="step-card">
            <span className="step-number">02</span>
            <h3 className="step-heading">EXTRACT</h3>
            <p className="step-text">
              The system reads the ingredients and nutrition information from both packaging surfaces.
            </p>
          </GlassCard>

          <GlassCard variant="default" padding="medium" className="step-card">
            <span className="step-number">03</span>
            <h3 className="step-heading">ANALYZE</h3>
            <p className="step-text">
              AI and our analysis engine evaluate chemical additives, NOVA levels, and WHO/FSSAI limits.
            </p>
          </GlassCard>

          <GlassCard variant="default" padding="medium" className="step-card">
            <span className="step-number">04</span>
            <h3 className="step-heading">UNDERSTAND</h3>
            <p className="step-text">
              Get a simple food-risk result and conscious eating guidance before consuming the product.
            </p>
          </GlassCard>
        </div>
      </section>

      {/* Bottom Awareness CTA Banner */}
      <section className="banner-section">
        <GlassCard variant="interactive" padding="large" className="cta-banner">
          <div className="banner-content">
            <span className="banner-badge">MOBILE-FIRST PWA</span>
            <h2 className="banner-title">Ready to decode your food packaging?</h2>
            <p className="banner-text">
              Try the interactive scanning demonstration and explore the full food risk analysis result.
            </p>
          </div>
          <Link to="/scan" className="banner-btn-link">
            <Button variant="primary" size="large">
              SCAN A FOOD PRODUCT NOW
            </Button>
          </Link>
        </GlassCard>
      </section>
    </div>
  );
};

export default HomePage;
