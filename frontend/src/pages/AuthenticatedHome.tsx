import React from 'react';
import { Link } from '../router/Router';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
import { RiskBadge } from '../components/common/RiskBadge';
import { Icon } from '../components/common/Icon';
import { UserProfile } from '../api/authApi';
import './auth-home.css';

interface AuthenticatedHomeProps {
  user: UserProfile | null;
}

export const AuthenticatedHome: React.FC<AuthenticatedHomeProps> = ({ user }) => {
  const displayName = user?.name || 'Consumer';

  return (
    <div className="auth-home">
      {/* 1. Personalized Header & Status Chips */}
      <header className="auth-home-header">
        <div className="auth-welcome-row">
          <div>
            <div className="auth-eyebrow">
              <span className="auth-eyebrow-dot" />
              <span>AUTHENTICATED CONSUMER WORKSPACE</span>
            </div>
            <h1 className="auth-welcome-title">
              Welcome back, <span className="auth-welcome-name">{displayName}</span>
            </h1>
            <p className="auth-welcome-sub">
              Your personal food safety cockpit &amp; deterministic packaging analysis center.
            </p>
          </div>

          <div className="auth-status-badges">
            <div className="auth-status-chip auth-status-chip--jwt">
              <Icon name="shield" size={14} color="#38D7FF" />
              <span>Stateless Session Active</span>
            </div>
            <div className="auth-status-chip auth-status-chip--privacy">
              <Icon name="lock" size={14} color="#10C98B" />
              <span>Zero Data Retention</span>
            </div>
          </div>
        </div>
      </header>

      {/* 2. Primary Cockpit Action Card */}
      <section className="auth-cockpit-card">
        <div className="auth-cockpit-grid">
          <div className="auth-cockpit-info">
            <div className="cockpit-tag">
              <Icon name="zap" size={16} color="#20C7FF" />
              <span>Ready for Extraction</span>
            </div>
            <h2 className="cockpit-title">
              Instant Multi-Surface Food Risk Scanner
            </h2>
            <p className="cockpit-desc">
              Capture or upload high-resolution packaging photos to decode chemical preservatives, 
              hidden sugars, excessive sodium, and industrial additives against WHO &amp; FSSAI health standards.
            </p>

            <div className="cockpit-actions">
              <Link to="/scan">
                <Button variant="primary" size="large" icon={<Icon name="camera" size={18} color="#20C7FF" />}>
                  LAUNCH FOOD SCANNER →
                </Button>
              </Link>
              <Link to="/how-it-works">
                <Button variant="outline" size="large" icon={<Icon name="info" size={18} />}>
                  VIEW OCR PIPELINE →
                </Button>
              </Link>
            </div>
          </div>

          {/* Quick Engine Status Panel */}
          <div className="cockpit-visual-panel">
            <div className="cockpit-status-row">
              <div className="cockpit-status-label">
                <Icon name="camera" size={15} color="#20C7FF" />
                <span>Ingredients Scanner (M5)</span>
              </div>
              <span className="cockpit-status-val status-active">ONLINE</span>
            </div>

            <div className="cockpit-status-row">
              <div className="cockpit-status-label">
                <Icon name="bar-chart" size={15} color="#20C7FF" />
                <span>Nutrition Facts Extractor (M6)</span>
              </div>
              <span className="cockpit-status-val status-active">ONLINE</span>
            </div>

            <div className="cockpit-status-row">
              <div className="cockpit-status-label">
                <Icon name="shield" size={15} color="#20C7FF" />
                <span>Deterministic Scoring (M10)</span>
              </div>
              <span className="cockpit-status-val status-ready">ARMED</span>
            </div>

            <div className="cockpit-status-row">
              <div className="cockpit-status-label">
                <Icon name="activity" size={15} color="#20C7FF" />
                <span>WHO / FSSAI Guideline Rules</span>
              </div>
              <span className="cockpit-status-val status-active">ACTIVE</span>
            </div>
          </div>
        </div>
      </section>

      {/* 3. Consumer Dietary Standards Matrix (3 Cards) */}
      <section className="auth-standards-section">
        <div className="section-title-wrap">
          <span className="section-eyebrow-text">SCIENCE-BACKED REFERENCE STANDARDS</span>
          <h2 className="section-main-heading">Dietary Risk Evaluation Guidelines</h2>
          <p className="section-subtext">
            Our engine measures packaged products against these empirical health thresholds.
          </p>
        </div>

        <div className="standards-cards-grid">
          {/* Card 1: Daily Nutritional Limits */}
          <GlassCard variant="elevated" padding="large" className="standard-card">
            <div className="standard-card-head">
              <div className="standard-icon-circle">
                <Icon name="heart" size={18} color="#20C7FF" />
              </div>
              <h3 className="standard-card-title">WHO Daily Limits (DRV)</h3>
            </div>
            <ul className="standard-list">
              <li>
                <strong>Sodium:</strong> Max 2,000 mg/day (~5g salt). Products with &gt;600mg/100g trigger high alerts.
              </li>
              <li>
                <strong>Free Sugars:</strong> Less than 10% of daily calories (&lt;50g/day, ideally &lt;25g).
              </li>
              <li>
                <strong>Saturated Fat:</strong> Max 10% of total caloric intake.
              </li>
              <li>
                <strong>Industrial Trans Fats:</strong> Strict 0% limit under WHO REPLACE guidelines.
              </li>
            </ul>
          </GlassCard>

          {/* Card 2: NOVA Classification */}
          <GlassCard variant="elevated" padding="large" className="standard-card">
            <div className="standard-card-head">
              <div className="standard-icon-circle">
                <Icon name="leaf" size={18} color="#10C98B" />
              </div>
              <h3 className="standard-card-title">NOVA Processing Index</h3>
            </div>
            <ul className="standard-list">
              <li>
                <strong>Group 1:</strong> Unprocessed or minimally processed whole agricultural foods.
              </li>
              <li>
                <strong>Group 2:</strong> Processed culinary ingredients (oils, natural sugars, salt).
              </li>
              <li>
                <strong>Group 3:</strong> Simple processed foods (canned vegetables, natural cheeses).
              </li>
              <li>
                <strong>Group 4 (UPF):</strong> Ultra-processed formulations with artificial emulsifiers and colors.
              </li>
            </ul>
          </GlassCard>

          {/* Card 3: Deterministic Risk Scale */}
          <GlassCard variant="elevated" padding="large" className="standard-card">
            <div className="standard-card-head">
              <div className="standard-icon-circle">
                <Icon name="scale" size={18} color="#F5A400" />
              </div>
              <h3 className="standard-card-title">Risk Scoring Bands</h3>
            </div>
            <ul className="standard-list">
              <li>
                <strong style={{ color: '#10C98B' }}>0 – 30 (Low Risk):</strong> Whole foods, minimal additives, low sodium. Safe for routine consumption.
              </li>
              <li>
                <strong style={{ color: '#F5A400' }}>31 – 60 (Moderate):</strong> Noticeable refined fats, moderate sugars, or mild preservatives. Consume in moderation.
              </li>
              <li>
                <strong style={{ color: '#FF5B61' }}>61 – 100 (High Concern):</strong> Heavy sodium load, trans-fats, or flagged artificial additives.
              </li>
            </ul>
          </GlassCard>
        </div>
      </section>

      {/* 4. Common Chemical Additives Watchlist */}
      <section className="additives-watchlist-section">
        <div className="section-title-wrap">
          <span className="section-eyebrow-text">LABEL INTELLIGENCE CHEATSHEET</span>
          <h2 className="section-main-heading">Common Additives Tracked by Scanner</h2>
          <p className="section-subtext">
            These E-numbers and INS chemical codes are automatically extracted from your uploaded packaging.
          </p>
        </div>

        <div className="additives-grid">
          <GlassCard variant="interactive" padding="medium" className="additive-card">
            <div className="additive-top-bar">
              <span className="additive-code">INS 102 / E102</span>
              <RiskBadge status="caution" label="CAUTION" size="small" />
            </div>
            <h4 className="additive-name">Tartrazine (Yellow 5)</h4>
            <p className="additive-desc">
              Synthetic azo dye used in confectionery and sodas. Linked to hyperactivity in children and hypersensitivity reactions.
            </p>
          </GlassCard>

          <GlassCard variant="interactive" padding="medium" className="additive-card">
            <div className="additive-top-bar">
              <span className="additive-code">INS 211 / E211</span>
              <RiskBadge status="avoid" label="WATCH" size="small" />
            </div>
            <h4 className="additive-name">Sodium Benzoate</h4>
            <p className="additive-desc">
              Common chemical preservative in acidic soft drinks. Can react with ascorbic acid (Vitamin C) to form trace benzene.
            </p>
          </GlassCard>

          <GlassCard variant="interactive" padding="medium" className="additive-card">
            <div className="additive-top-bar">
              <span className="additive-code">INS 621 / E621</span>
              <RiskBadge status="caution" label="SENSITIVITY" size="small" />
            </div>
            <h4 className="additive-name">Monosodium Glutamate (MSG)</h4>
            <p className="additive-desc">
              Flavor enhancer producing savory umami. Can cause headaches or palpitations in sensitive populations.
            </p>
          </GlassCard>

          <GlassCard variant="interactive" padding="medium" className="additive-card">
            <div className="additive-top-bar">
              <span className="additive-code">INS 951 / E951</span>
              <RiskBadge status="avoid" label="EVALUATED" size="small" />
            </div>
            <h4 className="additive-name">Aspartame</h4>
            <p className="additive-desc">
              Intense artificial sweetener evaluated by WHO/IARC as Group 2B. Contains phenylalanine warning for PKU patients.
            </p>
          </GlassCard>

          <GlassCard variant="interactive" padding="medium" className="additive-card">
            <div className="additive-top-bar">
              <span className="additive-code">INS 407 / E407</span>
              <RiskBadge status="caution" label="GUT ALERT" size="small" />
            </div>
            <h4 className="additive-name">Carrageenan</h4>
            <p className="additive-desc">
              Red seaweed extract used as emulsifier/stabilizer in dairy and plant milks. Correlated with gastrointestinal irritation.
            </p>
          </GlassCard>

          <GlassCard variant="interactive" padding="medium" className="additive-card">
            <div className="additive-top-bar">
              <span className="additive-code">INS 150d / E150d</span>
              <RiskBadge status="caution" label="COLORANT" size="small" />
            </div>
            <h4 className="additive-name">Sulphite Ammonia Caramel</h4>
            <p className="additive-desc">
              Widely used brown colorant in dark colas and sauces. Produced using ammonia, resulting in regulated 4-MEI compound.
            </p>
          </GlassCard>
        </div>
      </section>

      {/* 5. Bottom Navigation Banner */}
      <footer className="auth-bottom-banner">
        <div className="auth-bottom-content">
          <h3 className="auth-bottom-title">Have a packaged snack or meal ready?</h3>
          <p className="auth-bottom-sub">
            Snap two pictures with your smartphone camera to perform an instant, private risk assessment.
          </p>
        </div>
        <Link to="/scan">
          <Button variant="primary" size="large" icon={<Icon name="camera" size={18} color="#20C7FF" />}>
            SCAN PACKAGING NOW →
          </Button>
        </Link>
      </footer>
    </div>
  );
};

export default AuthenticatedHome;
