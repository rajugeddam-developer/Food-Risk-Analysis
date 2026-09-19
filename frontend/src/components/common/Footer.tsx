import React from 'react';
import { Link } from '../../router/Router';
import { Icon } from './Icon';

export const Footer: React.FC = () => {
  return (
    <footer className="app-footer">
      <div className="footer-container">
        {/* Top Disclaimer Banner */}
        <div className="footer-disclaimer-box">
          <div className="footer-disclaimer-header">
            <span className="disclaimer-badge">AWARENESS NOTICE</span>
            <span className="disclaimer-standards">WHO &amp; FSSAI REFERENCES</span>
          </div>
          <p className="footer-disclaimer-copy">
            <strong>Medical Disclaimer:</strong> This application is an educational awareness and information tool designed to help consumers understand packaged food labels. It is <strong>not a medical diagnosis</strong>, clinical advisory, or replacement for professional medical or nutritional advice. Dietary ratings are derived from publicly available WHO and FSSAI health guidelines.
          </p>
        </div>

        {/* Footer Navigation Columns */}
        <div className="footer-columns">
          {/* Brand Column */}
          <div className="footer-col-brand">
            <div className="footer-brand-title">
              <span className="footer-brand-badge">
                <Icon name="clock" size={16} color="#20C7FF" />
              </span>
              <span>Food Risk <span className="text-cyan">Analysis</span></span>
            </div>
            <p className="footer-tagline">
              Empowering conscious dietary choices through automated food label analysis.
            </p>
            <div className="footer-meta-pill">
              <span className="meta-dot" />
              <span>Milestone M1: 3D PWA UI</span>
            </div>

            {/* Social Icons */}
            <div className="footer-social-row">
              <a href="https://github.com" target="_blank" rel="noopener noreferrer" className="social-icon-btn" aria-label="GitHub">
                <Icon name="github" size={16} />
              </a>
              <a href="https://linkedin.com" target="_blank" rel="noopener noreferrer" className="social-icon-btn" aria-label="LinkedIn">
                <Icon name="linkedin" size={16} />
              </a>
              <a href="https://twitter.com" target="_blank" rel="noopener noreferrer" className="social-icon-btn" aria-label="Twitter">
                <Icon name="twitter" size={16} />
              </a>
              <a href="https://youtube.com" target="_blank" rel="noopener noreferrer" className="social-icon-btn" aria-label="YouTube">
                <Icon name="youtube" size={16} />
              </a>
            </div>
          </div>

          <div className="footer-col-links">
            <h5 className="footer-col-heading">Navigation</h5>
            <ul className="footer-link-list">
              <li><Link to="/">Home</Link></li>
              <li><Link to="/scan">Scan Food</Link></li>
              <li><Link to="/how-it-works">How It Works</Link></li>
              <li><Link to="/about">About &amp; Disclaimers</Link></li>
            </ul>
          </div>

          <div className="footer-col-links">
            <h5 className="footer-col-heading">Account</h5>
            <ul className="footer-link-list">
              <li><Link to="/login">Login</Link></li>
              <li><Link to="/register">Create Account</Link></li>
              <li><Link to="/forgot-password">Reset Password</Link></li>
            </ul>
          </div>

          <div className="footer-col-links">
            <h5 className="footer-col-heading">Guidelines</h5>
            <ul className="footer-link-list">
              <li><span className="footer-static-text">WHO Global Salt Target</span></li>
              <li><span className="footer-static-text">WHO Free Sugars Guideline</span></li>
              <li><span className="footer-static-text">FSSAI HFSS Framework</span></li>
              <li><span className="footer-static-text">NOVA Food Classification</span></li>
            </ul>
          </div>
        </div>

        {/* Bottom copyright line */}
        <div className="footer-bottom-bar">
          <p className="footer-copy-text">
            &copy; {new Date().getFullYear()} Food Risk Analysis PWA. Built with React 19 &amp; TypeScript.
          </p>
          <p className="footer-privacy-text">
            Privacy-First: Zero Permanent Food Scan Archiving.
          </p>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
