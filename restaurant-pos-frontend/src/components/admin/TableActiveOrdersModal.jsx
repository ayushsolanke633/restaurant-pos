import React from 'react';
import AdminModal from './AdminModal';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const TableActiveOrdersModal = ({
  isOpen,
  onClose,
  table,
  orders = [],
  onSelectOrder,
}) => {
  if (!table || !isOpen) return null;

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
      const date = new Date(dateStr);
      return date.toLocaleTimeString(undefined, {
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateStr;
    }
  };

  const renderStatusBadge = (status) => {
    const s = (status || '').toUpperCase();
    let badgeClass = 'badge-inactive';

    if (s === 'NEW') badgeClass = 'badge-new';
    else if (s === 'ACCEPTED') badgeClass = 'badge-accepted';
    else if (s === 'PREPARING') badgeClass = 'badge-preparing';
    else if (s === 'READY') badgeClass = 'badge-ready';
    else if (s === 'COMPLETED') badgeClass = 'badge-completed';
    else if (s === 'CANCELLED') badgeClass = 'badge-cancelled';

    return <span className={`badge ${badgeClass}`}>{status}</span>;
  };

  return (
    <AdminModal
      isOpen={isOpen}
      onClose={onClose}
      title={`Table ${table.tableNumber} - Active Orders (${orders.length})`}
    >
      <div className="table-active-orders-body">
        <div className="alert alert-warning mb-3" style={{ fontSize: '0.88rem' }}>
          <strong>Multiple active orders found for Table {table.tableNumber}.</strong>
          <p style={{ margin: '0.25rem 0 0 0' }}>
            Please select the specific order you want to view, add items to, or cancel. Operations will only modify the selected order.
          </p>
        </div>

        <div className="active-orders-list" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', maxHeight: '350px', overflowY: 'auto' }}>
          {orders.map((ord) => (
            <div
              key={ord.id}
              className="card active-order-card"
              style={{
                padding: '0.85rem 1rem',
                border: '1px solid var(--wn-border)',
                borderRadius: 'var(--radius-md)',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: '1rem',
                backgroundColor: 'var(--wn-card-surface)',
              }}
            >
              <div className="order-info-left" style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span style={{ fontWeight: 700, fontSize: '1rem', color: 'var(--wn-text-main)' }}>
                    Order #{ord.id}
                  </span>
                  {renderStatusBadge(ord.status)}
                </div>

                <div style={{ fontSize: '0.8rem', color: 'var(--wn-text-muted)' }}>
                  {ord.items?.length || 0} item(s) • Placed at {formatDate(ord.createdAt)}
                  {ord.waiterName ? ` • Waiter: ${ord.waiterName}` : ''}
                </div>

                {ord.notes && (
                  <div style={{ fontSize: '0.75rem', color: 'var(--wn-text-muted)', fontStyle: 'italic' }}>
                    Note: {ord.notes}
                  </div>
                )}
              </div>

              <div className="order-info-right" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.4rem' }}>
                <span style={{ fontWeight: 800, fontSize: '1.05rem', color: 'var(--wn-text-main)' }}>
                  {CURRENCY_SYMBOL}{Number(ord.totalAmount || ord.subtotal || 0).toFixed(2)}
                </span>

                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={() => onSelectOrder(ord)}
                >
                  Select Order #{ord.id} →
                </button>
              </div>
            </div>
          ))}
        </div>

        <div className="modal-footer" style={{ marginTop: '1.25rem', display: 'flex', justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </AdminModal>
  );
};

export default TableActiveOrdersModal;
