import React, { useState } from 'react';
import { Link, useRouter } from '../router/Router';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
import { Icon } from '../components/common/Icon';
import { loginUser } from '../api/authApi';
import { useAuth } from '../context/AuthContext';
import './auth.css';

export const LoginPage: React.FC = () => {
  const { navigate } = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    // Basic frontend validations
    if (!email.trim()) {
      setError('Please enter your email address.');
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email.trim())) {
      setError('Please enter a valid email address format.');
      return;
    }
    if (!password) {
      setError('Please enter your password.');
      return;
    }

    setIsLoading(true);
    try {
      const authResponse = await loginUser({
        email: email.trim(),
        password
      });

      login(authResponse);
      setSuccess('Logged in successfully! Redirecting to food scanner...');
      setTimeout(() => {
        navigate('/scan');
      }, 600);
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : 'Invalid email or password.';
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
            <span className="story-eyebrow">SMART FOOD CHOICES</span>
            <h1 className="story-heading">
              Safer Food <br />
              <span className="text-cyan">Healthier You</span>
            </h1>
            <p className="story-subtext">
              Sign in to continue your journey towards safer, healthier food choices.
            </p>

            <div className="story-features-list">
              <div className="story-feature-item">
                <div className="story-icon-badge">
                  <Icon name="shield" size={18} color="#20C7FF" />
                </div>
                <div>
                  <h4 className="feature-title">Evidence Based</h4>
                  <p className="feature-desc">Backed by WHO &amp; FSSAI guidelines</p>
                </div>
              </div>

              <div className="story-feature-item">
                <div className="story-icon-badge">
                  <Icon name="bar-chart" size={18} color="#20C7FF" />
                </div>
                <div>
                  <h4 className="feature-title">Instant Analysis</h4>
                  <p className="feature-desc">Know the risk in seconds</p>
                </div>
              </div>

              <div className="story-feature-item">
                <div className="story-icon-badge">
                  <Icon name="leaf" size={18} color="#20C7FF" />
                </div>
                <div>
                  <h4 className="feature-title">Healthier Tomorrow</h4>
                  <p className="feature-desc">Make informed food choices</p>
                </div>
              </div>
            </div>
          </div>

          {/* Organic Floating Food Atmosphere (Fades into darkness, NO rectangular box) */}
          <div className="auth-organic-food-scene">
            <div className="auth-food-spotlight" aria-hidden="true" />
            <div className="auth-food-stage">
              <img
                src="/images/auth-berries-bowl.webp"
                alt="Dark studio bowl with fresh blueberries and kiwi in volumetric atmospheric lighting"
                className="auth-organic-food-img"
              />
              <div className="auth-food-tag">
                <span className="story-cursive-text">Better Food Brighter Future</span>
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
                <Icon name="clock" size={24} color="#20C7FF" />
              </div>
              <h2 className="auth-card-title">WELCOME BACK</h2>
              <p className="auth-card-subtitle">
                Sign in to your account to review nutritional guidelines and scan food labels.
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
                <label htmlFor="login-email" className="form-label">
                  Email Address
                </label>
                <div className="glass-input-wrapper">
                  <span className="glass-input-icon-left">
                    <Icon name="mail" size={18} />
                  </span>
                  <input
                    id="login-email"
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
                <div className="form-label-row">
                  <label htmlFor="login-password" className="form-label">
                    Password
                  </label>
                  <Link to="/forgot-password" className="form-link-cyan">
                    Forgot password?
                  </Link>
                </div>
                <div className="glass-input-wrapper">
                  <span className="glass-input-icon-left">
                    <Icon name="lock" size={18} />
                  </span>
                  <input
                    id="login-password"
                    type={showPassword ? 'text' : 'password'}
                    className="glass-input glass-input--has-icon-left glass-input--has-action-right"
                    placeholder="••••••••"
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    autoComplete="current-password"
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

              <Button
                type="submit"
                variant="primary"
                size="large"
                fullWidth
                isLoading={isLoading}
                className="auth-submit-btn"
              >
                LOGIN →
              </Button>

              {/* Social Login Divider */}
              <div className="auth-divider">
                <span className="auth-divider-line" />
                <span className="auth-divider-text">OR CONTINUE WITH</span>
                <span className="auth-divider-line" />
              </div>

              <div className="social-auth-row">
                <button type="button" className="social-login-btn" aria-label="Sign in with Google">
                  <Icon name="google" size={18} />
                </button>
                <button type="button" className="social-login-btn" aria-label="Sign in with GitHub">
                  <Icon name="github" size={18} />
                </button>
                <button type="button" className="social-login-btn" aria-label="Sign in with Microsoft">
                  <Icon name="microsoft" size={18} />
                </button>
              </div>

              {/* Security Badge Card */}
              <div className="security-notice-card">
                <div className="security-icon-wrap">
                  <Icon name="lock" size={16} color="#10C98B" />
                </div>
                <div className="security-text-group">
                  <span className="security-title">Stateless JWT Authentication</span>
                  <span className="security-desc">Passwords are securely protected via BCrypt.</span>
                </div>
              </div>

              <div className="auth-footer-prompt">
                <span>Don't have an account? </span>
                <Link to="/register" className="form-link-cyan font-bold">
                  Register
                </Link>
              </div>
            </form>
          </GlassCard>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
