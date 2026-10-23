import React, { createContext, useState, useEffect, useCallback } from 'react';
import authService from '../services/authService';
import { SUPPORTED_ROLES } from '../utils/constants';

export const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [role, setRole] = useState(null);
  const [loading, setLoading] = useState(true);

  // Logout handler
  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      setUser(null);
      setToken(null);
      setRole(null);
    }
  }, []);

  // Restore session from localStorage on initial render
  useEffect(() => {
    const initAuth = () => {
      if (authService.isAuthenticated()) {
        const currentUser = authService.getCurrentUser();
        const currentToken = authService.getToken();
        
        if (currentUser && SUPPORTED_ROLES.includes(currentUser.role)) {
          setUser(currentUser);
          setToken(currentToken);
          setRole(currentUser.role);
        } else {
          // If stored role is unsupported in React app (e.g. WAITER)
          logout();
        }
      } else {
        logout();
      }
      setLoading(false);
    };

    initAuth();

    // Listen for global 401 unauthorized event from Axios interceptor
    const handleUnauthorized = () => {
      logout();
    };

    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => {
      window.removeEventListener('auth:unauthorized', handleUnauthorized);
    };
  }, [logout]);

  // Login handler
  const login = async (email, password) => {
    const data = await authService.login(email, password);

    if (!data || !data.role) {
      throw new Error('Invalid response from server.');
    }

    // Validate supported roles (ADMIN or KITCHEN only)
    if (!SUPPORTED_ROLES.includes(data.role)) {
      throw new Error('This web application is only for Platform Super Admin, Restaurant Admin, and Kitchen users.');
    }

    authService.saveSession(data);

    const currentUser = authService.getCurrentUser();
    setUser(currentUser);
    setToken(data.token);
    setRole(data.role);

    return data;
  };

  const value = {
    user,
    token,
    role,
    isAuthenticated: !!user && !!token,
    loading,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export default AuthContext;
