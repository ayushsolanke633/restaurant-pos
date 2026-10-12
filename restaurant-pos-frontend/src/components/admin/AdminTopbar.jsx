import React from 'react';
import useAuth from '../../hooks/useAuth';

export const AdminTopbar = ({ onToggleSidebar }) => {
  const { user, logout } = useAuth();

  return (
    <header className="admin-topbar">
      <div className="topbar-left">
        <button
          className="btn-toggle-sidebar"
          onClick={onToggleSidebar}
          aria-label="Toggle navigation menu"
        >
          Menu
        </button>
        <h1 className="topbar-title">Dashboard</h1>
      </div>

      <div className="topbar-right">
        {user && (
          <div className="admin-profile">
            <div className="profile-details">
              <span className="profile-name">{user.name || user.email}</span>
              <span className="role-chip chip-admin">ADMIN</span>
            </div>
            <button
              onClick={logout}
              className="btn btn-logout"
              aria-label="Log out of session"
            >
              Logout
            </button>
          </div>
        )}
      </div>
    </header>
  );
};

export default AdminTopbar;
