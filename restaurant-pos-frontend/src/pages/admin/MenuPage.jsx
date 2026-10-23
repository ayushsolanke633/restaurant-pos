import React, { useState, useEffect, useCallback, useMemo } from 'react';
import useAutoDismiss from '../../hooks/useAutoDismiss';
import menuItemService from '../../services/menuItemService';
import categoryService from '../../services/categoryService';
import MenuItemModal from '../../components/admin/MenuItemModal';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const MenuPage = () => {
  const [menuItems, setMenuItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  useAutoDismiss(successMsg, () => setSuccessMsg(''));

  // Search & Filter state
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedAvailability, setSelectedAvailability] = useState('ALL');

  // Modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState(null);

  // Fetch Categories & Menu Items
  const fetchData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [catsData, itemsData] = await Promise.all([
        categoryService.getCategories(),
        menuItemService.getMenuItems(),
      ]);
      setCategories(Array.isArray(catsData) ? catsData : []);
      setMenuItems(Array.isArray(itemsData) ? itemsData : []);
    } catch (err) {
      if (err.response) {
        if (err.response.status === 403) {
          setError('Access Denied: You do not have permission to view menu items.');
        } else {
          setError(err.response.data?.message || 'Failed to load menu data. Please try again.');
        }
      } else {
        setError('Unable to connect to the server. Please check your network connection.');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Client-side search and filtering
  const filteredMenuItems = useMemo(() => {
    return menuItems.filter((item) => {
      // Search filter by name or description
      const matchesSearch =
        !searchTerm.trim() ||
        item.name.toLowerCase().includes(searchTerm.toLowerCase().trim()) ||
        (item.description &&
          item.description.toLowerCase().includes(searchTerm.toLowerCase().trim()));

      // Category filter
      const matchesCategory =
        !selectedCategory || String(item.categoryId) === String(selectedCategory);

      // Availability filter
      let matchesAvailability = true;
      if (selectedAvailability === 'AVAILABLE') {
        matchesAvailability = item.isAvailable === true;
      } else if (selectedAvailability === 'UNAVAILABLE') {
        matchesAvailability = item.isAvailable === false;
      }

      return matchesSearch && matchesCategory && matchesAvailability;
    });
  }, [menuItems, searchTerm, selectedCategory, selectedAvailability]);

  // Handle open add modal
  const handleOpenAddModal = () => {
    setEditingItem(null);
    setIsModalOpen(true);
  };

  // Handle open edit modal
  const handleOpenEditModal = (item) => {
    setEditingItem(item);
    setIsModalOpen(true);
  };

  // Handle Form Submit (Create or Edit)
  const handleFormSubmit = async (formData) => {
    setIsSubmitting(true);
    setError('');
    setSuccessMsg('');

    try {
      if (editingItem) {
        const updated = await menuItemService.updateMenuItem(editingItem.id, formData);
        setMenuItems((prev) =>
          prev.map((item) => (item.id === editingItem.id ? updated : item))
        );
        setSuccessMsg(`Menu item "${updated.name}" updated successfully.`);
      } else {
        const created = await menuItemService.createMenuItem(formData);
        setMenuItems((prev) => [...prev, created]);
        setSuccessMsg(`Menu item "${created.name}" created successfully.`);
      }
      setIsModalOpen(false);
    } catch (err) {
      if (err.response) {
        if (err.response.status === 403) {
          setError('You do not have permission to perform this action.');
        } else {
          setError(err.response.data?.message || 'Failed to save menu item.');
        }
      } else {
        setError('Unable to connect to server. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Toggle Availability
  const handleToggleAvailability = async (item) => {
    const isCurrentlyAvailable = item.isAvailable;
    const actionName = isCurrentlyAvailable ? 'make unavailable' : 'make available';

    if (
      !window.confirm(
        `Are you sure you want to ${actionName} "${item.name}"?`
      )
    ) {
      return;
    }

    setActionLoadingId(item.id);
    setError('');
    setSuccessMsg('');

    try {
      let updated;
      if (isCurrentlyAvailable) {
        updated = await menuItemService.setUnavailable(item.id);
      } else {
        updated = await menuItemService.setAvailable(item.id);
      }
      setMenuItems((prev) =>
        prev.map((i) => (i.id === item.id ? updated : i))
      );
      setSuccessMsg(
        `"${updated.name}" is now ${
          updated.isAvailable ? 'Available' : 'Unavailable'
        }.`
      );
    } catch (err) {
      if (err.response) {
        setError(err.response.data?.message || `Failed to change item availability.`);
      } else {
        setError('Unable to connect to server. Please try again.');
      }
    } finally {
      setActionLoadingId(null);
    }
  };

  // Format currency in INR (₹)
  const formatCurrency = (val) => {
    if (val === undefined || val === null) return `${CURRENCY_SYMBOL}0.00`;
    return `${CURRENCY_SYMBOL}${Number(val).toFixed(2)}`;
  };

  return (
    <div className="admin-page-container">
      {/* Header Banner */}
      <div className="page-header-banner">
        <div className="header-text">
          <h1 className="page-title">Menu Management</h1>
          <p className="page-subtitle">
            Create, update, and manage your restaurant menu items and prices
          </p>
        </div>
        <button className="btn btn-primary" onClick={handleOpenAddModal}>
          Add Menu Item
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
          {/* Search Field */}
          <div className="search-box">
            <input
              type="text"
              className="form-input search-input"
              placeholder="Search items by name..."
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

          {/* Filters */}
          <div className="filters-group">
            {/* Category Filter */}
            <select
              className="form-input filter-select"
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
            >
              <option value="">All Categories</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>

            {/* Availability Filter */}
            <select
              className="form-input filter-select"
              value={selectedAvailability}
              onChange={(e) => setSelectedAvailability(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="AVAILABLE">Available Only</option>
              <option value="UNAVAILABLE">Unavailable Only</option>
            </select>
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      {loading ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading menu items...</p>
        </div>
      ) : filteredMenuItems.length === 0 ? (
        <div className="card empty-state-card">
          <h3>No Menu Items Found</h3>
          <p>
            {searchTerm || selectedCategory || selectedAvailability !== 'ALL'
              ? 'No items match your search or filter criteria. Try resetting filters.'
              : 'Add your first menu item to start building your restaurant menu.'}
          </p>
          {searchTerm || selectedCategory || selectedAvailability !== 'ALL' ? (
            <button
              className="btn btn-secondary"
              onClick={() => {
                setSearchTerm('');
                setSelectedCategory('');
                setSelectedAvailability('ALL');
              }}
            >
              Reset Filters
            </button>
          ) : (
            <button className="btn btn-primary" onClick={handleOpenAddModal}>
              Add First Menu Item
            </button>
          )}
        </div>
      ) : (
        <div className="card table-card">
          <div className="table-header-info">
            <span className="table-count">
              Showing <strong>{filteredMenuItems.length}</strong> of{' '}
              {menuItems.length} menu items
            </span>
          </div>
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Item Name</th>
                  <th>Category</th>
                  <th>Price</th>
                  <th>Description</th>
                  <th>Availability</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredMenuItems.map((item) => (
                  <tr key={item.id} className={!item.isAvailable ? 'row-inactive' : ''}>
                    <td className="font-weight-bold">{item.name}</td>
                    <td>
                      <span className="badge badge-category">
                        {item.categoryName || 'Uncategorized'}
                      </span>
                    </td>
                    <td className="font-price">{formatCurrency(item.price)}</td>
                    <td className="text-muted text-truncate">
                      {item.description || '—'}
                    </td>
                    <td>
                      <span
                        className={`status-badge ${
                          item.isAvailable ? 'status-active' : 'status-inactive'
                        }`}
                      >
                        {item.isAvailable ? 'Available' : 'Unavailable'}
                      </span>
                    </td>
                    <td className="text-right">
                      <div className="action-buttons">
                        <button
                          className="btn-action btn-edit"
                          onClick={() => handleOpenEditModal(item)}
                          title="Edit Menu Item"
                          disabled={actionLoadingId === item.id}
                        >
                          Edit
                        </button>
                        <button
                          className={`btn-action ${
                            item.isAvailable ? 'btn-deactivate' : 'btn-activate'
                          }`}
                          onClick={() => handleToggleAvailability(item)}
                          title={
                            item.isAvailable
                              ? 'Mark as Unavailable'
                              : 'Mark as Available'
                          }
                          disabled={actionLoadingId === item.id}
                        >
                          {actionLoadingId === item.id
                            ? '...'
                            : item.isAvailable
                            ? 'Mark Unavailable'
                            : 'Mark Available'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Add / Edit Menu Item Modal */}
      <MenuItemModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSubmit={handleFormSubmit}
        initialData={editingItem}
        categories={categories}
        isSubmitting={isSubmitting}
      />
    </div>
  );
};

export default MenuPage;
