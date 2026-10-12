import React from 'react';

export const CustomerDetailsModal = ({ customer, isOpen, onClose }) => {
  if (!isOpen || !customer) return null;

  const formatDate = (isoString) => {
    if (!isoString) return '—';
    try {
      const date = new Date(isoString);
      return date.toLocaleString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-container customer-details-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Customer Details</h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close">Close</button>
        </div>

        <div className="modal-body p-4">
          <div className="customer-detail-card card mb-3">
            <div className="customer-avatar-box">
              <div className="customer-avatar-info">
                <h4 className="m-0 font-bold text-main">{customer.name}</h4>
                <span className="text-xs text-muted">Customer ID #{customer.id}</span>
              </div>
            </div>
          </div>

          <div className="detail-grid grid grid-2 gap-3 mb-3">
            <div className="detail-item card p-3">
              <span className="text-xs text-muted font-semibold uppercase">Phone Number</span>
              <p className="font-semibold text-main m-0 mt-1">{customer.phone || '—'}</p>
            </div>

            <div className="detail-item card p-3">
              <span className="text-xs text-muted font-semibold uppercase">Email Address</span>
              <p className="font-semibold text-main m-0 mt-1">{customer.email || '—'}</p>
            </div>
          </div>

          <div className="detail-item card p-3 mb-3">
            <span className="text-xs text-muted font-semibold uppercase">Delivery / Billing Address</span>
            <p className="text-main m-0 mt-1">{customer.address || '—'}</p>
          </div>

          <div className="detail-grid grid grid-2 gap-3">
            <div className="detail-item card p-3">
              <span className="text-xs text-muted font-semibold uppercase">Registered On</span>
              <p className="text-xs text-muted m-0 mt-1">{formatDate(customer.createdAt)}</p>
            </div>

            <div className="detail-item card p-3">
              <span className="text-xs text-muted font-semibold uppercase">Last Updated</span>
              <p className="text-xs text-muted m-0 mt-1">{formatDate(customer.updatedAt)}</p>
            </div>
          </div>
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

export default CustomerDetailsModal;
