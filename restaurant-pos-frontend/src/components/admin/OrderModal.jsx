import React, { useState, useEffect, useMemo } from 'react';
import AdminModal from './AdminModal';
import tableService from '../../services/tableService';
import menuItemService from '../../services/menuItemService';
import categoryService from '../../services/categoryService';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const OrderModal = ({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  isSubmitting,
}) => {
  const [orderType, setOrderType] = useState('DINE_IN');
  const [tableId, setTableId] = useState('');
  const [notes, setNotes] = useState('');
  
  // Menu items & Categories data
  const [availableTables, setAvailableTables] = useState([]);
  const [menuItems, setMenuItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loadingData, setLoadingData] = useState(false);

  // Cart state: Array of { menuItemId, name, unitPrice, quantity, notes }
  const [cartItems, setCartItems] = useState([]);

  // Picker filters
  const [menuSearch, setMenuSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');

  const [errors, setErrors] = useState({});

  // Fetch tables, categories, and menu items when modal opens
  useEffect(() => {
    if (!isOpen) return;

    const loadFormDependencies = async () => {
      setLoadingData(true);
      try {
        const [tablesData, catsData, itemsData] = await Promise.all([
          tableService.getTables(),
          categoryService.getCategories(),
          menuItemService.getMenuItems(),
        ]);

        const allTables = (Array.isArray(tablesData) ? tablesData : []).sort(
          (a, b) => Number(a.tableNumber) - Number(b.tableNumber)
        );
        const allItems = Array.isArray(itemsData) ? itemsData : [];
        
        setCategories(Array.isArray(catsData) ? catsData : []);
        setMenuItems(allItems.filter((i) => i.isAvailable));

        if (initialData) {
          // Editing existing order (allowed only when status === 'NEW')
          setOrderType(initialData.orderType || 'DINE_IN');
          setTableId(initialData.tableId ? String(initialData.tableId) : '');
          setNotes(initialData.notes || '');

          // Filter available tables + current table if DINE_IN
          const usableTables = allTables
            .filter(
              (t) => (t.status || '').toUpperCase() === 'AVAILABLE' || t.id === initialData.tableId
            )
            .sort((a, b) => Number(a.tableNumber) - Number(b.tableNumber));
          setAvailableTables(usableTables);

          // Populate cart items from initialData.items
          if (Array.isArray(initialData.items)) {
            const mappedCart = initialData.items.map((item) => ({
              menuItemId: item.menuItemId,
              name: item.menuItemName || 'Item',
              unitPrice: Number(item.unitPrice || 0),
              quantity: item.quantity || 1,
              notes: item.notes || '',
            }));
            setCartItems(mappedCart);
          } else {
            setCartItems([]);
          }
        } else {
          // Creating new order
          setOrderType('DINE_IN');
          setNotes('');
          setCartItems([]);

          // Filter only AVAILABLE tables
          const avail = allTables
            .filter((t) => (t.status || '').toUpperCase() === 'AVAILABLE')
            .sort((a, b) => Number(a.tableNumber) - Number(b.tableNumber));
          setAvailableTables(avail);
          setTableId(avail.length > 0 ? String(avail[0].id) : '');
        }
      } catch (err) {
        console.error('Failed to load dependencies for OrderModal:', err);
      } finally {
        setLoadingData(false);
      }
    };

    loadFormDependencies();
    setErrors({});
  }, [isOpen, initialData]);

  // Filter menu items for picker
  const filteredMenuItems = useMemo(() => {
    return menuItems.filter((item) => {
      const matchesSearch =
        !menuSearch.trim() ||
        item.name.toLowerCase().includes(menuSearch.toLowerCase().trim());
      const matchesCat =
        !selectedCategory || String(item.categoryId) === String(selectedCategory);
      return matchesSearch && matchesCat;
    });
  }, [menuItems, menuSearch, selectedCategory]);

  // Cart operations
  const handleAddItemToCart = (item) => {
    setCartItems((prev) => {
      const existingIndex = prev.findIndex((i) => i.menuItemId === item.id);
      if (existingIndex > -1) {
        const updated = [...prev];
        updated[existingIndex].quantity += 1;
        return updated;
      } else {
        return [
          ...prev,
          {
            menuItemId: item.id,
            name: item.name,
            unitPrice: Number(item.price),
            quantity: 1,
            notes: '',
          },
        ];
      }
    });
  };

  const handleUpdateQuantity = (menuItemId, delta) => {
    setCartItems((prev) =>
      prev
        .map((i) => {
          if (i.menuItemId === menuItemId) {
            const newQty = i.quantity + delta;
            return newQty > 0 ? { ...i, quantity: newQty } : null;
          }
          return i;
        })
        .filter(Boolean)
    );
  };

  const handleItemNoteChange = (menuItemId, noteText) => {
    setCartItems((prev) =>
      prev.map((i) =>
        i.menuItemId === menuItemId ? { ...i, notes: noteText } : i
      )
    );
  };

  const handleRemoveItem = (menuItemId) => {
    setCartItems((prev) => prev.filter((i) => i.menuItemId !== menuItemId));
  };

  // Preview total calculation
  const previewTotal = useMemo(() => {
    return cartItems.reduce(
      (sum, item) => sum + item.unitPrice * item.quantity,
      0
    );
  }, [cartItems]);

  // Form Validation
  const validate = () => {
    const newErrors = {};

    if (orderType === 'DINE_IN' && !tableId) {
      newErrors.tableId = 'Please select a table for DINE_IN order.';
    }

    if (cartItems.length === 0) {
      newErrors.items = 'Please select at least one menu item for the order.';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;

    const payload = {
      orderType,
      tableId: orderType === 'DINE_IN' ? Number(tableId) : null,
      notes: notes.trim() || null,
      items: cartItems.map((item) => ({
        menuItemId: item.menuItemId,
        quantity: item.quantity,
        notes: item.notes.trim() || null,
      })),
    };

    onSubmit(payload);
  };

  const isEditing = !!initialData;

  return (
    <AdminModal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? `Edit Order #${initialData.id}` : 'Create New Order'}
    >
      {loadingData ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading form dependencies...</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="admin-form order-modal-form" noValidate>
          {/* Order Type Selector */}
          <div className="form-group">
            <label>Order Type *</label>
            <div className="type-toggle-group">
              <button
                type="button"
                className={`btn-type-toggle ${
                  orderType === 'DINE_IN' ? 'active' : ''
                }`}
                onClick={() => setOrderType('DINE_IN')}
                disabled={isSubmitting}
              >
                Dine In
              </button>
              <button
                type="button"
                className={`btn-type-toggle ${
                  orderType === 'TAKEAWAY' ? 'active' : ''
                }`}
                onClick={() => setOrderType('TAKEAWAY')}
                disabled={isSubmitting}
              >
                Takeaway
              </button>
            </div>
          </div>

          {/* Table Selection for DINE_IN */}
          {orderType === 'DINE_IN' && (
            <div className="form-group">
              <label htmlFor="order-table">Select Table *</label>
              <select
                id="order-table"
                className={`form-input form-select ${
                  errors.tableId ? 'input-error' : ''
                }`}
                value={tableId}
                onChange={(e) => setTableId(e.target.value)}
                disabled={isSubmitting || availableTables.length === 0}
              >
                {availableTables.length === 0 ? (
                  <option value="">No available tables</option>
                ) : (
                  availableTables.map((tbl) => (
                    <option key={tbl.id} value={tbl.id}>
                      Table {tbl.tableNumber} ({tbl.capacity} Seats)
                    </option>
                  ))
                )}
              </select>
              {errors.tableId && (
                <span className="error-message">{errors.tableId}</span>
              )}
            </div>
          )}

          {/* Overall Order Notes */}
          <div className="form-group">
            <label htmlFor="order-notes">Order Notes / Instructions</label>
            <input
              id="order-notes"
              type="text"
              className="form-input"
              placeholder="e.g. Less spicy, Allergy warning, Urgent"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              disabled={isSubmitting}
            />
          </div>

          {/* Menu Item Picker Section */}
          <div className="menu-picker-section">
            <label className="section-subtitle-label">Add Items from Menu</label>
            <div className="picker-controls">
              <input
                type="text"
                className="form-input search-input-sm"
                placeholder="Search menu..."
                value={menuSearch}
                onChange={(e) => setMenuSearch(e.target.value)}
              />
              <select
                className="form-input filter-select-sm"
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
            </div>

            <div className="menu-items-grid">
              {filteredMenuItems.length === 0 ? (
                <div className="picker-empty-text">No available menu items found</div>
              ) : (
                filteredMenuItems.map((item) => (
                  <div key={item.id} className="picker-item-card">
                    <div className="picker-item-info">
                      <span className="picker-item-name">{item.name}</span>
                      <span className="picker-item-price">
                        {CURRENCY_SYMBOL}
                        {Number(item.price).toFixed(2)}
                      </span>
                    </div>
                    <button
                      type="button"
                      className="btn-add-item"
                      onClick={() => handleAddItemToCart(item)}
                    >
                      + Add
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Order Items Cart Section */}
          <div className="cart-section">
            <label className="section-subtitle-label">
              Selected Order Items ({cartItems.length})
            </label>
            {errors.items && (
              <span className="error-message block-error">{errors.items}</span>
            )}

            {cartItems.length === 0 ? (
              <div className="cart-empty-state">
                No items added yet. Click "+ Add" on menu items above.
              </div>
            ) : (
              <div className="cart-items-list">
                {cartItems.map((cartItem) => (
                  <div key={cartItem.menuItemId} className="cart-item-row">
                    <div className="cart-item-main">
                      <div className="cart-item-details">
                        <span className="cart-item-title">{cartItem.name}</span>
                        <span className="cart-item-subtotal">
                          {CURRENCY_SYMBOL}
                          {(cartItem.unitPrice * cartItem.quantity).toFixed(2)}
                        </span>
                      </div>

                      <div className="cart-item-actions">
                        <div className="qty-stepper">
                          <button
                            type="button"
                            className="btn-qty"
                            onClick={() =>
                              handleUpdateQuantity(cartItem.menuItemId, -1)
                            }
                          >
                            -
                          </button>
                          <span className="qty-value">{cartItem.quantity}</span>
                          <button
                            type="button"
                            className="btn-qty"
                            onClick={() =>
                              handleUpdateQuantity(cartItem.menuItemId, 1)
                            }
                          >
                            +
                          </button>
                        </div>
                        <button
                          type="button"
                          className="btn-remove-cart"
                          onClick={() => handleRemoveItem(cartItem.menuItemId)}
                          title="Remove item"
                        >
                          Remove
                        </button>
                      </div>
                    </div>

                    <input
                      type="text"
                      className="form-input form-input-sm cart-item-note-input"
                      placeholder="Item note (e.g. Extra cheese)..."
                      value={cartItem.notes}
                      onChange={(e) =>
                        handleItemNoteChange(cartItem.menuItemId, e.target.value)
                      }
                    />
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Preview Total & Footer Actions */}
          <div className="order-total-preview-row">
            <span className="total-label">Estimated Total:</span>
            <span className="total-value">
              {CURRENCY_SYMBOL}
              {previewTotal.toFixed(2)}
            </span>
          </div>

          <div className="modal-footer">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={isSubmitting}
            >
              {isSubmitting
                ? isEditing
                  ? 'Updating Order...'
                  : 'Submitting Order...'
                : isEditing
                ? 'Update Order'
                : 'Create Order'}
            </button>
          </div>
        </form>
      )}
    </AdminModal>
  );
};

export default OrderModal;
