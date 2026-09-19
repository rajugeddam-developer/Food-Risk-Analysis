import React from 'react';
import { Link } from '../router/Router';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
import { Icon } from '../components/common/Icon';
import './info.css';

export const HowItWorksPage: React.FC = () => {
  return (
    <div className="info-page">
      <div className="info-container">
        <div className="info-header">
          <div className="info-badge">
            <span className="info-badge-dot" />
            <span>PIPELINE ARCHITECTURE</span>
          </div>
          <h1 className="info-title">
            How Food Risk Analysis <span className="text-gradient-cyan">Works</span>
          </h1>
          <p className="info-subtitle">
            A transparent overview of how our mobile PWA transforms complex packaging fine-print into clear dietary intelligence.
          </p>
        </div>

        {/* 4 Steps In-Depth */}
        <div className="info-steps-flow">
          {/* Step 1 */}
          <GlassCard variant="elevated" padding="large" className="info-step-card">
            <div className="step-card-num-badge">01</div>
            <div className="step-card-body">
              <span className="step-tag">INPUT STAGE</span>
              <h3 className="step-title">Capture &amp; Upload</h3>
              <p className="step-desc">
                Using your smartphone camera or photo gallery, you provide two focused snapshots of the food packaging:
              </p>
              <ul className="step-subpoints">
                <li><strong>Ingredients Statement:</strong> The continuous text block declaring whole agricultural foods, oils, chemical numbers (E-numbers / INS), and allergens.</li>
                <li><strong>Nutrition Facts Table:</strong> The tabular breakdown listing caloric density, total fat, saturated fat, sodium, and carbohydrates per 100g.</li>
              </ul>
            </div>
          </GlassCard>

          {/* Step 2 */}
          <GlassCard variant="elevated" padding="large" className="info-step-card">
            <div className="step-card-num-badge">02</div>
            <div className="step-card-body">
              <span className="step-tag">DIGITIZATION STAGE (M5)</span>
              <h3 className="step-title">Optical Character Recognition (OCR)</h3>
              <p className="step-desc">
                An advanced optical character recognition pipeline processes the high-resolution images:
              </p>
              <ul className="step-subpoints">
                <li>Extracts raw tabular key-value pairs from nutritional columns.</li>
                <li>Transcribes comma-separated ingredient phrases, percentages, and sub-ingredient parentheses.</li>
                <li>Handles low-light and slight camera tilt automatically.</li>
              </ul>
            </div>
          </GlassCard>

          {/* Step 3 */}
          <GlassCard variant="elevated" padding="large" className="info-step-card">
            <div className="step-card-num-badge">03</div>
            <div className="step-card-body">
              <span className="step-tag">INTELLIGENCE STAGE (M6–M10)</span>
              <h3 className="step-title">Gemini AI &amp; Risk Engines</h3>
              <p className="step-desc">
                The extracted text undergoes multi-layered algorithmic and semantic analysis:
              </p>
              <ul className="step-subpoints">
                <li><strong>Gemini AI Normalization:</strong> Resolves OCR spelling ambiguities and maps industrial codes (e.g., <em>INS 621</em> becomes <em>Monosodium Glutamate</em>).</li>
                <li><strong>Food Classification:</strong> Confirms the product is human consumable, flagging animal feed or non-edible chemicals.</li>
                <li><strong>NOVA Industrial Scoring:</strong> Detects ultra-processed food (UPF) markers, cosmetic additives, and emulsifiers.</li>
                <li><strong>WHO &amp; FSSAI Rule Verification:</strong> Benchmarks sodium, added sugars, and saturated fats against daily maximum limits.</li>
              </ul>
            </div>
          </GlassCard>

          {/* Step 4 */}
          <GlassCard variant="elevated" padding="large" className="info-step-card">
            <div className="step-card-num-badge">04</div>
            <div className="step-card-body">
              <span className="step-tag">SYNTHESIS &amp; GUIDANCE</span>
              <h3 className="step-title">Food Risk Score &amp; Awareness</h3>
              <p className="step-desc">
                All evaluation vectors synthesize into an intuitive consumer awareness report:
              </p>
              <ul className="step-subpoints">
                <li><strong>Overall Score (0–100):</strong> Transparent rating from Low Risk to Critical Concern.</li>
                <li><strong>Traffic Light Indicators:</strong> Color-coded alerts for individual ingredients and nutrient thresholds.</li>
                <li><strong>Actionable Guidance:</strong> Consumption frequency suggestions tailored to inform, not prescribe.</li>
              </ul>
            </div>
          </GlassCard>
        </div>

        {/* Data Privacy Pillar Callout */}
        <GlassCard variant="elevated" padding="large" className="privacy-callout-card">
          <div className="privacy-callout-header">
            <div className="privacy-shield-wrap">
              <Icon name="shield" size={24} color="#20c9ff" />
            </div>
            <div>
              <h3 className="privacy-callout-title">Privacy-By-Design: Ephemeral Food Processing</h3>
              <p className="privacy-callout-sub">
                Your diet is your personal business. Our architecture reflects that principle.
              </p>
            </div>
          </div>
          <p className="privacy-callout-text">
            Individual packaging images and scan results are <strong>never permanently archived in our database</strong>. Photos reside in short-lived memory only during the active OCR extraction pass, after which temporary processing artifacts expire immediately. Only essential account credentials (name, email, secure password hash) are permanently stored.
          </p>
        </GlassCard>

        {/* CTA */}
        <div className="info-bottom-cta">
          <Link to="/scan" className="info-cta-link">
            <Button variant="primary" size="large" icon={<Icon name="zap" size={18} />}>
              TRY SCANNING A PRODUCT NOW →
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default HowItWorksPage;
