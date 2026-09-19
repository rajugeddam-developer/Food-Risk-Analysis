import React, { useState } from 'react';
import { Link } from '../router/Router';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
import './auth.css';

export const ForgotPasswordPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email.trim()) {
      setError('Please enter your account email.');
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email.trim())) {
      setError('Please enter a valid email format.');
      return;
    }

    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);
      setIsSubmitted(true);
    }, 750);
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        <GlassCard variant="elevated" padding="large" className="auth-card">
          <div className="auth-header">
            <span className="auth-badge">PASSWORD RECOVERY</span>
            <h2 className="auth-title">RESET PASSWORD</h2>
            <p className="auth-subtitle">
              Enter your registered email address and we'll send you instructions to reset your password.
            </p>
          </div>

          {error && (
            <div className="auth-alert auth-alert--error" role="alert">
              <span className="alert-icon">⚠️</span>
              <span>{error}</span>
            </div>
          )}

          {isSubmitted ? (
            <div className="auth-success-card">
              <div className="success-icon-wrap" aria-hidden="true">
                ℹ️
              </div>
              <h3 className="success-title">Feature Notice</h3>
              <p className="success-desc">
                Automated password reset via email is scheduled for a future update. For local development or evaluation, please register a new account or sign in with existing credentials.
              </p>
              <div className="auth-notice-box">
                <span>Milestone M3 Scope: Real email delivery & SMTP are deferred to future milestones.</span>
              </div>
              <Link to="/login" className="btn-success-return">
                <Button variant="outline" size="medium" fullWidth>
                  Return to Login
                </Button>
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="auth-form" noValidate>
              <div className="form-group">
                <label htmlFor="reset-email" className="form-label">
                  Email Address
                </label>
                <input
                  id="reset-email"
                  type="email"
                  className="glass-input"
                  placeholder="name@example.com"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  autoComplete="email"
                  disabled={isLoading}
                />
              </div>

              <Button
                type="submit"
                variant="primary"
                size="large"
                fullWidth
                isLoading={isLoading}
              >
                SEND RESET LINK
              </Button>

              <div className="auth-footer-prompt">
                <Link to="/login" className="form-link-subtle">
                  ← Back to Login
                </Link>
              </div>
            </form>
          )}
        </GlassCard>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
