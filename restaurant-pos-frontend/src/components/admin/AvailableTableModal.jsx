import React from 'react';
import AdminModal from './AdminModal';

export const AvailableTableModal = ({
  isOpen,
  onClose,
  table,
  onAddOrder,
  onEditTable,
}) => {
  if (!table) return null;

  return (
    <AdminModal
      isOpen={isOpen}
      onClose={onClose}
      title={`Table ${table.tableNumber} Details`}
    >
      <div className="table-details-modal-body">
        <div className="details-meta-grid mb-4">
          <div className="meta-item">
            <span className="meta-label">Table Number</span>
            <span className="meta-value font-weight-bold" style={{ fontSize: '1.25rem' }}>
              Table {table.tableNumber}
            </span>
          </div>

          <div className="meta-item">
            <span className="meta-label">Capacity</span>
            <span className="meta-value">{table.capacity} Seats</span>
          </div>

          <div className="meta-item">
            <span className="meta-label">Status</span>
            <span className="meta-value">
              <span className="pos-table-badge status-badge-available">Available</span>
            </span>
          </div>
        </div>

        <p className="text-muted mb-4" style={{ fontSize: '0.9rem', color: 'var(--wn-text-muted)' }}>
          This table is currently available. You can create a new dine-in order for guests seated here or modify the table settings.
        </p>

        <div className="modal-actions-stacked" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <button
            type="button"
            className="btn btn-primary"
            style={{ width: '100%', padding: '0.75rem 1rem', fontSize: '1rem', fontWeight: 600 }}
            onClick={() => onAddOrder(table)}
          >
            Add Order for Table {table.tableNumber}
          </button>

          <button
            type="button"
            className="btn btn-secondary"
            style={{ width: '100%', padding: '0.55rem 1rem', fontSize: '0.85rem' }}
            onClick={() => onEditTable(table)}
          >
            Edit Table Capacity & Number
          </button>
        </div>

        <div className="modal-footer" style={{ marginTop: '1.5rem', display: 'flex', justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </AdminModal>
  );
};

export default AvailableTableModal;
