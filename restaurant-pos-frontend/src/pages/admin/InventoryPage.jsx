import React, { useState, useEffect, useCallback } from 'react';
import inventoryService from '../../services/inventoryService';
import InventoryModal from '../../components/admin/InventoryModal';
import InventoryTransactionModal from '../../components/admin/InventoryTransactionModal';
import InventoryHistoryModal from '../../components/admin/InventoryHistoryModal';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const InventoryPage = () => {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Filters & Search
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL', 'ACTIVE', 'INACTIVE'
  const [stockFilter, setStockFilter] = useState('ALL'); // 'ALL', 'LOW_STOCK', 'NORMAL'

  // Modals state
  const [isItemModalOpen, setIsItemModalOpen] = useState(false);
  const [selectedItemForEdit, setSelectedItemForEdit] = useState(null);

  const [isTxModalOpen, setIsTxModalOpen] = useState(false);
  const [selectedItemForTx, setSelectedItemForTx] = useState(null);

  const [isHistoryModalOpen, setIsHistoryModalOpen] = useState(false);
  const [selectedItemForHistory, setSelectedItemForHistory] = useState(null); // null = all items history

  const fetchInventoryData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await inventoryService.getAllInventoryItems();
      setItems(data || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to fetch inventory items');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchInventoryData();
  }, [fetchInventoryData]);

  const handleAddItemClick = () => {
    setSelectedItemForEdit(null);
    setIsItemModalOpen(true);
  };

  const handleEditItemClick = (item) => {
    setSelectedItemForEdit(item);
    setIsItemModalOpen(true);
  };

  const handleTxClick = (item) => {
    setSelectedItemForTx(item);
    setIsTxModalOpen(true);
  };

  const handleHistoryClick = (item = null) => {
    setSelectedItemForHistory(item);
    setIsHistoryModalOpen(true);
  };

  const handleActivate = async (id) => {
    setError('');
    try {
      await inventoryService.activateInventoryItem(id);
      setSuccessMsg('Inventory item activated successfully.');
      fetchInventoryData();
      setTimeout(() => setSuccessMsg(''), 4000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to activate inventory item');
    }
  };

  const handleDeactivate = async (id) => {
    setError('');
    try {
      await inventoryService.deactivateInventoryItem(id);
      setSuccessMsg('Inventory item deactivated successfully.');
      fetchInventoryData();
      setTimeout(() => setSuccessMsg(''), 4000);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to deactivate inventory item');
    }
  };

  // Filter items by search, status, and stock level
  const filteredItems = items.filter((item) => {
    // 1. Search (name)
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      if (!item.name?.toLowerCase().includes(term)) return false;
    }

    // 2. Status filter
    if (statusFilter === 'ACTIVE' && !item.isActive) return false;
    if (statusFilter === 'INACTIVE' && item.isActive) return false;

    // 3. Stock filter
    if (stockFilter === 'LOW_STOCK' && !item.lowStock) return false;
    if (stockFilter === 'NORMAL' && item.lowStock) return false;

    return true;
  });

  // Calculate Summary Metrics
  const totalItemsCount = items.length;
  const activeItemsCount = items.filter((i) => i.isActive).length;
  const lowStockCount = items.filter((i) => i.lowStock && i.isActive).length;

  return (
    <div className="admin-page inventory-page">
      {/* Page Header */}
      <div className="page-header">
        <div className="header-title">
          <h1>Inventory Management</h1>
          <p className="subtitle">Track raw materials, stock levels, reorder thresholds, and manual inventory movements</p>
        </div>
        <div className="header-actions d-flex gap-2">
          <button className="btn btn-secondary" onClick={() => handleHistoryClick(null)}>
            Audit History
          </button>
          <button className="btn btn-primary" onClick={handleAddItemClick}>
            Add Inventory Item
          </button>
        </div>
      </div>

      {successMsg && (
        <div className="alert alert-success alert-dismissible mb-4">
          <span>{successMsg}</span>
          <button className="alert-close" onClick={() => setSuccessMsg('')}>Close</button>
        </div>
      )}

      {error && <div className="alert alert-danger mb-4">{error}</div>}

      {/* Summary Cards */}
      <div className="metrics-grid mb-4">
        <div className="metric-card card">
          <div className="metric-header">
            <span className="metric-title">Total Items</span>
          </div>
          <div className="metric-value">{totalItemsCount}</div>
        </div>

        <div className="metric-card card">
          <div className="metric-header">
            <span className="metric-title">Active Items</span>
          </div>
          <div className="metric-value text-success">{activeItemsCount}</div>
        </div>

        <div className="metric-card card">
          <div className="metric-header">
            <span className="metric-title">Low Stock Alert</span>
          </div>
          <div className="metric-value text-warning">{lowStockCount}</div>
        </div>
      </div>

      {/* Toolbar / Filters */}
      <div className="billing-toolbar mb-4">
        <div className="d-flex gap-2 flex-wrap flex-1">
          <div className="search-box">
            <input
              type="text"
              className="form-input search-input"
              placeholder="Search inventory items by name..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{ paddingLeft: '1rem' }}
            />
          </div>

          <div className="filter-group d-flex gap-2">
            <select
              className="form-input filter-select"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="ACTIVE">Active Only</option>
              <option value="INACTIVE">Inactive Only</option>
            </select>

            <select
              className="form-input filter-select"
              value={stockFilter}
              onChange={(e) => setStockFilter(e.target.value)}
            >
              <option value="ALL">All Stock Levels</option>
              <option value="LOW_STOCK">Low Stock Only</option>
              <option value="NORMAL">Normal Stock</option>
            </select>
          </div>
        </div>

        <button className="btn btn-secondary btn-icon ml-auto" onClick={fetchInventoryData} title="Refresh Inventory List">
          Refresh
        </button>
      </div>

      {/* Inventory Data Table */}
      {loading ? (
        <div className="loading-container py-5 text-center">
          <div className="spinner mb-3"></div>
          <p className="text-muted">Loading inventory stock records...</p>
        </div>
      ) : filteredItems.length === 0 ? (
        <div className="empty-state card p-5 text-center">
          <h3>No Inventory Items Found</h3>
          <p className="text-muted m-0">
            {searchTerm || statusFilter !== 'ALL' || stockFilter !== 'ALL'
              ? 'No inventory items match your search and filter criteria.'
              : 'Click "Add Inventory Item" to register your first ingredient or raw material.'}
          </p>
        </div>
      ) : (
        <div className="table-responsive card">
          <table className="data-table">
            <thead>
              <tr>
                <th>Item Name</th>
                <th>Current Stock</th>
                <th>Unit</th>
                <th>Min Stock Level</th>
                <th>Cost / Unit</th>
                <th>Status</th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredItems.map((item) => {
                const currentQty = Number(item.currentQuantity) || 0;
                const minQty = Number(item.minimumQuantity) || 0;
                const isLow = item.lowStock || currentQty <= minQty;

                return (
                  <tr key={item.id} className={isLow && item.isActive ? 'row-low-stock' : ''}>
                    <td>
                      <div className="d-flex align-items-center gap-2">
                        <span className="font-semibold text-main">{item.name}</span>
                        {isLow && item.isActive && (
                          <span className="badge badge-warning text-xs" title="Stock at or below minimum threshold">
                            Low Stock
                          </span>
                        )}
                      </div>
                    </td>
                    <td>
                      <span className={`font-extrabold text-base ${isLow && item.isActive ? 'text-warning' : 'text-main'}`}>
                        {currentQty.toFixed(3)}
                      </span>
                    </td>
                    <td><span className="text-muted font-medium">{item.unit}</span></td>
                    <td>{minQty.toFixed(3)}</td>
                    <td>{CURRENCY_SYMBOL}{Number(item.costPerUnit || 0).toFixed(2)}</td>
                    <td>
                      <span className={`status-badge ${item.isActive ? 'status-active' : 'status-inactive'}`}>
                        {item.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="text-right">
                      <div className="d-flex gap-2 justify-content-end align-items-center">
                        {item.isActive && (
                          <button
                            className="btn btn-primary btn-sm"
                            onClick={() => handleTxClick(item)}
                            title="Record Purchase, Adjustment, or Wastage"
                          >
                            Stock Action
                          </button>
                        )}

                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => handleEditItemClick(item)}
                          title="Edit Item Details"
                        >
                          Edit
                        </button>

                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={() => handleHistoryClick(item)}
                          title="View Item Transaction History"
                        >
                          History
                        </button>

                        {item.isActive ? (
                          <button
                            className="btn btn-danger-outline btn-sm"
                            onClick={() => handleDeactivate(item.id)}
                            title="Deactivate Inventory Item"
                          >
                            Deactivate
                          </button>
                        ) : (
                          <button
                            className="btn btn-success btn-sm"
                            onClick={() => handleActivate(item.id)}
                            title="Activate Inventory Item"
                          >
                            Activate
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
      )}

      {/* Item Form Modal (Add / Edit) */}
      <InventoryModal
        item={selectedItemForEdit}
        isOpen={isItemModalOpen}
        onClose={() => setIsItemModalOpen(false)}
        onSuccess={fetchInventoryData}
      />

      {/* Stock Transaction Modal */}
      <InventoryTransactionModal
        item={selectedItemForTx}
        isOpen={isTxModalOpen}
        onClose={() => setIsTxModalOpen(false)}
        onSuccess={fetchInventoryData}
      />

      {/* Audit History Modal */}
      <InventoryHistoryModal
        item={selectedItemForHistory}
        isOpen={isHistoryModalOpen}
        onClose={() => setIsHistoryModalOpen(false)}
      />
    </div>
  );
};

export default InventoryPage;
