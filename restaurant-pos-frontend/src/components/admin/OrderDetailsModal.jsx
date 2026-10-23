import React, { useState, useEffect } from 'react';
import AdminModal from './AdminModal';
import billingService from '../../services/billingService';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const OrderDetailsModal = ({
  isOpen,
  onClose,
  order,
  onGenerateBill,
  onAddItems,
  onCancelOrder,
  hasMultipleOrders = false,
  onBackToOrderList,
}) => {
  const [billExists, setBillExists] = useState(false);
  const [checkingBill, setCheckingBill] = useState(false);
  const [isConfirmingCancel, setIsConfirmingCancel] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [cancelError, setCancelError] = useState('');

  // Check whether a bill exists for this order to enforce billing safeguards
  useEffect(() => {
    let isMounted = true;
    if (isOpen && order?.id) {
      setIsConfirmingCancel(false);
      setCancelError('');
      // If table status is BILLING or order has been completed/cancelled
      if (order.tableStatus === 'BILLING') {
        setBillExists(true);
      } else {
        setCheckingBill(true);
        billingService.getBillByOrderId(order.id)
          .then((bill) => {
            if (isMounted) setBillExists(!!bill);
          })
          .catch(() => {
            if (isMounted) setBillExists(false);
          })
          .finally(() => {
            if (isMounted) setCheckingBill(false);
          });
      }
    } else {
      setBillExists(false);
      setIsConfirmingCancel(false);
      setCancelError('');
    }
    return () => {
      isMounted = false;
    };
  }, [isOpen, order?.id, order?.tableStatus]);

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

  // Determine permissions and explanatory reasons
  const orderStatus = (order.status || '').toUpperCase();
  const isTerminal = orderStatus === 'COMPLETED' || orderStatus === 'CANCELLED';

  // Adding items rules: Allowed when active status AND no bill generated
  const activeStatusesForAdd = ['NEW', 'ACCEPTED', 'PREPARING', 'READY'];
  const canAddItems = activeStatusesForAdd.includes(orderStatus) && !billExists && !checkingBill;
  let addItemsReason = '';
  if (isTerminal) {
    addItemsReason = `Cannot add items: Order is ${orderStatus.toLowerCase()}.`;
  } else if (billExists) {
    addItemsReason = 'Cannot add items because a bill has already been generated for this order.';
  }

  // Cancellation rules for ADMIN: Allowed when NEW or ACCEPTED AND no bill generated
  const canCancel = (orderStatus === 'NEW' || orderStatus === 'ACCEPTED') && !billExists && !checkingBill;
  let cancelReason = '';
  if (orderStatus === 'PREPARING' || orderStatus === 'READY') {
    cancelReason = 'Cancellation unavailable: Order is in kitchen preparation or ready.';
  } else if (billExists) {
    cancelReason = 'Cancellation unavailable: A bill has already been generated for this order.';
  } else if (orderStatus === 'COMPLETED') {
    cancelReason = 'Cancellation unavailable: Order is completed and paid.';
  } else if (orderStatus === 'CANCELLED') {
    cancelReason = 'Order is already cancelled.';
  }

  const handleCancelClick = () => {
    setCancelError('');
    setIsConfirmingCancel(true);
  };

  const handleConfirmCancel = async () => {
    if (!onCancelOrder) return;
    setIsCancelling(true);
    setCancelError('');
    try {
      await onCancelOrder(order);
      setIsConfirmingCancel(false);
    } catch (err) {
      setCancelError(err.response?.data?.message || err.message || 'Failed to cancel order.');
    } finally {
      setIsCancelling(false);
    }
  };

  return (
    <AdminModal
      isOpen={isOpen}
      onClose={onClose}
      title={`Order Details #${order.id}`}
    >
      <div className="order-details-body">
        {/* Navigation back to multiple orders list if applicable */}
        {hasMultipleOrders && onBackToOrderList && (
          <div className="mb-3" style={{ borderBottom: '1px solid var(--wn-border)', paddingBottom: '0.65rem' }}>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={onBackToOrderList}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}
            >
              <span>← Back to Table {order.tableNumber || ''} Orders List</span>
            </button>
          </div>
        )}

        {/* Cancellation inline error alert */}
        {cancelError && (
          <div className="alert alert-danger mb-3" role="alert">
            <span>{cancelError}</span>
            <button
              type="button"
              className="alert-dismiss"
              onClick={() => setCancelError('')}
            >
              Close
            </button>
          </div>
        )}

        {/* Billing safeguard informational banner */}
        {billExists && !isTerminal && (
          <div
            className="alert alert-info mb-3"
            role="status"
            style={{ fontSize: '0.82rem', padding: '0.55rem 0.85rem' }}
          >
            ℹ️ A bill has been generated for this order. Adding items and order cancellation are locked to safeguard billing integrity.
          </div>
        )}

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
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <h4 className="section-heading" style={{ margin: 0 }}>
              Order Items ({Array.isArray(order.items) ? order.items.length : 0})
            </h4>

            {/* Quick Add Items button in header if eligible */}
            {canAddItems && onAddItems && (
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={() => onAddItems(order)}
                style={{ fontSize: '0.8rem', fontWeight: 600 }}
              >
                + Add Items
              </button>
            )}
          </div>

          <div className="table-responsive">
            <table className="data-table details-table">
              <thead>
                <tr>
                  <th>Item Name</th>
                  <th>Batch / Kitchen</th>
                  <th>Price</th>
                  <th>Qty</th>
                  <th>Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {Array.isArray(order.items) && order.items.length > 0 ? (
                  order.items.map((item, index) => (
                    <tr key={item.id || item.menuItemId || index}>
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
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', flexWrap: 'wrap' }}>
                          <span
                            className="badge"
                            style={{
                              backgroundColor: 'var(--wn-card-surface, #f8f6f0)',
                              border: '1px solid var(--wn-border, #e2ded5)',
                              color: 'var(--wn-text-main, #333)',
                              fontSize: '0.7rem',
                              fontWeight: 600,
                            }}
                          >
                            Batch #{item.batchNumber || 1}
                          </span>
                          {item.status && (
                            <span
                              className={`badge ${
                                item.status === 'READY'
                                  ? 'badge-ready'
                                  : item.status === 'PREPARING'
                                  ? 'badge-preparing'
                                  : item.status === 'ACCEPTED'
                                  ? 'badge-accepted'
                                  : 'badge-new'
                              }`}
                              style={{ fontSize: '0.68rem' }}
                            >
                              {item.status}
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
                    <td colSpan="5" className="text-center text-muted">
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

        {/* Inline cancellation confirmation dialogue */}
        {isConfirmingCancel && (
          <div
            className="card confirm-cancel-card p-3 my-3"
            style={{
              backgroundColor: '#FFF5F5',
              border: '1px solid #FED7D7',
              borderRadius: 'var(--radius-md)',
            }}
          >
            <h5 style={{ color: '#C53030', margin: '0 0 0.4rem 0', fontSize: '0.95rem' }}>
              Confirm Order Cancellation
            </h5>
            <p style={{ fontSize: '0.85rem', color: '#742A2A', margin: '0 0 0.75rem 0' }}>
              Are you sure you want to cancel Order #{order.id}? This will cancel all items and update floor seating accordingly.
            </p>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                type="button"
                className="btn btn-danger btn-sm"
                onClick={handleConfirmCancel}
                disabled={isCancelling}
              >
                {isCancelling ? 'Cancelling...' : 'Yes, Cancel Order'}
              </button>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={() => setIsConfirmingCancel(false)}
                disabled={isCancelling}
              >
                No, Keep Order
              </button>
            </div>
          </div>
        )}

        {/* Modal Actions Footer */}
        <div
          className="modal-footer"
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: '1.5rem',
            flexWrap: 'wrap',
            gap: '0.5rem',
          }}
        >
          {/* Left Actions: Add Items & Cancel Order */}
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
            {/* Add Items Action */}
            {onAddItems && !isTerminal && (
              canAddItems ? (
                <button
                  type="button"
                  className="btn btn-primary btn-add-items"
                  onClick={() => onAddItems(order)}
                >
                  ➕ Add Items
                </button>
              ) : (
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled
                  title={addItemsReason}
                  style={{ opacity: 0.6, cursor: 'not-allowed' }}
                >
                  ➕ Add Items
                </button>
              )
            )}

            {/* Cancel Order Action */}
            {onCancelOrder && !isTerminal && !isConfirmingCancel && (
              canCancel ? (
                <button
                  type="button"
                  className="btn btn-danger btn-cancel-order"
                  onClick={handleCancelClick}
                >
                  Cancel Order
                </button>
              ) : (
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled
                  title={cancelReason}
                  style={{ opacity: 0.6, cursor: 'not-allowed' }}
                >
                  Cancel Order
                </button>
              )
            )}
          </div>

          {/* Right Actions: Generate Bill / View Bill / Close */}
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
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

            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Close
            </button>
          </div>
        </div>
      </div>
    </AdminModal>
  );
};

export default OrderDetailsModal;
