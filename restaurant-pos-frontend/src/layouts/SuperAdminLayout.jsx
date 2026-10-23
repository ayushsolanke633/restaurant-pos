import React from 'react';
import { Outlet, NavLink } from 'react-router-dom';
import useAuth from '../hooks/useAuth';

export const SuperAdminLayout = () => {
  const { user, logout } = useAuth();

  return (
    <div className="layout-container super-admin-layout">
      <header className="app-header platform-header">
        <div className="header-brand">
          <span className="brand-title">AnnaDhara</span>
          <span className="platform-tag">PLATFORM SUPER ADMIN</span>
        </div>

        <nav className="header-nav">
          <NavLink
            to="/super-admin/restaurants"
            className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
          >
            Restaurants & Tenants
          </NavLink>
        </nav>

        <div className="header-right">
          <div className="user-profile">
            <div className="user-info">
              <span className="user-name">{user?.name || user?.email || 'Platform Admin'}</span>
              <span className="role-chip chip-super-admin">SUPER_ADMIN</span>
            </div>
            <button onClick={logout} className="btn btn-logout" title="Log out of platform portal">
              Logout
            </button>
          </div>
        </div>
      </header>

      <main className="main-content platform-main">
        <Outlet />
      </main>

      <footer className="app-footer">
        <div className="footer-content">
          <span>Platform Administration Portal • Multi-Tenant Isolated Environment</span>
        </div>
      </footer>
    </div>
  );
};

export default SuperAdminLayout;
