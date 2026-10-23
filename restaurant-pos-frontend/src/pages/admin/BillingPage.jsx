import React, { useState, useEffect, useCallback } from 'react';
import useAutoDismiss from '../../hooks/useAutoDismiss';
import { orderService } from '../../services/orderService';
import { billingService } from '../../services/billingService';
import settingsService from '../../services/settingsService';
import BillModal from '../../components/admin/BillModal';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const BillingPage = () => {
  const [activeTab, setActiveTab] = useState('READY'); // 'READY' or 'COMPLETED'
  const [readyOrders, setReadyOrders] = useState([]);
  const [completedBills, setCompletedBills] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [actionSuccessMsg, setActionSuccessMsg] = useState(null);

  useAutoDismiss(actionSuccessMsg, () => setActionSuccessMsg(null));

  // Load billable orders (and completed bills if tab active)
  const fetchBillingData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (activeTab === 'READY') {
        const allOrders = await orderService.getOrders();
        let kitchenEnabled = true;
        try {
          const settings = await settingsService.getSettings();
          kitchenEnabled = settings?.kitchenEnabled !== false;
        } catch {
          kitchenEnabled = true;
        }

        if (kitchenEnabled) {
          const readyOnly = (allOrders || []).filter(o => o.status === 'READY');
          setReadyOrders(readyOnly);
        } else {
          // Kitchen-free mode: all active orders (not completed, not cancelled) are billable
          const activeOrders = (allOrders || []).filter(o => o.status !== 'COMPLETED' && o.status !== 'CANCELLED');
          setReadyOrders(activeOrders);
        }
      } else {
        // Fetch completed bills/orders
        const bills = await billingService.getAllBills();
        const list = Array.isArray(bills) ? bills : [];
        const sortedList = [...list].sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
        setCompletedBills(sortedList);
      }
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load billing data');
    } finally {
      setLoading(false);
    }
  }, [activeTab]);

  useEffect(() => {
    fetchBillingData();
  }, [fetchBillingData]);

  const handleOpenOrder = (order) => {
    setSelectedOrder(order);
    setIsModalOpen(true);
  };

  const handleModalClose = () => {
    setIsModalOpen(false);
    setSelectedOrder(null);
  };

  const handlePaymentSuccess = () => {
    setActionSuccessMsg('Payment completed successfully! Table released.');
    fetchBillingData();
  };

  // Filter ready orders by order id or table name
  const filteredReadyOrders = readyOrders.filter((order) => {
    if (!searchTerm) return true;
    const term = searchTerm.toLowerCase();
    const idMatch = order.id?.toString().includes(term);
    const tableMatch = (order.tableName || order.tableNumber)?.toString().toLowerCase().includes(term);
    const typeMatch = order.orderType?.toLowerCase().includes(term);
    return idMatch || tableMatch || typeMatch;
  });

  // Filter completed bills by bill number or order id and preserve newest-first ordering (createdAt DESC)
  const filteredCompletedBills = completedBills
    .filter((bill) => {
      if (!searchTerm) return true;
      const term = searchTerm.toLowerCase();
      const billMatch = bill.billNumber?.toLowerCase().includes(term);
      const orderMatch = bill.orderId?.toString().includes(term);
      return billMatch || orderMatch;
    })
    .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());

  return (
    <div className="admin-page billing-page">
      {/* Page Header */}
      <div className="page-header">
        <div className="header-title">
          <h1>Billing & Payments</h1>
          <p className="subtitle">Review ready orders, generate bills, and process payments</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-secondary btn-icon" onClick={fetchBillingData} title="Refresh List">
            Refresh
          </button>
        </div>
      </div>

      {actionSuccessMsg && (
        <div className="alert alert-success alert-dismissible mb-4">
          <span>{actionSuccessMsg}</span>
          <button className="alert-close" onClick={() => setActionSuccessMsg(null)}>Close</button>
        </div>
      )}

      {/* Tabs & Search Bar */}
      <div className="billing-toolbar mb-4">
        <div className="tab-group">
          <button
            className={`tab-btn ${activeTab === 'READY' ? 'active' : ''}`}
            onClick={() => setActiveTab('READY')}
          >
            Ready for Billing ({readyOrders.length})
          </button>
          <button
            className={`tab-btn ${activeTab === 'COMPLETED' ? 'active' : ''}`}
            onClick={() => setActiveTab('COMPLETED')}
          >
            Bills & Payments History
          </button>
        </div>

        <div className="search-box">
          <input
            type="text"
            className="form-input search-input"
            placeholder={activeTab === 'READY' ? 'Search by Order ID, Table...' : 'Search by Bill #, Order ID...'}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
      </div>

      {/* Content Area */}
      {error && <div className="alert alert-danger mb-4">{error}</div>}

      {loading ? (
        <div className="loading-container py-5 text-center">
          <div className="spinner mb-3"></div>
          <p className="text-muted">Loading billing data...</p>
        </div>
      ) : activeTab === 'READY' ? (
        /* READY ORDERS SECTION */
        filteredReadyOrders.length === 0 ? (
          <div className="empty-state card p-5 text-center">
            <h3>No Orders Ready for Billing</h3>
            <p className="text-muted m-0">
              {searchTerm ? 'No orders match your search criteria.' : 'When kitchen marks orders as READY, they will appear here for billing.'}
            </p>
          </div>
        ) : (
          <div className="orders-grid">
            {filteredReadyOrders.map((order) => (
              <div key={order.id} className="order-card billing-order-card">
                <div className="order-card-header">
                  <div className="order-card-id">
                    <span className="order-number">Order #{order.id}</span>
                    <span className="badge badge-ready ml-2">READY</span>
                  </div>
                  <span className={`badge ${order.orderType === 'DINE_IN' ? 'badge-dinein' : 'badge-takeaway'}`}>
                    {order.orderType === 'DINE_IN' ? `Dine-In (${order.tableName || `Table ${order.tableNumber || ''}`})` : 'Takeaway'}
                  </span>
                </div>

                <div className="order-card-body">
                  <div className="order-card-meta mb-2">
                    <span className="meta-item">{new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                    <span className="meta-item">{order.items?.length || 0} Items</span>
                  </div>

                  <div className="order-items-preview mb-3">
                    {order.items?.slice(0, 3).map((item, idx) => (
                      <div key={idx} className="preview-item-row">
                        <span className="item-qty-badge">{item.quantity}x</span>
                        <span className="item-name-preview">{item.menuItemName}</span>
                        <span className="item-price-preview">{CURRENCY_SYMBOL}{Number(item.subtotal || item.unitPrice * item.quantity).toFixed(2)}</span>
                      </div>
                    ))}
                    {order.items?.length > 3 && (
                      <div className="more-items-text">+ {order.items.length - 3} more items</div>
                    )}
                  </div>

                  <div className="order-total-banner">
                    <span className="total-label">Total Amount</span>
                    <span className="total-value">{CURRENCY_SYMBOL}{Number(order.totalAmount || 0).toFixed(2)}</span>
                  </div>
                </div>

                <div className="order-card-footer">
                  <button
                    className="btn btn-primary btn-block"
                    onClick={() => handleOpenOrder(order)}
                  >
                    Review & Collect Payment
                  </button>
                </div>
              </div>
            ))}
          </div>
        )
      ) : (
        /* COMPLETED BILLS HISTORY SECTION */
        filteredCompletedBills.length === 0 ? (
          <div className="empty-state card p-5 text-center">
            <h3>No Bills Found</h3>
            <p className="text-muted m-0">No generated bills exist matching your search.</p>
          </div>
        ) : (
          <div className="table-responsive card">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Bill #</th>
                  <th>Order #</th>
                  <th>Subtotal</th>
                  <th>Tax</th>
                  <th>Discount</th>
                  <th>Total Amount</th>
                  <th>Generated By</th>
                  <th>Date & Time</th>
                </tr>
              </thead>
              <tbody>
                {filteredCompletedBills.map((bill) => (
                  <tr key={bill.id}>
                    <td><span className="font-semibold text-primary">{bill.billNumber}</span></td>
                    <td>Order #{bill.orderId}</td>
                    <td>{CURRENCY_SYMBOL}{Number(bill.subtotal).toFixed(2)}</td>
                    <td>{CURRENCY_SYMBOL}{Number(bill.taxAmount).toFixed(2)}</td>
                    <td>{CURRENCY_SYMBOL}{Number(bill.discountAmount).toFixed(2)}</td>
                    <td><span className="font-bold text-success">{CURRENCY_SYMBOL}{Number(bill.totalAmount).toFixed(2)}</span></td>
                    <td>{bill.generatedByName || 'Admin'}</td>
                    <td>{new Date(bill.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}

      {/* Bill & Payment Modal */}
      <BillModal
        order={selectedOrder}
        isOpen={isModalOpen}
        onClose={handleModalClose}
        onPaymentSuccess={handlePaymentSuccess}
        onBillGenerated={() => fetchBillingData()}
      />
    </div>
  );
};

export default BillingPage;
