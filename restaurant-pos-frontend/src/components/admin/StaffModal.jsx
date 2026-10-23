import React, { useState, useEffect } from 'react';
import staffService, { ROLE_MAP } from '../../services/staffService';
import useAuth from '../../hooks/useAuth';

export const StaffModal = ({ staffMember, hasKitchenStaff = false, isOpen, onClose, onSuccess }) => {
  const { user } = useAuth();
  const restaurantId = user?.restaurantId;

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [roleName, setRoleName] = useState('WAITER');
  const [password, setPassword] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const isEditing = Boolean(staffMember);
  const isKitchenDisabled = hasKitchenStaff && (!isEditing || staffMember?.roleName !== 'KITCHEN');

  useEffect(() => {
    if (isOpen) {
      setError('');
      if (staffMember) {
        setName(staffMember.name || '');
        setEmail(staffMember.email || '');
        setPhone(staffMember.phone || '');
        setRoleName(staffMember.roleName || 'WAITER');
        setIsActive(staffMember.isActive !== undefined ? staffMember.isActive : true);
        setPassword(''); // Password is never pre-filled
      } else {
        setName('');
        setEmail('');
        setPhone('');
        setRoleName('WAITER');
        setPassword('');
        setIsActive(true);
      }
    }
  }, [isOpen, staffMember]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    // Form validations
    if (!name.trim()) {
      setError('Staff member name is required.');
      return;
    }
    if (!email.trim() || !email.includes('@')) {
      setError('A valid email address is required.');
      return;
    }
    if (!isEditing && (!password || password.trim().length === 0)) {
      setError('Password is required when creating a new staff member.');
      return;
    }

    if (!isEditing && roleName === 'KITCHEN' && hasKitchenStaff) {
      setError('Only one kitchen staff account is allowed per restaurant.');
      return;
    }

    if (roleName === 'ADMIN' || roleName === 'SUPER_ADMIN') {
      if (!isEditing || staffMember?.roleName !== 'ADMIN') {
        setError('Admin accounts cannot be created.');
        return;
      }
    }

    const selectedRole = ROLE_MAP[roleName] || ROLE_MAP.WAITER;

    const payload = {
      restaurantId: Number(restaurantId),
      roleId: Number(selectedRole.id),
      name: name.trim(),
      email: email.trim(),
      phone: phone.trim() || null,
      isActive: roleName === 'ADMIN' ? true : Boolean(isActive),
    };

    if (password && password.trim().length > 0) {
      payload.password = password.trim();
    }

    setIsSubmitting(true);
    try {
      if (isEditing) {
        await staffService.updateStaffMember(staffMember.id, payload);
      } else {
        await staffService.createStaffMember(payload);
      }
      setPassword(''); // Clear sensitive password field immediately
      onSuccess();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to save staff member details');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-container staff-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{isEditing ? `Edit Staff — ${staffMember.name}` : 'Add New Staff Member'}</h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close">Close</button>
        </div>

        <form onSubmit={handleSubmit} className="modal-form">
          <div className="modal-body">
            {error && <div className="alert alert-danger mb-3">{error}</div>}

            <div className="form-group mb-3">
              <label className="form-label">Full Name *</label>
              <input
                type="text"
                className="form-input"
                placeholder="e.g. John Smith"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>

            <div className="form-group mb-3">
              <label className="form-label">Email Address *</label>
              <input
                type="email"
                className="form-input"
                placeholder="e.g. john.smith@restaurant.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>

            <div className="grid grid-2 mb-3">
              <div className="form-group">
                <label className="form-label">Role *</label>
                <select
                  className="form-input"
                  value={roleName}
                  disabled={isEditing && staffMember?.roleName === 'ADMIN'}
                  onChange={(e) => {
                    const newRole = e.target.value;
                    setRoleName(newRole);
                    if (newRole === 'ADMIN') {
                      setIsActive(true);
                    }
                  }}
                >
                  {isEditing && staffMember?.roleName === 'ADMIN' ? (
                    <option value="ADMIN">Admin</option>
                  ) : (
                    <>
                      <option value="WAITER">Waiter</option>
                      <option value="KITCHEN" disabled={isKitchenDisabled}>
                        Kitchen {isKitchenDisabled ? '(Limit reached)' : ''}
                      </option>
                    </>
                  )}
                </select>
                {isKitchenDisabled && (
                  <p className="text-muted text-xs mt-1" style={{ color: 'var(--text-secondary, #94a3b8)', marginBottom: 0 }}>
                    Only one kitchen staff account is allowed per restaurant.
                  </p>
                )}
              </div>

              <div className="form-group">
                <label className="form-label">Phone Number (Optional)</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. +91 9876543210"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                />
              </div>
            </div>

            <div className="form-group mb-3">
              <label className="form-label">
                {isEditing ? 'New Password (Leave blank to keep current)' : 'Password *'}
              </label>
              <input
                type="password"
                className="form-input"
                placeholder={isEditing ? 'Enter new password if changing' : 'Enter login password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required={!isEditing}
              />
            </div>

            {isEditing && (
              <div className="form-group mb-3">
                {roleName === 'ADMIN' ? (
                  <div className="text-muted text-xs font-semibold p-2 bg-dark rounded border border-color">
                    Admin accounts are always active and cannot be deactivated.
                  </div>
                ) : (
                  <label className="form-checkbox-label">
                    <input
                      type="checkbox"
                      checked={isActive}
                      onChange={(e) => setIsActive(e.target.checked)}
                    />
                    <span>Account is Active (Allow login access)</span>
                  </label>
                )}
              </div>
            )}
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <span className="spinner-sm"></span> Saving...
                </>
              ) : isEditing ? (
                'Update Staff Member'
              ) : (
                'Create Staff Member'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default StaffModal;
