import React, { useState, useEffect } from 'react';
import { useRouter, Link } from '../../router/Router';
import { useAuth } from '../../context/AuthContext';
import './navbar.css';

export const Navbar: React.FC = () => {
  const { currentPath, navigate } = useRouter();
  const { isAuthenticated, user, logout } = useAuth();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);

  const handleLogout = () => {
    logout();
    setIsMobileMenuOpen(false);
    navigate('/login');
  };

  // Close mobile drawer on route change
  useEffect(() => {
    setIsMobileMenuOpen(false);
  }, [currentPath]);

  // Track scroll for enhanced glass backdrop
  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <header className={`navbar-header ${isScrolled ? 'navbar--scrolled' : ''}`}>
      <div className="navbar-container">
        {/* Brand Logo & Name */}
        <Link to="/" className="navbar-brand" aria-label="Food Risk Analysis Home">
          <span className="navbar-brand-badge">
            <svg viewBox="0 0 24 24" className="navbar-brand-icon" aria-hidden="true">
              <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" fill="none" />
              <path d="M12 6v6l4 2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </span>
          <span className="navbar-brand-text">
            Food Risk <span className="text-highlight">Analysis</span>
          </span>
        </Link>

        {/* Desktop Navigation Links */}
        <nav className="navbar-desktop-nav" aria-label="Main Navigation">
          <Link
            to="/"
            className={`nav-link ${currentPath === '/' ? 'nav-link--active' : ''}`}
          >
            Home
          </Link>
          <Link
            to="/how-it-works"
            className={`nav-link ${currentPath === '/how-it-works' ? 'nav-link--active' : ''}`}
          >
            How It Works
          </Link>
          <Link
            to="/about"
            className={`nav-link ${currentPath === '/about' ? 'nav-link--active' : ''}`}
          >
            About
          </Link>
        </nav>

        {/* Desktop Action Buttons */}
        <div className="navbar-desktop-actions">
          {isAuthenticated ? (
            <>
              <span style={{ color: 'var(--color-text-secondary, #94a3b8)', fontSize: '0.875rem', fontWeight: 500, marginRight: '0.75rem' }}>
                👤 {user?.name || 'User'}
              </span>
              <button
                type="button"
                onClick={handleLogout}
                className="btn-nav-ghost"
              >
                Logout
              </button>
            </>
          ) : (
            <>
              <Link
                to="/login"
                className={`btn-nav-ghost ${currentPath === '/login' ? 'btn-nav-ghost--active' : ''}`}
              >
                Login
              </Link>
              <Link
                to="/register"
                className="btn-nav-primary"
              >
                Register
              </Link>
            </>
          )}
        </div>

        {/* Mobile Hamburger Toggle Button */}
        <button
          type="button"
          className="navbar-hamburger"
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          aria-label={isMobileMenuOpen ? 'Close Menu' : 'Open Menu'}
          aria-expanded={isMobileMenuOpen}
        >
          <span className={`hamburger-bar ${isMobileMenuOpen ? 'hamburger-bar--open-1' : ''}`} />
          <span className={`hamburger-bar ${isMobileMenuOpen ? 'hamburger-bar--open-2' : ''}`} />
          <span className={`hamburger-bar ${isMobileMenuOpen ? 'hamburger-bar--open-3' : ''}`} />
        </button>
      </div>

      {/* Mobile Drawer Menu */}
      {isMobileMenuOpen && (
        <div className="navbar-mobile-drawer" role="dialog" aria-modal="true">
          <div className="mobile-drawer-links">
            <Link
              to="/"
              className={`mobile-nav-link ${currentPath === '/' ? 'mobile-nav-link--active' : ''}`}
            >
              <span className="mobile-nav-icon">🏠</span>
              <span>Home</span>
            </Link>
            <Link
              to="/scan"
              className={`mobile-nav-link ${currentPath === '/scan' ? 'mobile-nav-link--active' : ''}`}
            >
              <span className="mobile-nav-icon">📷</span>
              <span>Scan Your Food</span>
            </Link>
            <Link
              to="/how-it-works"
              className={`mobile-nav-link ${currentPath === '/how-it-works' ? 'mobile-nav-link--active' : ''}`}
            >
              <span className="mobile-nav-icon">ℹ️</span>
              <span>How It Works</span>
            </Link>
            <Link
              to="/about"
              className={`mobile-nav-link ${currentPath === '/about' ? 'mobile-nav-link--active' : ''}`}
            >
              <span className="mobile-nav-icon">🛡️</span>
              <span>About &amp; Disclaimers</span>
            </Link>
          </div>

          <div className="mobile-drawer-actions">
            {isAuthenticated ? (
              <>
                <div style={{ textAlign: 'center', marginBottom: '0.75rem', color: '#94a3b8', fontSize: '0.9rem' }}>
                  Signed in as <strong>{user?.name}</strong>
                </div>
                <button
                  type="button"
                  onClick={handleLogout}
                  className="mobile-btn-ghost"
                  style={{ width: '100%', textAlign: 'center' }}
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="mobile-btn-ghost">
                  Login
                </Link>
                <Link to="/register" className="mobile-btn-primary">
                  Create Account
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  );
};

export default Navbar;
