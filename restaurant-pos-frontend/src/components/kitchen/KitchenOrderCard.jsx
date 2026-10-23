import React, { useState, useEffect } from 'react';

export const KitchenOrderCard = ({ order, onStatusTransition }) => {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [elapsedMinutes, setElapsedMinutes] = useState(0);

  // Calculate elapsed time since order creation
  useEffect(() => {
    const calcElapsed = () => {
      if (!order?.createdAt) return;
      const created = new Date(order.createdAt).getTime();
      const now = new Date().getTime();
      const diffMins = Math.floor((now - created) / 60000);
      setElapsedMinutes(diffMins >= 0 ? diffMins : 0);
    };

    calcElapsed();
    const interval = setInterval(calcElapsed, 30000); // update every 30s
    return () => clearInterval(interval);
  }, [order?.createdAt]);

  const handleActionClick = async (actionType) => {
    setIsSubmitting(true);
    try {
      await onStatusTransition(order.orderId, actionType, order.batchNumber);
    } finally {
      setIsSubmitting(false);
    }
  };

  const isDineIn = order.orderType === 'DINE_IN';
  const tableText = isDineIn ? `Table ${order.tableNumber || '#'}` : 'Takeaway';

  // Determine elapsed time urgency class
  let urgencyClass = 'time-normal';
  if (elapsedMinutes > 20) {
    urgencyClass = 'time-critical';
  } else if (elapsedMinutes > 10) {
    urgencyClass = 'time-warning';
  }

  return (
    <div className={`kds-card kds-card-${order.status.toLowerCase()} ${order.isUpdate ? 'kds-card-update' : ''}`}>
      {/* Card Header */}
      <div className="kds-card-header">
        <div className="kds-header-top">
          <div className="d-flex align-items-center gap-2">
            <span className="kds-order-id">#{order.orderId}</span>
            {order.isUpdate && (
              <span className="kds-update-badge" title={`Incremental kitchen addition (Batch #${order.batchNumber})`}>
                ⚡ UPDATE #{order.batchNumber}
              </span>
            )}
            {!order.isUpdate && order.totalBatches > 1 && (
              <span className="kds-batch-tag" title="First batch of this order">
                Batch 1/{order.totalBatches}
              </span>
            )}
          </div>
          <span className={`kds-order-type-badge ${isDineIn ? 'type-dinein' : 'type-takeaway'}`}>
            {isDineIn ? tableText : 'Takeaway'}
          </span>
        </div>
        <div className="kds-header-meta">
          <span className={`kds-elapsed-tag ${urgencyClass}`}>
            {elapsedMinutes}m ago ({new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})
          </span>
        </div>
      </div>

      {/* Batches Overview if multiple additions exist */}
      {order.batches && order.batches.length > 1 && (
        <div className="kds-batches-summary">
          <div className="kds-batches-header">
            <span>Order Progression ({order.batches.length} Batches)</span>
            <span className="text-muted">Total: {order.batches.reduce((sum, b) => sum + (b.totalQuantity || 0), 0)} items</span>
          </div>
          <div className="kds-batch-pills">
            {order.batches.map((b) => {
              const isCurrent = b.batchNumber === order.batchNumber;
              return (
                <span
                  key={b.batchNumber}
                  className={`kds-batch-pill status-${b.status?.toLowerCase()} ${isCurrent ? 'current' : ''}`}
                  title={`Batch #${b.batchNumber}: ${b.status} (${b.totalQuantity || 0} items)`}
                >
                  {isCurrent ? '▶ ' : ''}B#{b.batchNumber}: {b.status} ({b.totalQuantity || 0}x)
                </span>
              );
            })}
          </div>
        </div>
      )}

      {/* Order Level Notes Banner */}
      {order.notes && (
        <div className="kds-order-notes-banner">
          <span className="notes-content">Note: {order.notes}</span>
        </div>
      )}

      {/* Order Items List */}
      <div className="kds-items-list">
        {order.items && order.items.length > 0 ? (
          order.items.map((item, idx) => (
            <div key={item.id || idx} className="kds-item-row">
              <span className="kds-item-qty">{item.quantity}x</span>
              <div className="kds-item-details">
                <span className="kds-item-name">{item.menuItemName}</span>
                {item.notes && <span className="kds-item-note font-bold">Note: {item.notes}</span>}
              </div>
            </div>
          ))
        ) : (
          <div className="text-muted text-center py-2">No item details</div>
        )}
      </div>

      {/* Card Action Footer */}
      <div className="kds-card-footer">
        {order.status === 'NEW' && (
          <button
            className="kds-action-btn btn-accept"
            onClick={() => handleActionClick('accept')}
            disabled={isSubmitting}
          >
            {isSubmitting
              ? 'Accepting...'
              : order.isUpdate
              ? `Accept Update #${order.batchNumber}`
              : 'Accept Order'}
          </button>
        )}

        {order.status === 'ACCEPTED' && (
          <button
            className="kds-action-btn btn-prepare"
            onClick={() => handleActionClick('prepare')}
            disabled={isSubmitting}
          >
            {isSubmitting
              ? 'Updating...'
              : order.isUpdate
              ? `Start Preparing Update #${order.batchNumber}`
              : 'Start Preparing'}
          </button>
        )}

        {order.status === 'PREPARING' && (
          <button
            className="kds-action-btn btn-ready"
            onClick={() => handleActionClick('ready')}
            disabled={isSubmitting}
          >
            {isSubmitting
              ? 'Updating...'
              : order.isUpdate
              ? `Mark Update #${order.batchNumber} Ready`
              : 'Mark Ready'}
          </button>
        )}

        {order.status === 'READY' && (
          <div className="kds-ready-badge">
            {order.isUpdate
              ? `Update #${order.batchNumber} Ready for Service`
              : 'Ready for Pickup / Service'}
          </div>
        )}
      </div>
    </div>
  );
};

export default KitchenOrderCard;
