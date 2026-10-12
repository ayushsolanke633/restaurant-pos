import React, { useState, useEffect } from 'react';
import customerService from '../../services/customerService';

export const CustomerModal = ({ customer, isOpen, onClose, onSuccess }) => {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const isEditing = Boolean(customer);

  useEffect(() => {
    if (isOpen) {
      setError('');
      if (customer) {
        setName(customer.name || '');
        setPhone(customer.phone || '');
        setEmail(customer.email || '');
        setAddress(customer.address || '');
      } else {
        setName('');
        setPhone('');
        setEmail('');
        setAddress('');
      }
    }
  }, [isOpen, customer]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    // Validations
    if (!name.trim()) {
      setError('Customer name is required.');
      return;
    }

    if (email && email.trim() && !email.includes('@')) {
      setError('Please enter a valid email address.');
      return;
    }

    const payload = {
      name: name.trim(),
      phone: phone.trim() || null,
      email: email.trim() || null,
      address: address.trim() || null,
    };

    setIsSubmitting(true);
    try {
      if (isEditing) {
        await customerService.updateCustomer(customer.id, payload);
      } else {
        await customerService.createCustomer(payload);
      }
      onSuccess();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to save customer details');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-container customer-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{isEditing ? `Edit Customer — ${customer.name}` : 'Add New Customer'}</h3>
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
                placeholder="e.g. Rahul Sharma"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>

            <div className="grid grid-2 mb-3">
              <div className="form-group">
                <label className="form-label">Phone Number (Optional)</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. 9876543210"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Email Address (Optional)</label>
                <input
                  type="email"
                  className="form-input"
                  placeholder="e.g. rahul@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
            </div>

            <div className="form-group mb-3">
              <label className="form-label">Address (Optional)</label>
              <textarea
                className="form-input form-textarea"
                rows="3"
                placeholder="e.g. Flat 302, Green Park Apartments, Nagpur"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
              ></textarea>
            </div>
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
                'Update Customer'
              ) : (
                'Create Customer'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CustomerModal;
