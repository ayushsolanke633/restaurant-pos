import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import useAuth from '../hooks/useAuth';
import { ROLES } from '../utils/constants';

export const ProtectedRoute = ({ allowedRoles }) => {
  const { isAuthenticated, role, loading, logout } = useAuth();

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <p>Restoring session...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(role)) {
    // Cross-role redirect enforcement
    if (role === ROLES.ADMIN) {
      return <Navigate to="/admin" replace />;
    } else if (role === ROLES.KITCHEN) {
      return <Navigate to="/kitchen" replace />;
    } else {
      logout();
      return <Navigate to="/login" replace />;
    }
  }

  return <Outlet />;
};

export default ProtectedRoute;
