import React from 'react';
import { Link } from '../router/Router';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
import './info.css';

export const AboutPage: React.FC = () => {
  return (
    <div className="info-page">
      <div className="info-container">
        <div className="info-header">
          <span className="info-badge">ABOUT OUR MISSION</span>
          <h1 className="info-title">Empowering Food Awareness</h1>
          <p className="info-subtitle">
            Demystifying chemical additives, deceptive portion sizes, and hidden sugars in everyday packaged food.
          </p>
        </div>

        {/* Mission Statement */}
        <GlassCard variant="elevated" padding="large" className="about-hero-card">
          <h2 className="about-hero-title">Know What You Eat.</h2>
          <p className="about-hero-copy">
            The modern grocery aisle is flooded with ultra-processed foods disguised behind health claims. Synthetic emulsifiers that disrupt gut microbiota, excessive sodium masked by artificial flavor enhancers (like MSG/E621), and trans fats hidden in hydrogenated fractions are commonplace.
          </p>
          <p className="about-hero-copy">
            The <strong>Food Risk Analysis PWA</strong> puts objective, science-grounded intelligence directly into the hands of consumers right at the point of purchase.
          </p>
        </GlassCard>

        {/* Pillars Grid */}
        <div className="about-pillars-grid">
          <GlassCard variant="default" padding="medium" className="about-pillar-card">
            <span className="pillar-icon" aria-hidden="true">🔬</span>
            <h3 className="pillar-title">Scientific Standards</h3>
            <p className="pillar-text">
              Our evaluation models reference public dietary thresholds established by the <strong>World Health Organization (WHO)</strong> and the <strong>Food Safety and Standards Authority of India (FSSAI)</strong>, alongside the NOVA Ultra-Processed Food classification framework.
            </p>
          </GlassCard>

          <GlassCard variant="default" padding="medium" className="about-pillar-card">
            <span className="pillar-icon" aria-hidden="true">🔒</span>
            <h3 className="pillar-title">Zero Data Exploitation</h3>
            <p className="pillar-text">
              We do not track your grocery habits or sell dietary profiles to advertisers. Food images and nutritional analyses are ephemeral—processed in volatile memory and never converted into permanent consumer dossiers.
            </p>
          </GlassCard>

          <GlassCard variant="default" padding="medium" className="about-pillar-card">
            <span className="pillar-icon" aria-hidden="true">📱</span>
            <h3 className="pillar-title">Mobile-First PWA</h3>
            <p className="pillar-text">
              Engineered as a lightweight Progressive Web App that works seamlessly in mobile browsers or as an installed app on iOS and Android without demanding unnecessary device permissions.
            </p>
          </GlassCard>
        </div>

        {/* Mandatory Non-Medical Disclaimer */}
        <GlassCard variant="subtle" padding="large" className="about-disclaimer-card">
          <div className="disclaimer-header-row">
            <span className="disclaimer-icon" aria-hidden="true">⚖️</span>
            <div>
              <h3 className="disclaimer-heading">MANDATORY MEDICAL &amp; NUTRITIONAL DISCLAIMER</h3>
              <p className="disclaimer-copy">
                The Food Risk Analysis PWA is strictly an educational consumer awareness system. It is <strong>not a medical diagnosis, clinical advisory, or personalized nutrition prescription</strong>.
              </p>
              <p className="disclaimer-copy">
                The system does not diagnose allergies, celiac disease, metabolic conditions, or specific medical intolerances. Consumers with medical conditions, pregnancy, or severe dietary restrictions must always consult qualified medical practitioners or registered dietitians before making dietary changes.
              </p>
            </div>
          </div>
        </GlassCard>

        {/* Action button */}
        <div className="info-bottom-cta">
          <Link to="/scan" className="info-cta-link">
            <Button variant="primary" size="large">
              EXPERIENCE THE SCANNER
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default AboutPage;
