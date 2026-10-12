import React, { useState, useEffect, useCallback, useRef } from 'react';
import useAuth from '../../hooks/useAuth';
import kitchenService from '../../services/kitchenService';
import settingsService from '../../services/settingsService';
import KitchenWebSocketClient from '../../services/websocketService';
import KitchenOrderCard from '../../components/kitchen/KitchenOrderCard';
import CombinedDishesPanel from '../../components/kitchen/CombinedDishesPanel';

export const KitchenKdsPage = () => {
  const { user, role } = useAuth();
  const isAdmin = role === 'ADMIN';
  const restaurantId = user?.restaurantId || 1; // Fallback tenant ID

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [_wsStatus, setWsStatus] = useState('CONNECTING');

  const [currentTime, setCurrentTime] = useState(new Date().toLocaleTimeString());

  // Restaurant Settings State
  const [autoAcceptOrders, setAutoAcceptOrders] = useState(false);
  const [settingsLoading, setSettingsLoading] = useState(true);
  const [settingsError, setSettingsError] = useState(false);
  const [isSavingSetting, setIsSavingSetting] = useState(false);

  const wsClientRef = useRef(null);

  // Live clock ticker
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date().toLocaleTimeString());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  // Fetch current restaurant settings for Auto Accept configuration
  const fetchSettings = useCallback(async () => {
    setSettingsLoading(true);
    setSettingsError(false);
    try {
      const data = await settingsService.getSettings();
      if (data && typeof data.autoAcceptOrders === 'boolean') {
        setAutoAcceptOrders(data.autoAcceptOrders);
      }
    } catch (err) {
      console.error('Failed to load restaurant settings in KDS:', err);
      setSettingsError(true);
    } finally {
      setSettingsLoading(false);
    }
  }, []);

  // Fetch initial active orders via REST API
  const fetchActiveOrders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const activeOrders = await kitchenService.getKitchenOrders();
      setOrders(activeOrders || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load kitchen orders');
    } finally {
      setLoading(false);
    }
  }, []);

  // Combined refresh handler for queue and settings
  const handleRefreshQueue = () => {
    fetchActiveOrders();
    fetchSettings();
  };

  // Handle single order update or fetch when WebSocket event arrives
  const handleOrderEvent = useCallback(async (event) => {
    if (!event || !event.orderId) return;

    console.log(`[KDS WS] received event=`, event);
    console.log(`[KDS WS] eventType=${event.eventType || 'ORDER_STATUS_CHANGED'} orderId=${event.orderId} status=${event.status}`);

    // If order is completed or cancelled, remove from active KDS board
    if (['COMPLETED', 'CANCELLED'].includes(event.status)) {
      setOrders((prev) => prev.filter((o) => o.orderId !== event.orderId));
      return;
    }

    // Instantly transition order status in local React state so UI card moves immediately
    setOrders((prev) => {
      const existingIdx = prev.findIndex((o) => o.orderId === event.orderId);
      if (existingIdx >= 0) {
        const updated = [...prev];
        updated[existingIdx] = { ...updated[existingIdx], status: event.status };
        return updated;
      } else {
        return [
          ...prev,
          {
            orderId: event.orderId,
            orderType: event.orderType || 'DINE_IN',
            tableId: event.tableId,
            tableNumber: event.tableNumber,
            status: event.status,
            createdAt: event.createdAt,
            updatedAt: event.updatedAt,
            items: [],
          },
        ];
      }
    });

    try {
      // Fetch full authoritative order details (including item details) from backend REST API
      const fullOrder = await kitchenService.getKitchenOrderById(event.orderId);
      if (fullOrder) {
        console.log(`[KDS WS] refreshed order status=${fullOrder.status} orderId=${fullOrder.orderId}`);
        setOrders((prev) => {
          const existingIdx = prev.findIndex((o) => o.orderId === fullOrder.orderId);
          if (existingIdx >= 0) {
            const updated = [...prev];
            updated[existingIdx] = fullOrder;
            return updated;
          } else {
            return [...prev, fullOrder];
          }
        });
      }
    } catch (_err) {
      console.warn(`[KDS WS] Fetching full order details failed for orderId=${event.orderId}`);
    }
  }, []);

  const isMountedRef = useRef(false);

  const handleWsStatusChange = useCallback((status) => {
    setWsStatus(status);
    if (status === 'CONNECTED') {
      if (isMountedRef.current) {
        // Re-reconcile active orders queue on WebSocket reconnection
        fetchActiveOrders();
        fetchSettings();
      } else {
        isMountedRef.current = true;
      }
    }
  }, [fetchActiveOrders, fetchSettings]);

  // Setup WebSocket connection and initial load on mount
  useEffect(() => {
    fetchActiveOrders();
    fetchSettings();

    if (restaurantId) {
      const wsClient = new KitchenWebSocketClient({
        restaurantId,
        onOrderEvent: handleOrderEvent,
        onStatusChange: handleWsStatusChange,
        clientName: 'KITCHEN',
      });

      wsClient.connect();
      wsClientRef.current = wsClient;
    }

    return () => {
      if (wsClientRef.current) {
        wsClientRef.current.disconnect();
      }
    };
  }, [restaurantId, fetchActiveOrders, fetchSettings, handleOrderEvent, handleWsStatusChange]);

  // Handle ADMIN toggling Auto Accept setting directly from KDS header
  const handleToggleAutoAccept = async () => {
    if (!isAdmin || isSavingSetting) return;

    const newValue = !autoAcceptOrders;
    setIsSavingSetting(true);
    try {
      const updated = await settingsService.updateSettings({ autoAcceptOrders: newValue });
      if (updated && typeof updated.autoAcceptOrders === 'boolean') {
        setAutoAcceptOrders(updated.autoAcceptOrders);
      } else {
        setAutoAcceptOrders(newValue);
      }
    } catch (err) {
      console.error('Failed to update auto accept setting:', err);
      alert('Failed to update Auto Accept setting. Please try again.');
    } finally {
      setIsSavingSetting(false);
    }
  };

  // Handle kitchen order status transitions (NEW -> ACCEPTED -> PREPARING -> READY)
  const handleStatusTransition = async (orderId, actionType) => {
    try {
      let updatedOrder = null;
      if (actionType === 'accept') {
        updatedOrder = await kitchenService.acceptOrder(orderId);
      } else if (actionType === 'prepare') {
        updatedOrder = await kitchenService.prepareOrder(orderId);
      } else if (actionType === 'ready') {
        updatedOrder = await kitchenService.readyOrder(orderId);
      }

      if (updatedOrder) {
        setOrders((prev) =>
          prev.map((o) => (o.orderId === orderId ? updatedOrder : o))
        );
      }
    } catch (err) {
      alert(err.response?.data?.message || err.message || `Failed to update order #${orderId}`);
    }
  };

  // Group orders into workflow status columns
  const newOrders = orders.filter((o) => o.status === 'NEW');
  const acceptedOrders = orders.filter((o) => o.status === 'ACCEPTED');
  const preparingOrders = orders.filter((o) => o.status === 'PREPARING');
  const readyOrders = orders.filter((o) => o.status === 'READY');

  // Render KDS Header Control for Auto Accept Orders
  const renderAutoAcceptControl = () => {
    if (settingsLoading) {
      return (
        <div className="auto-accept-indicator loading" title="Loading Auto Accept status">
          <span>Auto Accept...</span>
        </div>
      );
    }

    if (settingsError) {
      return (
        <div className="auto-accept-indicator error" title="Auto Accept status unavailable from server">
          <span>Auto Accept status unavailable</span>
        </div>
      );
    }

    if (isAdmin) {
      return (
        <div className="auto-accept-control-admin">
          <span className="auto-accept-label">Auto Accept Orders</span>
          <button
            type="button"
            className={`auto-accept-toggle-btn ${autoAcceptOrders ? 'active' : 'inactive'}`}
            onClick={handleToggleAutoAccept}
            disabled={isSavingSetting}
            aria-label="Toggle Auto Accept Orders"
            title={autoAcceptOrders ? "Auto Accept is ON (5 min delay)" : "Auto Accept is OFF (Manual acceptance)"}
          >
            <span className="toggle-pill">{autoAcceptOrders ? 'ON' : 'OFF'}</span>
            <span className="toggle-subtext">{autoAcceptOrders ? '5 min' : 'Manual'}</span>
          </button>
        </div>
      );
    }

    // Read-only indicator for Kitchen role
    return (
      <div
        className={`auto-accept-indicator ${autoAcceptOrders ? 'status-on' : 'status-off'}`}
        title={autoAcceptOrders ? "NEW DINE_IN orders are automatically accepted after 5 minutes" : "NEW orders require manual acceptance by kitchen staff"}
      >
        <span className="indicator-title">Auto Accept:</span>
        {autoAcceptOrders ? (
          <span className="indicator-status on">
            ON <small>· 5 min</small>
          </span>
        ) : (
          <span className="indicator-status off">
            OFF <small>· Manual</small>
          </span>
        )}
      </div>
    );
  };

  return (
    <div className="kds-page-container">
      {/* Top KDS Header */}
      <header className="kds-header">
        <div className="kds-header-title">
          <h1>Kitchen Display System (KDS)</h1>
          <span className="kds-subtitle font-mono">{currentTime}</span>
        </div>

        <div className="kds-header-actions">
          {renderAutoAcceptControl()}
          <button className="btn btn-secondary btn-icon" onClick={handleRefreshQueue} title="Refresh Queue">
            Refresh Queue
          </button>
        </div>
      </header>

      {error && <div className="alert alert-danger m-4">{error}</div>}

      {/* Main KDS Columns Grid */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner mb-3"></div>
          <p className="text-muted">Loading kitchen queue...</p>
        </div>
      ) : (
        <div className="kds-columns-grid">
          {/* Column 1: NEW */}
          <div className="kds-column column-new">
            <div className="kds-column-header">
              <span className="col-title">NEW ORDERS</span>
              <span className="col-count-badge count-new">{newOrders.length}</span>
            </div>
            <div className="kds-column-body">
              {newOrders.length === 0 ? (
                <div className="kds-empty-col">No new orders</div>
              ) : (
                newOrders.map((order) => (
                  <KitchenOrderCard
                    key={order.orderId}
                    order={order}
                    onStatusTransition={handleStatusTransition}
                  />
                ))
              )}
            </div>
          </div>

          {/* Column 2: ACCEPTED */}
          <div className="kds-column column-accepted">
            <div className="kds-column-header">
              <span className="col-title">ACCEPTED</span>
              <span className="col-count-badge count-accepted">{acceptedOrders.length}</span>
            </div>
            <div className="kds-column-body">
              {acceptedOrders.length === 0 ? (
                <div className="kds-empty-col">No accepted orders</div>
              ) : (
                acceptedOrders.map((order) => (
                  <KitchenOrderCard
                    key={order.orderId}
                    order={order}
                    onStatusTransition={handleStatusTransition}
                  />
                ))
              )}
            </div>
          </div>

          {/* Column 3: PREPARING */}
          <div className="kds-column column-preparing">
            <div className="kds-column-header">
              <span className="col-title">IN PREPARATION</span>
              <span className="col-count-badge count-preparing">{preparingOrders.length}</span>
            </div>
            <div className="kds-column-body">
              {preparingOrders.length === 0 ? (
                <div className="kds-empty-col">No orders cooking</div>
              ) : (
                preparingOrders.map((order) => (
                  <KitchenOrderCard
                    key={order.orderId}
                    order={order}
                    onStatusTransition={handleStatusTransition}
                  />
                ))
              )}
            </div>
          </div>

          {/* Column 4: READY */}
          <div className="kds-column column-ready">
            <div className="kds-column-header">
              <span className="col-title">READY FOR SERVICE</span>
              <span className="col-count-badge count-ready">{readyOrders.length}</span>
            </div>
            <div className="kds-column-body">
              {readyOrders.length === 0 ? (
                <div className="kds-empty-col">No ready orders</div>
              ) : (
                readyOrders.map((order) => (
                  <KitchenOrderCard
                    key={order.orderId}
                    order={order}
                    onStatusTransition={handleStatusTransition}
                  />
                ))
              )}
            </div>
          </div>

          {/* Column 5: COMBINED DISHES */}
          <CombinedDishesPanel orders={orders} />
        </div>
      )}
    </div>
  );
};

export default KitchenKdsPage;
