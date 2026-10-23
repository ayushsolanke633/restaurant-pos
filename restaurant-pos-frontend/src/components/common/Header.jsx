import React from 'react';
import { NavLink } from 'react-router-dom';
import useAuth from '../../hooks/useAuth';

export const Header = () => {
  const { user, isAuthenticated, logout } = useAuth();

  return (
    <header className="app-header">
      <div className="header-brand">
        <span className="brand-title">AnnaDhara</span>
      </div>

      <div className="header-right">
        {isAuthenticated && user ? (
          <div className="user-profile">
            <div className="user-info">
              <span className="user-name">{user.name || user.email}</span>
              <span className={`role-chip chip-${user.role.toLowerCase()}`}>
                {user.role}
              </span>
            </div>
            <button onClick={logout} className="btn btn-logout">
              Logout
            </button>
          </div>
        ) : (
          <nav className="header-nav">
            <NavLink
              to="/login"
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            >
              Login
            </NavLink>
          </nav>
        )}
      </div>
    </header>
  );
};

export default Header;
