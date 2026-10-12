import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import tableService from '../../services/tableService';
import orderService from '../../services/orderService';
import KitchenWebSocketClient from '../../services/websocketService';
import TableModal from '../../components/admin/TableModal';
import OrderDetailsModal from '../../components/admin/OrderDetailsModal';
import BillModal from '../../components/admin/BillModal';
import useAuth from '../../hooks/useAuth';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const TablesPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const restaurantId = user?.restaurantId || 1;

  const [tables, setTables] = useState([]);
  const [activeOrders, setActiveOrders] = useState({}); // tableId -> order
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [wsStatus, setWsStatus] = useState('DISCONNECTED');

  // Filter & Search states
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('ALL');

  // Table Modal states (Add / Edit Table)
  const [isTableModalOpen, setIsTableModalOpen] = useState(false);
  const [editingTable, setEditingTable] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Order Details & Bill Modal states (Occupied Table click & Billing)
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [isOrderModalOpen, setIsOrderModalOpen] = useState(false);
  const [isBillModalOpen, setIsBillModalOpen] = useState(false);

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

      // Map active orders (NEW, ACCEPTED, PREPARING, READY) by tableId
      const activeMap = {};
      if (Array.isArray(dineInOrders)) {
        const activeStatuses = ['NEW', 'ACCEPTED', 'PREPARING', 'READY'];
        dineInOrders.forEach((ord) => {
          if (ord.tableId && activeStatuses.includes((ord.status || '').toUpperCase())) {
            // Keep the latest order for each table
            if (!activeMap[ord.tableId] || new Date(ord.createdAt) > new Date(activeMap[ord.tableId].createdAt)) {
              activeMap[ord.tableId] = ord;
            }
          }
        });
      }
      setActiveOrders(activeMap);
    } catch (err) {
      if (err.response) {
        if (err.response.status === 403) {
          setError('Access Denied: You do not have permission to view restaurant tables.');
        } else {
          setError(err.response.data?.message || 'Failed to load restaurant tables.');
        }
      } else {
        setError('Unable to connect to the server. Please check your network connection.');
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
      // Re-fetch state seamlessly from REST API to maintain absolute source of truth
      fetchTablesAndOrders(false);
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
    e.stopPropagation(); // Prevent opening order modal
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
      if (err.response) {
        if (err.response.status === 403) {
          setError('You do not have permission to perform this action.');
        } else {
          setError(err.response.data?.message || 'Failed to save table.');
        }
      } else {
        setError('Unable to connect to server. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // Occupied Table click handler -> View Active Order Details
  const handleTableCardClick = async (table) => {
    const status = (table.status || '').toUpperCase();
    if (status === 'OCCUPIED' || status === 'BILLING') {
      // Check if we already have the active order in state
      let activeOrd = activeOrders[table.id];
      if (!activeOrd) {
        try {
          activeOrd = await orderService.getActiveOrderByTable(table.id);
        } catch (err) {
          console.warn('Could not fetch active order for table', table.id, err);
        }
      }

      if (activeOrd) {
        // Ensure table number is present for display
        activeOrd.tableName = `Table ${table.tableNumber}`;
        setSelectedOrder(activeOrd);
        setIsOrderModalOpen(true);
      } else {
        // Fallback if no order record found
        setError(`No active order records found for Table ${table.tableNumber}.`);
      }
    } else {
      // Available Table: Open Edit modal or allow editing
      handleOpenEditModal({ stopPropagation: () => {} }, table);
    }
  };

  const handleGenerateBill = (order) => {
    setIsOrderModalOpen(false);
    setSelectedOrder(order);
    setIsBillModalOpen(true);
  };

  const handlePaymentSuccess = () => {
    setSuccessMsg('Payment recorded successfully! Order completed and Table released.');
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
            Real-time floor layout view and active dine-in table orders
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

            return (
              <div
                key={table.id}
                className={`pos-table-card status-${status.toLowerCase()}`}
                onClick={() => handleTableCardClick(table)}
                title={isOccupied ? `Table ${table.tableNumber} - Click to view active order` : `Table ${table.tableNumber} - Click to edit`}
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

                <div className="pos-card-footer">
                  {renderStatusBadge(table.status)}
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

      {/* Occupied Table Order Details Modal */}
      <OrderDetailsModal
        isOpen={isOrderModalOpen}
        onClose={() => {
          setIsOrderModalOpen(false);
          setSelectedOrder(null);
        }}
        order={selectedOrder}
        onGenerateBill={handleGenerateBill}
      />

      {/* Thermal Receipt & Payment Modal */}
      <BillModal
        isOpen={isBillModalOpen}
        onClose={() => {
          setIsBillModalOpen(false);
        }}
        order={selectedOrder}
        onPaymentSuccess={handlePaymentSuccess}
      />
    </div>
  );
};

export default TablesPage;
