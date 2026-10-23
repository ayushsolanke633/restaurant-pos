import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import useAuth from '../../hooks/useAuth';
import useAutoDismiss from '../../hooks/useAutoDismiss';
import orderService from '../../services/orderService';
import settingsService from '../../services/settingsService';
import KitchenWebSocketClient from '../../services/websocketService';
import OrderModal from '../../components/admin/OrderModal';
import OrderDetailsModal from '../../components/admin/OrderDetailsModal';
import BillModal from '../../components/admin/BillModal';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const OrdersPage = () => {
  const { user } = useAuth();
  const restaurantId = user?.restaurantId;
  const wsClientRef = useRef(null);

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  useAutoDismiss(successMsg, () => setSuccessMsg(''));

  // Filters
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedType, setSelectedType] = useState('ALL');
  const [selectedStatus, setSelectedStatus] = useState('ALL');

  // Modals
  const [isFormModalOpen, setIsFormModalOpen] = useState(false);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
  const [isBillModalOpen, setIsBillModalOpen] = useState(false);
  const [activeOrder, setActiveOrder] = useState(null);
  const [billModalOrder, setBillModalOrder] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);
  const [kitchenEnabled, setKitchenEnabled] = useState(true);

  // Fetch restaurant kitchen mode setting
  useEffect(() => {
    const fetchKitchenSetting = async () => {
      try {
        const settings = await settingsService.getSettings();
        setKitchenEnabled(settings?.kitchenEnabled !== false);
      } catch {
        setKitchenEnabled(true);
      }
    };
    fetchKitchenSetting();
  }, [restaurantId]);

  // Fetch orders from API
  const fetchOrders = useCallback(async (showLoading = true) => {
    if (showLoading) setLoading(true);
    setError('');
    try {
      const data = await orderService.getOrders(selectedStatus, selectedType);
      const list = Array.isArray(data) ? data : [];
      const sortedList = [...list].sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
      setOrders(sortedList);
    } catch (err) {
      if (err.response) {
        if (err.response.status === 403) {
          setError('Access Denied: You do not have permission to view orders.');
        } else {
          setError(err.response.data?.message || 'Failed to load orders.');
        }
      } else {
        setError('Unable to connect to the server. Please check your network connection.');
      }
    } finally {
      if (showLoading) setLoading(false);
    }
  }, [selectedStatus, selectedType]);

  useEffect(() => {
    fetchOrders(true);
  }, [fetchOrders]);

  // WebSocket event handler for real-time order updates with duplicate protection
  const handleOrderEvent = useCallback(
    async (eventPayload) => {
      if (!eventPayload || !eventPayload.orderId) {
        fetchOrders(false);
        return;
      }
      const orderId = Number(eventPayload.orderId);
      try {
        const updatedOrder = await orderService.getOrderById(orderId);
        setOrders((prevOrders) => {
          const index = prevOrders.findIndex((o) => Number(o.id) === orderId);
          let updatedList;
          if (index !== -1) {
            updatedList = [...prevOrders];
            updatedList[index] = updatedOrder;
          } else {
            updatedList = [updatedOrder, ...prevOrders];
          }
          return updatedList.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
        });
        setActiveOrder((prevActive) => {
          if (prevActive && Number(prevActive.id) === orderId) {
            return updatedOrder;
          }
          return prevActive;
        });
        setBillModalOrder((prevBillOrder) => {
          if (prevBillOrder && Number(prevBillOrder.id) === orderId) {
            return updatedOrder;
          }
          return prevBillOrder;
        });
      } catch (err) {
        fetchOrders(false);
      }
    },
    [fetchOrders]
  );

  useEffect(() => {
    if (restaurantId) {
      const wsClient = new KitchenWebSocketClient({
        restaurantId,
        onOrderEvent: handleOrderEvent,
        clientName: 'ADMIN',
      });

      wsClient.connect();
      wsClientRef.current = wsClient;
    }

    return () => {
      if (wsClientRef.current) {
        wsClientRef.current.disconnect();
      }
    };
  }, [restaurantId, handleOrderEvent]);

  // Client-side search filtering
  const filteredOrders = useMemo(() => {
    const list = orders.filter((order) => {
      // Search filter by Order ID, Table ID, or Item Name
      const term = searchTerm.toLowerCase().trim();
      let matchesSearch = true;
      if (term) {
        const matchesId = String(order.id).includes(term);
        const matchesTable =
          (order.tableNumber !== undefined && order.tableNumber !== null && String(order.tableNumber).includes(term)) ||
          (order.tableId ? String(order.tableId).includes(term) : false);
        const matchesItemName =
          Array.isArray(order.items) &&
          order.items.some(
            (i) => i.menuItemName && i.menuItemName.toLowerCase().includes(term)
          );
        matchesSearch = matchesId || matchesTable || matchesItemName;
      }

      // Type filter
      const matchesType =
        selectedType === 'ALL' ||
        (order.orderType && order.orderType.toUpperCase() === selectedType.toUpperCase());

      // Status filter
      const matchesStatus =
        selectedStatus === 'ALL' ||
        (order.status && order.status.toUpperCase() === selectedStatus.toUpperCase());

      return matchesSearch && matchesType && matchesStatus;
    });

    return list.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
  }, [orders, searchTerm, selectedType, selectedStatus]);

  // Open Create Modal
  const handleOpenCreateModal = () => {
    setActiveOrder(null);
    setIsFormModalOpen(true);
  };

  // Open Edit Modal (Allowed only when status === 'NEW')
  const handleOpenEditModal = (order) => {
    setActiveOrder(order);
    setIsFormModalOpen(true);
  };

  // Open Details Modal
  const handleOpenDetailsModal = (order) => {
    setActiveOrder(order);
    setIsDetailsModalOpen(true);
  };

  // Open Bill Modal
  const handleOpenBillModal = (order) => {
    setBillModalOrder(order);
    setIsBillModalOpen(true);
  };

  const handlePaymentSuccess = (paymentResult) => {
    setSuccessMsg(
      `Payment of ${CURRENCY_SYMBOL}${paymentResult.amount} recorded successfully. Order COMPLETED and Table released!`
    );
    if (billModalOrder) {
      const completedId = Number(billModalOrder.id);
      setOrders((prev) =>
        prev.map((o) =>
          Number(o.id) === completedId ? { ...o, status: 'COMPLETED' } : o
        )
      );
      if (activeOrder && Number(activeOrder.id) === completedId) {
        setActiveOrder((prev) => (prev ? { ...prev, status: 'COMPLETED' } : null));
      }
    }
    fetchOrders(false);
  };

  // Handle Form Submit (Create or Update)
  const handleFormSubmit = async (formData) => {
    setIsSubmitting(true);
    setError('');
    setSuccessMsg('');

    try {
      if (activeOrder) {
        const updated = await orderService.updateOrder(activeOrder.id, formData);
        setOrders((prev) =>
          prev.map((o) => (o.id === activeOrder.id ? updated : o))
        );
        setSuccessMsg(`Order #${updated.id} updated successfully.`);
      } else {
        const created = await orderService.createOrder(formData);
        setOrders((prev) => [created, ...prev]);
        setSuccessMsg(`Order #${created.id} created successfully.`);
      }
      setIsFormModalOpen(false);
    } catch (err) {
      if (err.response) {
        if (err.response.status === 403) {
          setError('You do not have permission to perform this action.');
        } else {
          setError(err.response.data?.message || 'Failed to save order.');
        }
      } else {
        setError('Unable to connect to server. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Cancel Order (Allowed when status === 'NEW' or 'ACCEPTED')
  const handleCancelOrder = async (order) => {
    if (
      !window.confirm(
        `Are you sure you want to CANCEL Order #${order.id}?`
      )
    ) {
      return;
    }

    setActionLoadingId(order.id);
    setError('');
    setSuccessMsg('');

    try {
      const cancelled = await orderService.cancelOrder(order.id);
      setOrders((prev) =>
        prev.map((o) => (o.id === order.id ? cancelled : o))
      );
      setSuccessMsg(`Order #${cancelled.id} has been CANCELLED.`);
    } catch (err) {
      if (err.response) {
        setError(err.response.data?.message || 'Failed to cancel order.');
      } else {
        setError('Unable to connect to server. Please try again.');
      }
    } finally {
      setActionLoadingId(null);
    }
  };

  // Format date helper
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

  // Status badge styling helper
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

  // Helper summary of items in row
  const getItemsSummary = (items) => {
    if (!Array.isArray(items) || items.length === 0) return 'No items';
    const names = items.map((i) => `${i.quantity}x ${i.menuItemName || 'Item'}`);
    if (names.length <= 2) return names.join(', ');
    return `${names.slice(0, 2).join(', ')} +${names.length - 2} more`;
  };

  return (
    <div className="admin-page-container">
      {/* Header Banner */}
      <div className="page-header-banner">
        <div className="header-text">
          <h1 className="page-title">Order Management</h1>
          <p className="page-subtitle">
            Create, view, edit, and manage customer orders
          </p>
        </div>
        <button className="btn btn-primary" onClick={handleOpenCreateModal}>
          Create Order
        </button>
      </div>

      {/* Alerts */}
      {error && (
        <div className="alert alert-danger" role="alert">
          <span>{error}</span>
          <button
            type="button"
            className="alert-dismiss"
            onClick={() => setError('')}
          >
            Close
          </button>
        </div>
      )}

      {successMsg && (
        <div className="alert alert-success" role="alert">
          <span>{successMsg}</span>
          <button
            type="button"
            className="alert-dismiss"
            onClick={() => setSuccessMsg('')}
          >
            Close
          </button>
        </div>
      )}

      {/* Controls Bar: Search & Filters */}
      <div className="card controls-card">
        <div className="controls-row">
          <div className="search-box">
            <input
              type="text"
              className="form-input search-input"
              placeholder="Search by Order ID, Table #, or Item..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{ paddingLeft: '1rem' }}
            />
            {searchTerm && (
              <button
                type="button"
                className="btn-clear-search"
                onClick={() => setSearchTerm('')}
              >
                Clear
              </button>
            )}
          </div>

          <div className="filters-group">
            {/* Order Type Filter */}
            <select
              className="form-input filter-select"
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value)}
            >
              <option value="ALL">All Types</option>
              <option value="DINE_IN">Dine In Only</option>
              <option value="TAKEAWAY">Takeaway Only</option>
            </select>

            {/* Status Filter */}
            <select
              className="form-input filter-select"
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="NEW">NEW</option>
              <option value="ACCEPTED">ACCEPTED</option>
              <option value="PREPARING">PREPARING</option>
              <option value="READY">READY</option>
              <option value="COMPLETED">COMPLETED</option>
              <option value="CANCELLED">CANCELLED</option>
            </select>
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      {loading ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading orders...</p>
        </div>
      ) : filteredOrders.length === 0 ? (
        <div className="card empty-state-card">
          <h3>No Orders Found</h3>
          <p>
            {searchTerm || selectedType !== 'ALL' || selectedStatus !== 'ALL'
              ? 'No orders match your search or filter criteria. Try resetting filters.'
              : 'Create your first order to begin tracking operations.'}
          </p>
          {searchTerm || selectedType !== 'ALL' || selectedStatus !== 'ALL' ? (
            <button
              className="btn btn-secondary"
              onClick={() => {
                setSearchTerm('');
                setSelectedType('ALL');
                setSelectedStatus('ALL');
              }}
            >
              Reset Filters
            </button>
          ) : (
            <button className="btn btn-primary" onClick={handleOpenCreateModal}>
              Create First Order
            </button>
          )}
        </div>
      ) : (
        <div className="card table-card">
          <div className="table-header-info">
            <span className="table-count">
              Showing <strong>{filteredOrders.length}</strong> of {orders.length} orders
            </span>
          </div>
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Type</th>
                  <th>Table</th>
                  <th>Status</th>
                  <th>Items Summary</th>
                  <th>Total Amount</th>
                  <th>Time</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredOrders.map((ord) => {
                  const statusUpper = (ord.status || '').toUpperCase();
                  const isNew = statusUpper === 'NEW';
                  const isCancelable = isNew || statusUpper === 'ACCEPTED';
                  const isReady = statusUpper === 'READY';
                  const isCompleted = statusUpper === 'COMPLETED';

                  return (
                    <tr key={ord.id}>
                      <td className="font-weight-bold">#{ord.id}</td>
                      <td>{renderTypeBadge(ord.orderType)}</td>
                      <td>
                        {ord.orderType === 'DINE_IN'
                          ? (ord.tableNumber !== undefined && ord.tableNumber !== null
                              ? `Table ${ord.tableNumber}`
                              : ord.tableId
                              ? `Table ${ord.tableId}`
                              : '—')
                          : '—'}
                      </td>
                      <td>{renderStatusBadge(ord.status)}</td>
                      <td className="text-muted text-truncate">
                        {getItemsSummary(ord.items)}
                      </td>
                      <td className="font-price">
                        {CURRENCY_SYMBOL}
                        {Number(ord.totalAmount || ord.subtotal || 0).toFixed(2)}
                      </td>
                      <td className="text-muted">{formatDate(ord.createdAt)}</td>
                      <td className="text-right">
                        <div className="action-buttons">
                          <button
                            className="btn-action btn-edit"
                            onClick={() => handleOpenDetailsModal(ord)}
                            title="View Receipt Details"
                          >
                            Details
                          </button>

                          {/* Generate Bill button for READY orders or active orders in kitchen-free mode */}
                          {(isReady || (!kitchenEnabled && (isNew || ord.status === 'ACCEPTED' || ord.status === 'PREPARING'))) && (
                            <button
                              className="btn-action btn-activate"
                              onClick={() => handleOpenBillModal(ord)}
                              title="Generate Bill"
                            >
                              Generate Bill
                            </button>
                          )}

                          {/* View Bill button for COMPLETED orders */}
                          {isCompleted && (
                            <button
                              className="btn-action btn-edit"
                              onClick={() => handleOpenBillModal(ord)}
                              title="View Bill / Receipt"
                            >
                              View Bill
                            </button>
                          )}

                          {/* Edit action allowed ONLY when status === 'NEW' */}
                          {isNew && (
                            <button
                              className="btn-action btn-edit"
                              onClick={() => handleOpenEditModal(ord)}
                              title="Edit Order"
                              disabled={actionLoadingId === ord.id}
                            >
                              Edit
                            </button>
                          )}

                          {/* Cancel action allowed ONLY when status === 'NEW' or 'ACCEPTED' */}
                          {isCancelable && (
                            <button
                              className="btn-action btn-deactivate"
                              onClick={() => handleCancelOrder(ord)}
                              title="Cancel Order"
                              disabled={actionLoadingId === ord.id}
                            >
                              {actionLoadingId === ord.id ? '...' : 'Cancel'}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Form Modal (Create / Edit) */}
      <OrderModal
        isOpen={isFormModalOpen}
        onClose={() => setIsFormModalOpen(false)}
        onSubmit={handleFormSubmit}
        initialData={activeOrder}
        isSubmitting={isSubmitting}
      />

      {/* Details Receipt Modal */}
      <OrderDetailsModal
        isOpen={isDetailsModalOpen}
        onClose={() => setIsDetailsModalOpen(false)}
        order={activeOrder}
        onGenerateBill={(order) => {
          setIsDetailsModalOpen(false);
          handleOpenBillModal(order);
        }}
      />

      {/* Thermal Receipt & Payment Modal */}
      <BillModal
        isOpen={isBillModalOpen}
        onClose={() => {
          setIsBillModalOpen(false);
          setBillModalOrder(null);
        }}
        order={billModalOrder}
        onPaymentSuccess={handlePaymentSuccess}
        onBillGenerated={() => fetchOrders(false)}
      />
    </div>
  );
};

export default OrdersPage;
