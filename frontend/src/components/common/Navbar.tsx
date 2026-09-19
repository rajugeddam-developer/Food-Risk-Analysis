import React, { useState, useEffect } from 'react';
import { useRouter, Link } from '../../router/Router';
import { useAuth } from '../../context/AuthContext';
import { Icon } from './Icon';
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
      setIsScrolled(window.scrollY > 15);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <header className={`navbar-header ${isScrolled ? 'navbar--scrolled' : ''}`}>
      <div className="navbar-container">
        {/* Brand Logo & Name */}
        <Link to={isAuthenticated ? '/scan' : '/'} className="navbar-brand" aria-label="Food Risk Analysis">
          <span className="navbar-brand-badge">
            <Icon name="clock" size={17} color="#20C7FF" />
          </span>
          <span className="navbar-brand-text">
            Food Risk <span className="text-cyan">Analysis</span>
          </span>
        </Link>

        {/* Desktop Navigation Links (Auth-Aware) */}
        <nav className="navbar-desktop-nav" aria-label="Main Navigation">
          {isAuthenticated ? (
            /* Mode B: Authenticated Application Navigation */
            <>
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
            </>
          ) : (
            /* Mode A: Public Gateway Navigation */
            <>
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
            </>
          )}
        </nav>

        {/* Desktop Action Buttons (Auth-Aware) */}
        <div className="navbar-desktop-actions">
          {isAuthenticated ? (
            <>
              <div className="navbar-user-chip">
                <Icon name="user" size={15} color="#20C7FF" />
                <span className="user-chip-name">{user?.name || 'User'}</span>
              </div>
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
                className="btn-nav-register"
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

      {/* Mobile Drawer Menu (Auth-Aware) */}
      {isMobileMenuOpen && (
        <div className="navbar-mobile-drawer" role="dialog" aria-modal="true">
          <div className="mobile-drawer-links">
            {isAuthenticated ? (
              /* Mode B Authenticated Drawer */
              <>
                <Link
                  to="/"
                  className={`mobile-nav-link ${currentPath === '/' ? 'mobile-nav-link--active' : ''}`}
                >
                  <Icon name="clock" size={18} />
                  <span>Home</span>
                </Link>
                <Link
                  to="/how-it-works"
                  className={`mobile-nav-link ${currentPath === '/how-it-works' ? 'mobile-nav-link--active' : ''}`}
                >
                  <Icon name="info" size={18} />
                  <span>How It Works</span>
                </Link>
              </>
            ) : (
              /* Mode A Public Drawer */
              <>
                <Link
                  to="/"
                  className={`mobile-nav-link ${currentPath === '/' ? 'mobile-nav-link--active' : ''}`}
                >
                  <Icon name="clock" size={18} />
                  <span>Home</span>
                </Link>
                <Link
                  to="/how-it-works"
                  className={`mobile-nav-link ${currentPath === '/how-it-works' ? 'mobile-nav-link--active' : ''}`}
                >
                  <Icon name="info" size={18} />
                  <span>How It Works</span>
                </Link>
                <Link
                  to="/about"
                  className={`mobile-nav-link ${currentPath === '/about' ? 'mobile-nav-link--active' : ''}`}
                >
                  <Icon name="shield" size={18} />
                  <span>About</span>
                </Link>
              </>
            )}
          </div>

          <div className="mobile-drawer-actions">
            {isAuthenticated ? (
              <>
                <div className="mobile-user-status">
                  <Icon name="user" size={16} color="#20C7FF" />
                  <span>Signed in as <strong>{user?.name || 'User'}</strong></span>
                </div>
                <button
                  type="button"
                  onClick={handleLogout}
                  className="mobile-btn-ghost"
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
                  Register
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
