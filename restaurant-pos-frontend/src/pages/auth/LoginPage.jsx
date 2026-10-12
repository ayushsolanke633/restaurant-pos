import React, { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import useAuth from '../../hooks/useAuth';
import { ROLES } from '../../utils/constants';

export const LoginPage = () => {
  const { login, isAuthenticated, role, loading: authLoading } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // If already logged in, redirect to respective role dashboard
  if (!authLoading && isAuthenticated) {
    if (role === ROLES.SUPER_ADMIN) {
      return <Navigate to="/super-admin" replace />;
    }
    if (role === ROLES.ADMIN) {
      return <Navigate to="/admin" replace />;
    }
    if (role === ROLES.KITCHEN) {
      return <Navigate to="/kitchen" replace />;
    }
  }

  const validateForm = () => {
    const errors = {};
    const emailRegex = /\S+@\S+\.\S+/;

    if (!email.trim()) {
      errors.email = 'Email address is required.';
    } else if (!emailRegex.test(email.trim())) {
      errors.email = 'Please enter a valid email address.';
    }

    if (!password) {
      errors.password = 'Password is required.';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError('');

    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);

    try {
      const responseData = await login(email.trim(), password);

      // Redirect based on role returned
      if (responseData.role === ROLES.SUPER_ADMIN) {
        navigate('/super-admin', { replace: true });
      } else if (responseData.role === ROLES.ADMIN) {
        navigate('/admin', { replace: true });
      } else if (responseData.role === ROLES.KITCHEN) {
        navigate('/kitchen', { replace: true });
      } else {
        setServerError('This web application is only for Platform Super Admin, Restaurant Admin, and Kitchen users.');
      }
    } catch (err) {
      if (err.message && err.message.includes('only for Admin and Kitchen')) {
        setServerError(err.message);
      } else if (err.response) {
        if (err.response.status === 401) {
          setServerError('Invalid email or password.');
        } else {
          setServerError('Something went wrong. Please try again.');
        }
      } else if (err.request || err.code === 'ERR_NETWORK') {
        setServerError('Unable to connect to the server. Please try again.');
      } else {
        setServerError(err.message || 'Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page-container auth-page">
      <div className="card auth-card">
        <div className="auth-header">
          <h1 className="app-title">Restaurant POS</h1>
          <h2 className="section-title">Login</h2>
          <p className="subtitle">Sign in with your Admin or Kitchen account</p>
        </div>

        {serverError && (
          <div className="alert alert-danger" role="alert">
            <span>{serverError}</span>
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label htmlFor="email">Email Address</label>
            <input
              type="email"
              id="email"
              placeholder="e.g. admin@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isSubmitting}
              className={`form-input ${fieldErrors.email ? 'input-error' : ''}`}
              autoComplete="email"
            />
            {fieldErrors.email && (
              <span className="error-message">{fieldErrors.email}</span>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              type="password"
              id="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={isSubmitting}
              className={`form-input ${fieldErrors.password ? 'input-error' : ''}`}
              autoComplete="current-password"
            />
            {fieldErrors.password && (
              <span className="error-message">{fieldErrors.password}</span>
            )}
          </div>

          <button
            type="submit"
            className="btn btn-primary"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Logging in...' : 'Login'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default LoginPage;
