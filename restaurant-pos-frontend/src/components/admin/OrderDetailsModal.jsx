import React from 'react';
import AdminModal from './AdminModal';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const OrderDetailsModal = ({ isOpen, onClose, order, onGenerateBill }) => {
  if (!order) return null;

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
      const date = new Date(dateStr);
      return date.toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
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

  const renderTypeBadge = (orderType) => {
    const t = (orderType || '').toUpperCase();
    return (
      <span className={`badge ${t === 'DINE_IN' ? 'badge-dinein' : 'badge-takeaway'}`}>
        {t === 'DINE_IN' ? 'Dine In' : 'Takeaway'}
      </span>
    );
  };

  return (
    <AdminModal
      isOpen={isOpen}
      onClose={onClose}
      title={`Order Details #${order.id}`}
    >
      <div className="order-details-body">
        {/* Meta Info Header */}
        <div className="details-meta-grid">
          <div className="meta-item">
            <span className="meta-label">Order Type</span>
            <span className="meta-value">{renderTypeBadge(order.orderType)}</span>
          </div>

          <div className="meta-item">
            <span className="meta-label">Current Status</span>
            <span className="meta-value">{renderStatusBadge(order.status)}</span>
          </div>

          {order.orderType === 'DINE_IN' && (
            <div className="meta-item">
              <span className="meta-label">Table</span>
              <span className="meta-value font-weight-bold">
                {order.tableName
                  ? order.tableName
                  : (order.tableNumber !== undefined && order.tableNumber !== null)
                  ? `Table ${order.tableNumber}`
                  : order.tableId
                  ? `Table ${order.tableId}`
                  : '—'}
              </span>
            </div>
          )}

          {(order.waiterName || order.waiterId) && (
            <div className="meta-item">
              <span className="meta-label">Waiter</span>
              <span className="meta-value font-weight-bold">
                {order.waiterName || `Waiter #${order.waiterId}`}
              </span>
            </div>
          )}

          <div className="meta-item">
            <span className="meta-label">Created At</span>
            <span className="meta-value text-muted">{formatDate(order.createdAt)}</span>
          </div>

          {order.updatedAt && (
            <div className="meta-item">
              <span className="meta-label">Updated At</span>
              <span className="meta-value text-muted">{formatDate(order.updatedAt)}</span>
            </div>
          )}
        </div>

        {/* Overall Notes */}
        {order.notes && (
          <div className="details-notes-box">
            <span className="notes-heading">Order Notes / Instructions:</span>
            <p className="notes-text">{order.notes}</p>
          </div>
        )}

        {/* Items Table */}
        <div className="details-items-section">
          <h4 className="section-heading">Order Items</h4>
          <div className="table-responsive">
            <table className="data-table details-table">
              <thead>
                <tr>
                  <th>Item Name</th>
                  <th>Price</th>
                  <th>Qty</th>
                  <th>Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {Array.isArray(order.items) && order.items.length > 0 ? (
                  order.items.map((item) => (
                    <tr key={item.id || item.menuItemId}>
                      <td>
                        <div className="item-name-cell">
                          <span className="font-weight-bold">
                            {item.menuItemName || `Item #${item.menuItemId}`}
                          </span>
                          {item.notes && (
                            <span className="item-note-text">
                              Note: {item.notes}
                            </span>
                          )}
                        </div>
                      </td>
                      <td>
                        {CURRENCY_SYMBOL}
                        {Number(item.unitPrice || 0).toFixed(2)}
                      </td>
                      <td>x{item.quantity}</td>
                      <td className="font-weight-bold">
                        {CURRENCY_SYMBOL}
                        {Number(item.subtotal || 0).toFixed(2)}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="4" className="text-center text-muted">
                      No item details available.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Financial Summary */}
        <div className="details-summary-box">
          <div className="summary-row">
            <span>Subtotal:</span>
            <span>
              {CURRENCY_SYMBOL}
              {Number(order.subtotal || 0).toFixed(2)}
            </span>
          </div>
          {Number(order.taxAmount || 0) > 0 && (
            <div className="summary-row">
              <span>Tax:</span>
              <span>
                {CURRENCY_SYMBOL}
                {Number(order.taxAmount).toFixed(2)}
              </span>
            </div>
          )}
          {Number(order.discountAmount || 0) > 0 && (
            <div className="summary-row">
              <span>Discount:</span>
              <span>
                -{CURRENCY_SYMBOL}
                {Number(order.discountAmount).toFixed(2)}
              </span>
            </div>
          )}
          <div className="summary-row summary-total-row">
            <span>Total Amount:</span>
            <span>
              {CURRENCY_SYMBOL}
              {Number(order.totalAmount || order.subtotal || 0).toFixed(2)}
            </span>
          </div>
        </div>

        <div className="modal-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1.5rem' }}>
          <div>
            {order.status !== 'CANCELLED' && order.status !== 'COMPLETED' && (
              <button
                type="button"
                className="btn btn-primary btn-generate-bill"
                onClick={() => {
                  if (onGenerateBill) {
                    onGenerateBill(order);
                  }
                }}
              >
                Generate Bill
              </button>
            )}

            {order.status === 'COMPLETED' && (
              <button
                type="button"
                className="btn btn-secondary btn-view-bill"
                onClick={() => {
                  if (onGenerateBill) {
                    onGenerateBill(order);
                  }
                }}
              >
                View Bill / Receipt
              </button>
            )}

            {order.status === 'CANCELLED' && (
              <span className="badge badge-cancelled">
                Order Cancelled
              </span>
            )}
          </div>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </AdminModal>
  );
};

export default OrderDetailsModal;
