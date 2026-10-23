import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import tableService from '../../services/tableService';
import orderService from '../../services/orderService';
import KitchenWebSocketClient from '../../services/websocketService';
import TableModal from '../../components/admin/TableModal';
import OrderModal from '../../components/admin/OrderModal';
import OrderDetailsModal from '../../components/admin/OrderDetailsModal';
import BillModal from '../../components/admin/BillModal';
import AvailableTableModal from '../../components/admin/AvailableTableModal';
import TableActiveOrdersModal from '../../components/admin/TableActiveOrdersModal';
import AddItemsModal from '../../components/admin/AddItemsModal';
import useAuth from '../../hooks/useAuth';
import useAutoDismiss from '../../hooks/useAutoDismiss';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const TablesPage = () => {
  const { user } = useAuth();
  const restaurantId = user?.restaurantId;

  const [tables, setTables] = useState([]);
  const [activeOrdersByTable, setActiveOrdersByTable] = useState({}); // tableId -> [orders]
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  useAutoDismiss(successMsg, () => setSuccessMsg(''));
  const [_wsStatus, setWsStatus] = useState('DISCONNECTED');

  // Filter & Search states
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('ALL');

  // Table Modal states (Add / Edit Table Capacity)
  const [isTableModalOpen, setIsTableModalOpen] = useState(false);
  const [editingTable, setEditingTable] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Available Table Modal state (Clicking an available table)
  const [selectedAvailableTable, setSelectedAvailableTable] = useState(null);
  const [isAvailableModalOpen, setIsAvailableModalOpen] = useState(false);

  // Multiple Active Orders Modal state (Legacy table with > 1 active order)
  const [multipleOrdersTable, setMultipleOrdersTable] = useState(null);
  const [multipleActiveOrders, setMultipleActiveOrders] = useState([]);
  const [isMultipleOrdersModalOpen, setIsMultipleOrdersModalOpen] = useState(false);

  // Create Order Modal state (Creating dine-in order for a specific table)
  const [orderModalTable, setOrderModalTable] = useState(null);
  const [isAddOrderModalOpen, setIsAddOrderModalOpen] = useState(false);
  const [isCreatingOrder, setIsCreatingOrder] = useState(false);

  // Order Details & Bill Modal states (Occupied Table click & Billing)
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [isOrderModalOpen, setIsOrderModalOpen] = useState(false);
  const [isBillModalOpen, setIsBillModalOpen] = useState(false);

  // Add Items Modal state (Adding incremental items to an existing order)
  const [isAddItemsModalOpen, setIsAddItemsModalOpen] = useState(false);

  const wsClientRef = useRef(null);

  // Fetch tables and their active DINE_IN orders from REST API
  const fetchTablesAndOrders = useCallback(async (showLoading = true) => {
    if (showLoading) setLoading(true);
    setError('');
    try {
      const [tablesData, dineInOrders] = await Promise.all([
        tableService.getTables(),
        orderService.getOrders(null, 'DINE_IN').catch(() => []),
      ]);

      const tableList = Array.isArray(tablesData) ? tablesData : [];
      const sortedTables = [...tableList].sort((a, b) => Number(a.tableNumber) - Number(b.tableNumber));
      setTables(sortedTables);

      // Group active orders (NEW, ACCEPTED, PREPARING, READY) by tableId
      const ordersByTable = {};
      const activeStatuses = ['NEW', 'ACCEPTED', 'PREPARING', 'READY'];

      if (Array.isArray(dineInOrders)) {
        dineInOrders.forEach((ord) => {
          if (ord.tableId && activeStatuses.includes((ord.status || '').toUpperCase())) {
            if (!ordersByTable[ord.tableId]) {
              ordersByTable[ord.tableId] = [];
            }
            ordersByTable[ord.tableId].push(ord);
          }
        });

        // Sort descending by createdAt
        Object.keys(ordersByTable).forEach((tId) => {
          ordersByTable[tId].sort(
            (a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
          );
        });
      }

      setActiveOrdersByTable(ordersByTable);
    } catch (err) {
      console.error('Failed to load tables or orders:', err);
      if (err?.response) {
        if (err.response.status === 403) {
          setError('Access Denied: You do not have permission to view restaurant tables.');
        } else if (err.response.status === 401) {
          setError('Session expired. Please log in again.');
        } else if (err.response.status >= 500) {
          setError('Server error while loading tables. Please try again later.');
        } else {
          setError(err.response.data?.message || 'Failed to load restaurant tables.');
        }
      } else if (err?.code === 'ERR_NETWORK' || err?.request) {
        setError('Unable to connect to the server. Please check your network connection.');
      } else {
        setError(err?.message || 'An unexpected error occurred while loading tables.');
      }
    } finally {
      if (showLoading) setLoading(false);
    }
  }, []);

  // WebSocket event handler for real-time order updates
  const handleOrderEvent = useCallback((event) => {
    if (!event) return;
    // When a DINE_IN order is created, updated, or status changed:
    if (event.orderType === 'DINE_IN' || event.tableId) {
      fetchTablesAndOrders(false);

      // If active order details modal is open and matches this event, update its data
      if (event.orderId) {
        setSelectedOrder((prev) => {
          if (prev && Number(prev.id) === Number(event.orderId)) {
            orderService.getOrderById(event.orderId).then((fresh) => {
              setSelectedOrder(fresh);
            }).catch(() => {});
          }
          return prev;
        });
      }
    }
  }, [fetchTablesAndOrders]);

  // Mount & WebSocket connection setup
  useEffect(() => {
    fetchTablesAndOrders(true);

    if (restaurantId) {
      const wsClient = new KitchenWebSocketClient({
        restaurantId,
        onOrderEvent: handleOrderEvent,
        onStatusChange: (status) => setWsStatus(status),
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
  }, [restaurantId, fetchTablesAndOrders, handleOrderEvent]);

  // Client-side search and status filtering
  const filteredTables = useMemo(() => {
    const list = tables.filter((table) => {
      const matchesSearch =
        !searchTerm.trim() ||
        String(table.tableNumber).includes(searchTerm.trim());

      const matchesStatus =
        selectedStatus === 'ALL' ||
        (table.status && table.status.toUpperCase() === selectedStatus.toUpperCase());

      return matchesSearch && matchesStatus;
    });

    return list.sort((a, b) => Number(a.tableNumber) - Number(b.tableNumber));
  }, [tables, searchTerm, selectedStatus]);

  // Modal Handlers for Table CRUD
  const handleOpenAddModal = () => {
    setEditingTable(null);
    setIsTableModalOpen(true);
  };

  const handleOpenEditModal = (e, tbl) => {
    if (e && e.stopPropagation) e.stopPropagation();
    setEditingTable(tbl);
    setIsTableModalOpen(true);
  };

  const handleTableFormSubmit = async (formData) => {
    setIsSubmitting(true);
    setError('');
    setSuccessMsg('');

    try {
      if (editingTable) {
        const updated = await tableService.updateTable(editingTable.id, formData);
        setTables((prev) => {
          const next = prev.map((t) => (t.id === editingTable.id ? updated : t));
          return next.sort((a, b) => Number(a.tableNumber) - Number(b.tableNumber));
        });
        setSuccessMsg(`Table ${updated.tableNumber} updated successfully.`);
      } else {
        const created = await tableService.createTable(formData);
        setTables((prev) => {
          const next = [...prev, created];
          return next.sort((a, b) => Number(a.tableNumber) - Number(b.tableNumber));
        });
        setSuccessMsg(`Table ${created.tableNumber} created successfully.`);
      }
      setIsTableModalOpen(false);
    } catch (err) {
      console.error('Failed to save table:', err);
      if (err?.response) {
        if (err.response.status === 403) {
          setError('You do not have permission to perform this action.');
        } else if (err.response.status >= 500) {
          setError('Server error while saving table. Please try again later.');
        } else {
          setError(err.response.data?.message || 'Failed to save table.');
        }
      } else if (err?.code === 'ERR_NETWORK' || err?.request) {
        setError('Unable to connect to the server. Please check your network connection.');
      } else {
        setError(err?.message || 'Failed to save table.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // Table Card Click / Keyboard Selection Handler
  const handleTableCardClick = async (table) => {
    setError('');
    const status = (table.status || '').toUpperCase();

    if (status === 'AVAILABLE') {
      // Available Table: Open details modal with Add Order & Edit options
      setSelectedAvailableTable(table);
      setIsAvailableModalOpen(true);
    } else if (status === 'OCCUPIED' || status === 'BILLING') {
      // Occupied / Billing Table: Retrieve active orders for this table
      let tableOrders = activeOrdersByTable[table.id] || [];

      if (tableOrders.length === 0) {
        try {
          const fetched = await orderService.getActiveOrderByTable(table.id);
          if (fetched) {
            tableOrders = [fetched];
          }
        } catch (err) {
          console.warn('Could not fetch active order for table', table.id, err);
        }
      }

      if (tableOrders.length === 0) {
        setError(`No active order records found for Table ${table.tableNumber}.`);
        return;
      }

      if (tableOrders.length > 1) {
        // Multiple active orders exist for this table in legacy data: require Admin to select intended order
        setMultipleOrdersTable(table);
        setMultipleActiveOrders(tableOrders);
        setIsMultipleOrdersModalOpen(true);
      } else {
        // Exactly one active order: open OrderDetailsModal directly
        const singleOrder = {
          ...tableOrders[0],
          tableName: `Table ${table.tableNumber}`,
          tableNumber: table.tableNumber,
          tableStatus: status,
        };
        setSelectedOrder(singleOrder);
        setMultipleOrdersTable(table);
        setMultipleActiveOrders([]);
        setIsOrderModalOpen(true);
      }
    } else {
      // Other statuses (RESERVED, CLEANING): Open edit modal
      handleOpenEditModal({ stopPropagation: () => {} }, table);
    }
  };

  // Handler from AvailableTableModal -> Open Add Order workflow
  const handleStartAddOrder = (table) => {
    setIsAvailableModalOpen(false);
    setOrderModalTable(table);
    setIsAddOrderModalOpen(true);
  };

  // Handler for creating a new DINE_IN order from table
  const handleCreateOrderSubmit = async (formData) => {
    setIsCreatingOrder(true);
    setError('');
    setSuccessMsg('');

    try {
      const created = await orderService.createOrder(formData);
      setIsAddOrderModalOpen(false);
      setSuccessMsg(
        `Order #${created.id} created successfully for Table ${orderModalTable?.tableNumber || created.tableNumber || ''}.`
      );
      fetchTablesAndOrders(false);
    } catch (err) {
      console.error('Failed to create order:', err);
      if (err?.response) {
        if (err.response.status === 403) {
          setError('You do not have permission to create this order.');
        } else if (err.response.status >= 500) {
          setError('Server error while creating order. Please try again later.');
        } else {
          setError(err.response.data?.message || 'Failed to create order.');
        }
      } else if (err?.code === 'ERR_NETWORK' || err?.request) {
        setError('Unable to connect to the server. Please check your network connection.');
      } else {
        setError(err?.message || 'Failed to create order.');
      }
    } finally {
      setIsCreatingOrder(false);
    }
  };

  // Handler from TableActiveOrdersModal -> Admin selects an intended order
  const handleSelectActiveOrder = (order) => {
    setIsMultipleOrdersModalOpen(false);
    const orderWithTable = {
      ...order,
      tableName: multipleOrdersTable ? `Table ${multipleOrdersTable.tableNumber}` : `Table ${order.tableNumber}`,
      tableNumber: multipleOrdersTable?.tableNumber || order.tableNumber,
      tableStatus: multipleOrdersTable?.status,
    };
    setSelectedOrder(orderWithTable);
    setIsOrderModalOpen(true);
  };

  // Handler to navigate back to multiple active orders list from OrderDetailsModal
  const handleBackToOrdersList = () => {
    setIsOrderModalOpen(false);
    setIsMultipleOrdersModalOpen(true);
  };

  // Handler to open Add Items modal
  const handleOpenAddItems = (order) => {
    setSelectedOrder(order);
    setIsAddItemsModalOpen(true);
  };

  // Handler when items are successfully added to an existing order
  const handleItemsAddedSuccess = (updatedOrder) => {
    const enriched = {
      ...updatedOrder,
      tableName: selectedOrder?.tableName || `Table ${updatedOrder.tableNumber}`,
      tableNumber: selectedOrder?.tableNumber || updatedOrder.tableNumber,
      tableStatus: selectedOrder?.tableStatus,
    };
    setSelectedOrder(enriched);
    setSuccessMsg(
      `Successfully added items to Order #${updatedOrder.id}. New Total: ${CURRENCY_SYMBOL}${Number(updatedOrder.totalAmount).toFixed(2)}`
    );
    fetchTablesAndOrders(false);
  };

  // Handler for Admin Order Cancellation directly from OrderDetailsModal
  const handleCancelOrder = async (order) => {
    setError('');
    setSuccessMsg('');
    try {
      const cancelled = await orderService.cancelOrder(order.id);
      setIsOrderModalOpen(false);
      setSelectedOrder(null);
      setSuccessMsg(`Order #${cancelled.id} has been CANCELLED and table status updated.`);
      fetchTablesAndOrders(false);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to cancel order.';
      setError(msg);
      throw err; // Re-throw so OrderDetailsModal can display error inline if desired
    }
  };

  // Handler for generating a bill
  const handleGenerateBill = (order) => {
    setIsOrderModalOpen(false);
    setSelectedOrder(order);
    setIsBillModalOpen(true);
  };

  const handleBillGenerated = useCallback((newBill, billedOrder) => {
    if (billedOrder?.tableId) {
      setTables((prevTables) =>
        prevTables.map((t) =>
          t.id === billedOrder.tableId ? { ...t, status: 'BILLING' } : t
        )
      );
    }
    fetchTablesAndOrders(false);
  }, [fetchTablesAndOrders]);

  const handlePaymentSuccess = () => {
    setSuccessMsg('Payment recorded successfully! Order completed and Table released.');
    setSelectedOrder(null);
    fetchTablesAndOrders(false);
  };

  // Metric counts
  const availableCount = tables.filter((t) => (t.status || '').toUpperCase() === 'AVAILABLE').length;
  const occupiedCount = tables.filter((t) => (t.status || '').toUpperCase() === 'OCCUPIED').length;
  const billingCount = tables.filter((t) => (t.status || '').toUpperCase() === 'BILLING').length;

  const renderStatusBadge = (status) => {
    const s = (status || '').toUpperCase();
    let badgeClass = 'status-badge-available';
    let label = 'Available';

    if (s === 'OCCUPIED') {
      badgeClass = 'status-badge-occupied';
      label = 'Occupied';
    } else if (s === 'BILLING') {
      badgeClass = 'status-badge-billing';
      label = 'Billing';
    } else if (s === 'RESERVED') {
      badgeClass = 'status-badge-reserved';
      label = 'Reserved';
    } else if (s === 'CLEANING') {
      badgeClass = 'status-badge-cleaning';
      label = 'Cleaning';
    }

    return <span className={`pos-table-badge ${badgeClass}`}>{label}</span>;
  };

  return (
    <div className="admin-page-container pos-tables-page">
      {/* Header Banner */}
      <div className="page-header-banner">
        <div className="header-text">
          <h1 className="page-title">POS Table Management</h1>
          <p className="page-subtitle">
            Manage floor layout, create dine-in orders, add items, and track live active orders
          </p>
        </div>
        <div className="header-actions-group">
          <button className="btn btn-primary" onClick={handleOpenAddModal}>
            Add Table
          </button>
        </div>
      </div>

      {/* Summary Metrics */}
      <div className="grid grid-4 table-summary-grid">
        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Total Tables</span>
          </div>
          <div className="metric-value">{tables.length}</div>
        </div>

        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Available</span>
          </div>
          <div className="metric-value">{availableCount}</div>
        </div>

        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Occupied</span>
          </div>
          <div className="metric-value">{occupiedCount}</div>
        </div>

        <div className="card metric-card">
          <div className="metric-header">
            <span className="metric-title">Billing</span>
          </div>
          <div className="metric-value">{billingCount}</div>
        </div>
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
      <div className="card controls-card mb-4">
        <div className="controls-row">
          <div className="search-box">
            <input
              type="text"
              className="form-input search-input"
              placeholder="Search by table number..."
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
            <select
              className="form-input filter-select"
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="AVAILABLE">Available</option>
              <option value="OCCUPIED">Occupied</option>
              <option value="BILLING">Billing</option>
            </select>
          </div>
        </div>
      </div>

      {/* Main POS Table Grid */}
      {loading ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading table grid...</p>
        </div>
      ) : filteredTables.length === 0 ? (
        <div className="card empty-state-card">
          <h3>No Tables Found</h3>
          <p>
            {searchTerm || selectedStatus !== 'ALL'
              ? 'No tables match your search or filter criteria. Try resetting filters.'
              : 'Add your first restaurant table to configure seating.'}
          </p>
          {searchTerm || selectedStatus !== 'ALL' ? (
            <button
              className="btn btn-secondary"
              onClick={() => {
                setSearchTerm('');
                setSelectedStatus('ALL');
              }}
            >
              Reset Filters
            </button>
          ) : (
            <button className="btn btn-primary" onClick={handleOpenAddModal}>
              Add First Table
            </button>
          )}
        </div>
      ) : (
        <div className="pos-tables-grid">
          {filteredTables.map((table) => {
            const status = (table.status || '').toUpperCase();
            const isOccupied = status === 'OCCUPIED' || status === 'BILLING';
            const tableOrders = activeOrdersByTable[table.id] || [];
            const hasMultiple = tableOrders.length > 1;

            return (
              <div
                key={table.id}
                role="button"
                tabIndex={0}
                aria-label={`Table ${table.tableNumber}, ${table.capacity} Seats, Status ${status}`}
                className={`pos-table-card status-${status.toLowerCase()}`}
                onClick={() => handleTableCardClick(table)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    handleTableCardClick(table);
                  }
                }}
                title={
                  isOccupied
                    ? hasMultiple
                      ? `Table ${table.tableNumber} - ${tableOrders.length} Active Orders. Click to select order.`
                      : `Table ${table.tableNumber} - Occupied. Click to manage active order.`
                    : `Table ${table.tableNumber} - Available. Click to add order or edit.`
                }
              >
                <div className="pos-card-header">
                  <div className="table-number-title">
                    <span className="table-num-label">Table</span>
                    <span className="table-num-value">{table.tableNumber}</span>
                    <span className="pos-card-capacity">{table.capacity} Seats</span>
                  </div>

                  <button
                    type="button"
                    className="btn-edit-table"
                    title="Edit Table Capacity"
                    onClick={(e) => handleOpenEditModal(e, table)}
                  >
                    Edit
                  </button>
                </div>

                <div className="pos-card-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  {renderStatusBadge(table.status)}
                  {hasMultiple && (
                    <span
                      className="badge badge-warning"
                      style={{ fontSize: '0.68rem', fontWeight: 700 }}
                      title={`${tableOrders.length} active orders`}
                    >
                      {tableOrders.length} Orders
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Add / Edit Table Modal */}
      <TableModal
        isOpen={isTableModalOpen}
        onClose={() => setIsTableModalOpen(false)}
        onSubmit={handleTableFormSubmit}
        initialData={editingTable}
        isSubmitting={isSubmitting}
      />

      {/* Available Table Action Modal */}
      <AvailableTableModal
        isOpen={isAvailableModalOpen}
        onClose={() => {
          setIsAvailableModalOpen(false);
          setSelectedAvailableTable(null);
        }}
        table={selectedAvailableTable}
        onAddOrder={handleStartAddOrder}
        onEditTable={(tbl) => {
          setIsAvailableModalOpen(false);
          handleOpenEditModal({ stopPropagation: () => {} }, tbl);
        }}
      />

      {/* Multiple Active Orders Selection Modal (Legacy Data Protection) */}
      <TableActiveOrdersModal
        isOpen={isMultipleOrdersModalOpen}
        onClose={() => {
          setIsMultipleOrdersModalOpen(false);
          setMultipleOrdersTable(null);
          setMultipleActiveOrders([]);
        }}
        table={multipleOrdersTable}
        orders={multipleActiveOrders}
        onSelectOrder={handleSelectActiveOrder}
      />

      {/* Create Order Modal (Pre-locked for DINE_IN Table) */}
      <OrderModal
        isOpen={isAddOrderModalOpen}
        onClose={() => {
          setIsAddOrderModalOpen(false);
          setOrderModalTable(null);
        }}
        onSubmit={handleCreateOrderSubmit}
        initialData={null}
        isSubmitting={isCreatingOrder}
        initialTableId={orderModalTable?.id}
        lockTable={true}
      />

      {/* Occupied Table Order Details Modal */}
      <OrderDetailsModal
        isOpen={isOrderModalOpen}
        onClose={() => {
          setIsOrderModalOpen(false);
          setSelectedOrder(null);
          setMultipleOrdersTable(null);
          setMultipleActiveOrders([]);
        }}
        order={selectedOrder}
        onGenerateBill={handleGenerateBill}
        onAddItems={handleOpenAddItems}
        onCancelOrder={handleCancelOrder}
        hasMultipleOrders={multipleActiveOrders.length > 1}
        onBackToOrderList={handleBackToOrdersList}
      />

      {/* Add Items to Order Modal */}
      <AddItemsModal
        isOpen={isAddItemsModalOpen}
        onClose={() => setIsAddItemsModalOpen(false)}
        order={selectedOrder}
        onItemsAdded={handleItemsAddedSuccess}
      />

      {/* Thermal Receipt & Payment Modal */}
      <BillModal
        isOpen={isBillModalOpen}
        onClose={() => {
          setIsBillModalOpen(false);
          setSelectedOrder(null);
        }}
        order={selectedOrder}
        onPaymentSuccess={handlePaymentSuccess}
        onBillGenerated={handleBillGenerated}
      />
    </div>
  );
};

export default TablesPage;
