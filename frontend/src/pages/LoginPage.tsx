import React, { useState } from 'react';
import { Link, useRouter } from '../router/Router';
import { GlassCard } from '../components/common/GlassCard';
import { Button } from '../components/common/Button';
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
      }, 700);
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : 'Invalid email or password.';
      setError(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        <GlassCard variant="elevated" padding="large" className="auth-card">
          <div className="auth-header">
            <span className="auth-badge">AUTHENTICATION UI</span>
            <h2 className="auth-title">WELCOME BACK</h2>
            <p className="auth-subtitle">
              Sign in to your account to review nutritional guidelines and scan food labels.
            </p>
          </div>

          {error && (
            <div className="auth-alert auth-alert--error" role="alert">
              <span className="alert-icon">⚠️</span>
              <span>{error}</span>
            </div>
          )}

          {success && (
            <div className="auth-alert auth-alert--success" role="status">
              <span className="alert-icon">✓</span>
              <span>{success}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="auth-form" noValidate>
            <div className="form-group">
              <label htmlFor="login-email" className="form-label">
                Email Address
              </label>
              <input
                id="login-email"
                type="email"
                className="glass-input"
                placeholder="name@example.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
                autoComplete="email"
                disabled={isLoading}
              />
            </div>

            <div className="form-group">
              <div className="form-label-row">
                <label htmlFor="login-password" className="form-label">
                  Password
                </label>
                <Link to="/forgot-password" className="form-link-subtle">
                  Forgot password?
                </Link>
              </div>

              <div className="password-input-wrap">
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  className="glass-input password-input"
                  placeholder="Enter your password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  autoComplete="current-password"
                  disabled={isLoading}
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? '👁️' : '👁️‍🗨️'}
                </button>
              </div>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="large"
              fullWidth
              isLoading={isLoading}
            >
              LOGIN
            </Button>

            <div className="auth-notice-box">
              <span>🔒 Stateless JWT Authentication: Passwords protected via BCrypt.</span>
            </div>

            <div className="auth-footer-prompt">
              <span>Don't have an account? </span>
              <Link to="/register" className="form-link-bold">
                Register
              </Link>
            </div>
          </form>
        </GlassCard>
      </div>
    </div>
  );
};

export default LoginPage;
