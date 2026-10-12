import React, { useState, useEffect, useCallback } from 'react';
import platformService from '../../services/platformService';

export const SuperAdminRestaurantsPage = () => {
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [modalError, setModalError] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const initialFormState = {
    restaurantName: '',
    address: '',
    phone: '',
    email: '',
    gstNumber: '',
    adminName: '',
    adminEmail: '',
    adminPassword: '',
    adminPhone: '',
  };
  const [formData, setFormData] = useState(initialFormState);
  const [fieldErrors, setFieldErrors] = useState({});

  // Status toggle in progress
  const [statusUpdatingId, setStatusUpdatingId] = useState(null);

  const fetchRestaurants = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const data = await platformService.getAllRestaurants();
      setRestaurants(data || []);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load restaurants.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRestaurants();
  }, [fetchRestaurants]);

  // Handle Restaurant Status Toggle (Deactivate / Activate)
  const handleToggleStatus = async (restaurant) => {
    const nextStatus = !restaurant.isActive;
    const actionLabel = nextStatus ? 'activate' : 'deactivate';
    const confirmMsg = nextStatus
      ? `Are you sure you want to activate "${restaurant.name}"? Staff will be permitted to log in.`
      : `Are you sure you want to deactivate "${restaurant.name}"? Active staff sessions and WebSocket connections will be terminated immediately.`;

    if (!window.confirm(confirmMsg)) {
      return;
    }

    try {
      setStatusUpdatingId(restaurant.id);
      await platformService.updateRestaurantStatus(restaurant.id, nextStatus);
      await fetchRestaurants();
    } catch (err) {
      alert(`Failed to ${actionLabel} restaurant: ` + (err?.response?.data?.message || err.message));
    } finally {
      setStatusUpdatingId(null);
    }
  };

  const handleOpenModal = () => {
    setFormData(initialFormState);
    setFieldErrors({});
    setModalError('');
    setShowPassword(false);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    if (!submitting) {
      setIsModalOpen(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    if (fieldErrors[name]) {
      setFieldErrors((prev) => ({ ...prev, [name]: '' }));
    }
  };

  const validateOnboardForm = () => {
    const errors = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if (!formData.restaurantName.trim()) {
      errors.restaurantName = 'Restaurant name is required.';
    }
    if (formData.email && !emailRegex.test(formData.email.trim())) {
      errors.email = 'Invalid restaurant contact email address.';
    }

    if (!formData.adminName.trim()) {
      errors.adminName = 'Admin name is required.';
    }

    if (!formData.adminEmail.trim()) {
      errors.adminEmail = 'Admin email address is required.';
    } else if (!emailRegex.test(formData.adminEmail.trim())) {
      errors.adminEmail = 'Please provide a valid email format for the admin.';
    }

    if (!formData.adminPassword) {
      errors.adminPassword = 'Admin password is required.';
    } else if (formData.adminPassword.length < 8) {
      errors.adminPassword = 'Password must be at least 8 characters long.';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleOnboardSubmit = async (e) => {
    e.preventDefault();
    setModalError('');

    if (!validateOnboardForm()) {
      return;
    }

    setSubmitting(true);
    try {
      await platformService.onboardRestaurant(formData);
      setIsModalOpen(false);
      await fetchRestaurants();
    } catch (err) {
      if (err.response?.status === 409) {
        setModalError(err.response?.data?.message || 'An account with this admin email already exists.');
      } else {
        setModalError(err?.response?.data?.message || err?.message || 'Failed to onboard restaurant.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  // Filtered restaurants
  const filteredRestaurants = restaurants.filter((r) => {
    const matchesSearch =
      (r.name && r.name.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (r.email && r.email.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (r.adminEmail && r.adminEmail.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (r.phone && r.phone.includes(searchQuery));

    const matchesStatus =
      statusFilter === 'ALL' ||
      (statusFilter === 'ACTIVE' && r.isActive) ||
      (statusFilter === 'DEACTIVATED' && !r.isActive);

    return matchesSearch && matchesStatus;
  });

  const totalCount = restaurants.length;
  const activeCount = restaurants.filter((r) => r.isActive).length;
  const deactivatedCount = restaurants.filter((r) => !r.isActive).length;

  return (
    <div className="platform-container">
      {/* Top Banner / Header */}
      <div className="platform-header-section">
        <div>
          <h1 className="platform-page-title">Tenant Restaurants</h1>
          <p className="platform-page-subtitle">
            Manage multi-tenant restaurant instances, status activation, and atomic onboarding.
          </p>
        </div>
        <button
          onClick={handleOpenModal}
          className="btn btn-primary btn-onboard"
        >
          + Onboard New Restaurant
        </button>
      </div>

      {/* KPI Stats Cards */}
      <div className="platform-stats-grid">
        <div className="card stat-card">
          <span className="stat-label">Total Registered Tenants</span>
          <span className="stat-value">{totalCount}</span>
        </div>
        <div className="card stat-card">
          <span className="stat-label">Active Tenants</span>
          <span className="stat-value text-success">{activeCount}</span>
        </div>
        <div className="card stat-card">
          <span className="stat-label">Deactivated / Suspended</span>
          <span className="stat-value text-danger">{deactivatedCount}</span>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="card platform-controls-card">
        <div className="search-bar">
          <input
            type="text"
            placeholder="Search by restaurant name, email, or admin..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="form-input"
          />
        </div>
        <div className="filter-group">
          <button
            className={`btn-filter ${statusFilter === 'ALL' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ALL')}
          >
            All ({totalCount})
          </button>
          <button
            className={`btn-filter ${statusFilter === 'ACTIVE' ? 'active' : ''}`}
            onClick={() => setStatusFilter('ACTIVE')}
          >
            Active ({activeCount})
          </button>
          <button
            className={`btn-filter ${statusFilter === 'DEACTIVATED' ? 'active' : ''}`}
            onClick={() => setStatusFilter('DEACTIVATED')}
          >
            Deactivated ({deactivatedCount})
          </button>
        </div>
      </div>

      {/* Error alert */}
      {error && (
        <div className="alert alert-danger" role="alert">
          <span>{error}</span>
        </div>
      )}

      {/* Restaurants Table */}
      <div className="card platform-table-card">
        {loading ? (
          <div className="loading-state">
            <div className="spinner"></div>
            <p>Loading restaurants...</p>
          </div>
        ) : filteredRestaurants.length === 0 ? (
          <div className="empty-state">
            <p>No restaurants match your search or filter criteria.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="platform-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Restaurant Name</th>
                  <th>Contact Info</th>
                  <th>Initial Admin</th>
                  <th>Staff Count</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredRestaurants.map((r) => (
                  <tr key={r.id} className={!r.isActive ? 'row-deactivated' : ''}>
                    <td className="cell-id">#{r.id}</td>
                    <td>
                      <div className="restaurant-name-cell">
                        <strong>{r.name}</strong>
                        {r.address && <small className="text-muted">{r.address}</small>}
                      </div>
                    </td>
                    <td>
                      <div>
                        {r.phone && <div>📞 {r.phone}</div>}
                        {r.email && <div className="text-muted">✉️ {r.email}</div>}
                        {r.gstNumber && <div className="text-muted">GST: {r.gstNumber}</div>}
                      </div>
                    </td>
                    <td>
                      <div className="admin-email-badge">
                        {r.adminEmail ? r.adminEmail : <span className="text-muted">—</span>}
                      </div>
                    </td>
                    <td>{r.totalUsers} users</td>
                    <td>
                      <span className={`badge ${r.isActive ? 'badge-success' : 'badge-danger'}`}>
                        {r.isActive ? 'ACTIVE' : 'DEACTIVATED'}
                      </span>
                    </td>
                    <td>
                      <button
                        onClick={() => handleToggleStatus(r)}
                        disabled={statusUpdatingId === r.id}
                        className={`btn btn-sm ${r.isActive ? 'btn-danger-outline' : 'btn-success-outline'}`}
                      >
                        {statusUpdatingId === r.id
                          ? 'Updating...'
                          : r.isActive
                          ? 'Deactivate'
                          : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Onboard Restaurant Modal */}
      {isModalOpen && (
        <div className="modal-backdrop" onClick={handleCloseModal}>
          <div className="modal-card platform-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Onboard New Restaurant Tenant</h2>
              <button
                type="button"
                className="btn-close"
                onClick={handleCloseModal}
                disabled={submitting}
              >
                ✕
              </button>
            </div>

            {modalError && (
              <div className="alert alert-danger" role="alert">
                <span>{modalError}</span>
              </div>
            )}

            <form onSubmit={handleOnboardSubmit} noValidate>
              <div className="modal-body modal-scrollable">
                <h3 className="form-section-title">Restaurant Details</h3>
                <div className="form-group">
                  <label htmlFor="restaurantName">Restaurant Name *</label>
                  <input
                    type="text"
                    id="restaurantName"
                    name="restaurantName"
                    placeholder="e.g. Olive & Thyme Bistro"
                    value={formData.restaurantName}
                    onChange={handleInputChange}
                    className={`form-input ${fieldErrors.restaurantName ? 'input-error' : ''}`}
                    disabled={submitting}
                  />
                  {fieldErrors.restaurantName && (
                    <span className="error-message">{fieldErrors.restaurantName}</span>
                  )}
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label htmlFor="phone">Restaurant Phone</label>
                    <input
                      type="text"
                      id="phone"
                      name="phone"
                      placeholder="e.g. 9876543210"
                      value={formData.phone}
                      onChange={handleInputChange}
                      className="form-input"
                      disabled={submitting}
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="email">Restaurant Contact Email</label>
                    <input
                      type="email"
                      id="email"
                      name="email"
                      placeholder="e.g. info@bistro.com"
                      value={formData.email}
                      onChange={handleInputChange}
                      className={`form-input ${fieldErrors.email ? 'input-error' : ''}`}
                      disabled={submitting}
                    />
                    {fieldErrors.email && (
                      <span className="error-message">{fieldErrors.email}</span>
                    )}
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label htmlFor="gstNumber">GST Number</label>
                    <input
                      type="text"
                      id="gstNumber"
                      name="gstNumber"
                      placeholder="e.g. 29ABCDE1234F1Z5"
                      value={formData.gstNumber}
                      onChange={handleInputChange}
                      className="form-input"
                      disabled={submitting}
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="address">Address</label>
                    <input
                      type="text"
                      id="address"
                      name="address"
                      placeholder="e.g. 123 Brigade Road, Bangalore"
                      value={formData.address}
                      onChange={handleInputChange}
                      className="form-input"
                      disabled={submitting}
                    />
                  </div>
                </div>

                <h3 className="form-section-title">Initial Tenant Administrator</h3>
                <div className="form-row">
                  <div className="form-group">
                    <label htmlFor="adminName">Admin Full Name *</label>
                    <input
                      type="text"
                      id="adminName"
                      name="adminName"
                      placeholder="e.g. Rajesh Kumar"
                      value={formData.adminName}
                      onChange={handleInputChange}
                      className={`form-input ${fieldErrors.adminName ? 'input-error' : ''}`}
                      disabled={submitting}
                    />
                    {fieldErrors.adminName && (
                      <span className="error-message">{fieldErrors.adminName}</span>
                    )}
                  </div>
                  <div className="form-group">
                    <label htmlFor="adminPhone">Admin Phone</label>
                    <input
                      type="text"
                      id="adminPhone"
                      name="adminPhone"
                      placeholder="e.g. 9876543210"
                      value={formData.adminPhone}
                      onChange={handleInputChange}
                      className="form-input"
                      disabled={submitting}
                    />
                  </div>
                </div>

                <div className="form-row">
                  <div className="form-group">
                    <label htmlFor="adminEmail">Admin Email (Login Username) *</label>
                    <input
                      type="email"
                      id="adminEmail"
                      name="adminEmail"
                      placeholder="e.g. rajesh@bistro.com"
                      value={formData.adminEmail}
                      onChange={handleInputChange}
                      className={`form-input ${fieldErrors.adminEmail ? 'input-error' : ''}`}
                      disabled={submitting}
                    />
                    {fieldErrors.adminEmail && (
                      <span className="error-message">{fieldErrors.adminEmail}</span>
                    )}
                  </div>
                  <div className="form-group">
                    <label htmlFor="adminPassword">Initial Admin Password *</label>
                    <div className="password-input-wrapper">
                      <input
                        type={showPassword ? 'text' : 'password'}
                        id="adminPassword"
                        name="adminPassword"
                        placeholder="At least 8 characters"
                        value={formData.adminPassword}
                        onChange={handleInputChange}
                        className={`form-input ${fieldErrors.adminPassword ? 'input-error' : ''}`}
                        disabled={submitting}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className="btn-toggle-pw"
                      >
                        {showPassword ? 'Hide' : 'Show'}
                      </button>
                    </div>
                    {fieldErrors.adminPassword && (
                      <span className="error-message">{fieldErrors.adminPassword}</span>
                    )}
                  </div>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  onClick={handleCloseModal}
                  className="btn btn-secondary"
                  disabled={submitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={submitting}
                >
                  {submitting ? 'Onboarding Restaurant...' : 'Complete Onboarding'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default SuperAdminRestaurantsPage;
