import React, { useState } from 'react';
import { Link, useRouter } from '../router/Router';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
import { Icon } from '../components/common/Icon';
import { registerUser } from '../api/authApi';
import './auth.css';

export const RegisterPage: React.FC = () => {
  const { navigate } = useRouter();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    // Frontend validations
    if (!name.trim()) {
      setError('Please provide your full name.');
      return;
    }
    if (!email.trim()) {
      setError('Please provide your email address.');
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email.trim())) {
      setError('Please enter a valid email address.');
      return;
    }
    if (!password) {
      setError('Please create a password.');
      return;
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match. Please re-check.');
      return;
    }

    setIsLoading(true);
    try {
      await registerUser({
        name: name.trim(),
        email: email.trim(),
        password
      });

      setSuccess('Account created successfully! Redirecting to login...');
      setTimeout(() => {
        navigate('/login');
      }, 1000);
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : 'Registration failed. Please try again.';
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-split-container">
        {/* Left Column: Visual Storytelling (REFERENCE 03) */}
        <div className="auth-storytelling-col">
          <div className="story-content">
            <span className="story-eyebrow">JOIN THE MOVEMENT</span>
            <h1 className="story-heading">
              Create Account <br />
              <span className="text-cyan">For a Healthier You</span>
            </h1>
            <p className="story-subtext">
              Join the food risk awareness platform to make informed nutrition decisions.
            </p>

            <div className="story-features-list">
              <div className="story-feature-item">
                <div className="story-icon-badge">
                  <Icon name="user" size={18} color="#20C7FF" />
                </div>
                <div>
                  <h4 className="feature-title">Be Informed</h4>
                  <p className="feature-desc">Get science-backed insights</p>
                </div>
              </div>

              <div className="story-feature-item">
                <div className="story-icon-badge">
                  <Icon name="heart" size={18} color="#20C7FF" />
                </div>
                <div>
                  <h4 className="feature-title">Make Better Choices</h4>
                  <p className="feature-desc">Understand what you eat</p>
                </div>
              </div>

              <div className="story-feature-item">
                <div className="story-icon-badge">
                  <Icon name="leaf" size={18} color="#20C7FF" />
                </div>
                <div>
                  <h4 className="feature-title">A Healthier Tomorrow</h4>
                  <p className="feature-desc">Starts with you</p>
                </div>
              </div>
            </div>
          </div>

          {/* Organic Floating Food Atmosphere (Fades into darkness, NO rectangular box) */}
          <div className="auth-organic-food-scene">
            <div className="auth-food-spotlight" aria-hidden="true" />
            <div className="auth-food-stage">
              <img
                src="/images/auth-spinach-tomatoes.webp"
                alt="Fresh dark studio vegetables in volumetric cyan lighting"
                className="auth-organic-food-img"
              />
              <div className="auth-food-tag">
                <span className="story-cursive-text">Good Food Brighter Lives</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Glass Authentication Panel (REFERENCE 03) */}
        <div className="auth-panel-col">
          <GlassCard variant="elevated" padding="large" className="auth-glass-card">
            {/* Centered Top Icon Badge */}
            <div className="auth-avatar-header">
              <div className="auth-avatar-badge">
                <Icon name="user-plus" size={24} color="#20C7FF" />
              </div>
              <h2 className="auth-card-title">CREATE ACCOUNT</h2>
              <p className="auth-card-subtitle">
                Start your journey towards safer, healthier food choices.
              </p>
            </div>

            {error && (
              <div className="auth-alert auth-alert--error" role="alert">
                <Icon name="alert-triangle" size={18} color="#FF5B61" />
                <span>{error}</span>
              </div>
            )}

            {success && (
              <div className="auth-alert auth-alert--success" role="status">
                <Icon name="check" size={18} color="#10C98B" />
                <span>{success}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="auth-form" noValidate>
              <div className="form-group">
                <label htmlFor="reg-name" className="form-label">
                  Full Name
                </label>
                <div className="glass-input-wrapper">
                  <span className="glass-input-icon-left">
                    <Icon name="user" size={18} />
                  </span>
                  <input
                    id="reg-name"
                    type="text"
                    className="glass-input glass-input--has-icon-left"
                    placeholder="Jane Doe"
                    value={name}
                    onChange={e => setName(e.target.value)}
                    autoComplete="name"
                    disabled={isLoading}
                  />
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="reg-email" className="form-label">
                  Email Address
                </label>
                <div className="glass-input-wrapper">
                  <span className="glass-input-icon-left">
                    <Icon name="mail" size={18} />
                  </span>
                  <input
                    id="reg-email"
                    type="email"
                    className="glass-input glass-input--has-icon-left"
                    placeholder="name@example.com"
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                    autoComplete="email"
                    disabled={isLoading}
                  />
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="reg-password" className="form-label">
                  Password
                </label>
                <div className="glass-input-wrapper">
                  <span className="glass-input-icon-left">
                    <Icon name="lock" size={18} />
                  </span>
                  <input
                    id="reg-password"
                    type={showPassword ? 'text' : 'password'}
                    className="glass-input glass-input--has-icon-left glass-input--has-action-right"
                    placeholder="Min 8 characters"
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    autoComplete="new-password"
                    disabled={isLoading}
                  />
                  <button
                    type="button"
                    className="glass-input-action-right"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    <Icon name={showPassword ? 'eye-off' : 'eye'} size={18} />
                  </button>
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="reg-confirm-password" className="form-label">
                  Confirm Password
                </label>
                <div className="glass-input-wrapper">
                  <span className="glass-input-icon-left">
                    <Icon name="lock" size={18} />
                  </span>
                  <input
                    id="reg-confirm-password"
                    type={showConfirmPassword ? 'text' : 'password'}
                    className="glass-input glass-input--has-icon-left glass-input--has-action-right"
                    placeholder="Confirm your password"
                    value={confirmPassword}
                    onChange={e => setConfirmPassword(e.target.value)}
                    autoComplete="new-password"
                    disabled={isLoading}
                  />
                  <button
                    type="button"
                    className="glass-input-action-right"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                  >
                    <Icon name={showConfirmPassword ? 'eye-off' : 'eye'} size={18} />
                  </button>
                </div>
              </div>

              <Button
                type="submit"
                variant="primary"
                size="large"
                fullWidth
                isLoading={isLoading}
                className="auth-submit-btn"
              >
                REGISTER →
              </Button>

              {/* Security Notice Card */}
              <div className="security-notice-card">
                <div className="security-icon-wrap">
                  <Icon name="lock" size={16} color="#20C7FF" />
                </div>
                <div className="security-text-group">
                  <span className="security-title">PostgreSQL Security</span>
                  <span className="security-desc">Password will be securely hashed with BCrypt and persisted in PostgreSQL.</span>
                </div>
              </div>

              <div className="auth-footer-prompt">
                <span>Already have an account? </span>
                <Link to="/login" className="form-link-cyan font-bold">
                  Login
                </Link>
              </div>
            </form>
          </GlassCard>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
