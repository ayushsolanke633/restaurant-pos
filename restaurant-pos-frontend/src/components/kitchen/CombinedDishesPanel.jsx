import React, { useState } from 'react';
import { calculateCombinedDishes } from '../../utils/kitchenUtils';

export const CombinedDishesPanel = ({ orders }) => {
  const [expandedIds, setExpandedIds] = useState(new Set());

  const combinedDishes = calculateCombinedDishes(orders);

  const toggleExpand = (menuItemId) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(menuItemId)) {
        next.delete(menuItemId);
      } else {
        next.add(menuItemId);
      }
      return next;
    });
  };

  const toggleExpandAll = () => {
    if (expandedIds.size === combinedDishes.length) {
      setExpandedIds(new Set());
    } else {
      setExpandedIds(new Set(combinedDishes.map((d) => d.menuItemId)));
    }
  };

  return (
    <div className="kds-column column-combined">
      {/* Column Header */}
      <div className="kds-column-header">
        <div className="d-flex align-items-center gap-2">
          <span className="col-title">COMBINED DISHES</span>
          {combinedDishes.length > 0 && (
            <button
              type="button"
              className="btn-toggle-all"
              onClick={toggleExpandAll}
              title={expandedIds.size === combinedDishes.length ? 'Collapse All' : 'Expand All'}
            >
              {expandedIds.size === combinedDishes.length ? 'Collapse All' : 'Expand All'}
            </button>
          )}
        </div>
        <span className="col-count-badge count-combined">{combinedDishes.length}</span>
      </div>

      {/* Column Body */}
      <div className="kds-column-body">
        {combinedDishes.length === 0 ? (
          <div className="kds-empty-col">
            <span>No dishes currently in preparation</span>
            <p className="empty-subtext mt-1">Accept orders from NEW column to populate combined queue.</p>
          </div>
        ) : (
          combinedDishes.map((dish) => {
            const isExpanded = expandedIds.has(dish.menuItemId);
            return (
              <div key={dish.menuItemId || dish.menuItemName} className="combined-dish-card">
                {/* Main Card Summary */}
                <div
                  className="combined-card-header"
                  onClick={() => toggleExpand(dish.menuItemId)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      toggleExpand(dish.menuItemId);
                    }
                  }}
                >
                  <div className="combined-dish-title">
                    <span className="dish-name">{dish.menuItemName}</span>
                    {dish.categoryName && <span className="dish-category">{dish.categoryName}</span>}
                  </div>

                  <div className="combined-dish-meta">
                    <span className="combined-total-qty">Total: {dish.totalQuantity}</span>
                    <span className="combined-order-count">
                      {dish.orders.length} {dish.orders.length === 1 ? 'order' : 'orders'}
                    </span>
                    <span className="expand-indicator">{isExpanded ? 'Less' : 'More'}</span>
                  </div>
                </div>

                {/* Expandable Order Breakdown */}
                {isExpanded && (
                  <div className="combined-breakdown-section">
                    <div className="breakdown-title">Contributing Orders Breakdown:</div>
                    <div className="breakdown-list">
                      {dish.orders.map((ord, idx) => {
                        const isDineIn = ord.orderType === 'DINE_IN';
                        const tableLabel = isDineIn
                          ? `Table ${ord.tableNumber || '#'}`
                          : 'Takeaway';

                        return (
                          <div key={idx} className="breakdown-order-row">
                            <div className="breakdown-row-left">
                              <span className="breakdown-order-id">Order #{ord.orderId}</span>
                              <span className={`breakdown-type-badge ${isDineIn ? 'type-dinein' : 'type-takeaway'}`}>
                                {isDineIn ? tableLabel : 'Takeaway'}
                              </span>
                              <span className={`breakdown-status-chip status-${ord.status.toLowerCase()}`}>
                                {ord.status}
                              </span>
                            </div>

                            <div className="breakdown-row-right">
                              <span className="breakdown-qty-badge">Qty: {ord.quantity}</span>
                            </div>

                            {/* Individual Item Note Display */}
                            {ord.notes && (
                              <div className="breakdown-note-box">
                                <span>Note: {ord.notes}</span>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default CombinedDishesPanel;
