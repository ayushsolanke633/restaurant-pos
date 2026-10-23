import React, { useState, useEffect, useMemo } from 'react';
import AdminModal from './AdminModal';
import menuItemService from '../../services/menuItemService';
import categoryService from '../../services/categoryService';
import orderService from '../../services/orderService';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const AddItemsModal = ({
  isOpen,
  onClose,
  order,
  onItemsAdded,
}) => {
  const [menuItems, setMenuItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loadingData, setLoadingData] = useState(false);

  // Cart of new items to add
  const [cartItems, setCartItems] = useState([]);
  const [additionNotes, setAdditionNotes] = useState('');

  // Picker filters
  const [menuSearch, setMenuSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Load menu items and categories when modal opens
  useEffect(() => {
    if (!isOpen || !order?.id) return;

    const loadMenuData = async () => {
      setLoadingData(true);
      setError('');
      try {
        const [catsData, itemsData] = await Promise.all([
          categoryService.getCategories(),
          menuItemService.getMenuItems(),
        ]);
        setCategories(Array.isArray(catsData) ? catsData : []);
        const allItems = Array.isArray(itemsData) ? itemsData : [];
        setMenuItems(allItems.filter((i) => i.isAvailable));
      } catch (err) {
        console.error('Failed to load menu data for AddItemsModal:', err);
        setError('Failed to load menu items. Please try again.');
      } finally {
        setLoadingData(false);
      }
    };

    loadMenuData();
    setCartItems([]);
    setAdditionNotes('');
    setMenuSearch('');
    setSelectedCategory('');
  }, [isOpen, order?.id]);

  // Filter menu items
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
            unitPrice: Number(item.price || 0),
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

  // Subtotal for additional items
  const addedSubtotal = useMemo(() => {
    return cartItems.reduce(
      (sum, item) => sum + item.unitPrice * item.quantity,
      0
    );
  }, [cartItems]);

  const currentOrderTotal = Number(order?.totalAmount || order?.subtotal || 0);
  const estimatedNewTotal = currentOrderTotal + addedSubtotal;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (cartItems.length === 0) {
      setError('Please select at least one item to add.');
      return;
    }

    setIsSubmitting(true);
    setError('');

    const payload = {
      orderType: order.orderType || 'DINE_IN',
      notes: additionNotes.trim() || undefined,
      items: cartItems.map((item) => ({
        menuItemId: item.menuItemId,
        quantity: item.quantity,
        notes: item.notes.trim() || null,
      })),
    };

    try {
      const updatedOrder = await orderService.addItemsToOrder(order.id, payload);
      if (onItemsAdded) {
        onItemsAdded(updatedOrder);
      }
      onClose();
    } catch (err) {
      if (err.response) {
        setError(err.response.data?.message || 'Failed to add items to order.');
      } else {
        setError('Unable to connect to server. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!order) return null;

  const tableLabel = order.tableName
    ? order.tableName
    : order.tableNumber !== undefined && order.tableNumber !== null
    ? `Table ${order.tableNumber}`
    : order.tableId
    ? `Table ${order.tableId}`
    : '';

  return (
    <AdminModal
      isOpen={isOpen}
      onClose={onClose}
      title={`Add Items to Order #${order.id} ${tableLabel ? `(${tableLabel})` : ''}`}
    >
      {loadingData ? (
        <div className="loading-state">
          <div className="spinner"></div>
          <p>Loading menu items...</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="admin-form add-items-form" noValidate>
          {error && (
            <div className="alert alert-danger" role="alert" style={{ marginBottom: '1rem' }}>
              <span>{error}</span>
            </div>
          )}

          <div className="order-modal-layout">
            {/* Left side: Menu items picker */}
            <div className="menu-picker-column">
              <h4 className="picker-heading">Select Menu Items to Add</h4>

              <div className="picker-controls">
                <input
                  type="text"
                  className="form-input"
                  placeholder="Search menu..."
                  value={menuSearch}
                  onChange={(e) => setMenuSearch(e.target.value)}
                />
                <select
                  className="form-input form-select"
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

              <div className="menu-items-picker-list">
                {filteredMenuItems.length === 0 ? (
                  <p className="no-items-text text-muted">No items found</p>
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
                        className="btn btn-secondary btn-sm btn-add-picker"
                        onClick={() => handleAddItemToCart(item)}
                        disabled={isSubmitting}
                      >
                        + Add
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* Right side: Cart of items to add */}
            <div className="cart-summary-column">
              <h4 className="picker-heading">
                Items to Add ({cartItems.length})
              </h4>

              <div className="cart-items-list">
                {cartItems.length === 0 ? (
                  <div className="empty-cart-msg text-muted">
                    No items selected yet. Click "+ Add" on any menu item on the left.
                  </div>
                ) : (
                  cartItems.map((item) => (
                    <div key={item.menuItemId} className="cart-item-row">
                      <div className="cart-item-header">
                        <span className="cart-item-name">{item.name}</span>
                        <button
                          type="button"
                          className="btn-remove-item"
                          onClick={() => handleRemoveItem(item.menuItemId)}
                          title="Remove item"
                          disabled={isSubmitting}
                        >
                          ✕
                        </button>
                      </div>

                      <div className="cart-item-controls">
                        <div className="qty-control-group">
                          <button
                            type="button"
                            className="btn-qty"
                            onClick={() => handleUpdateQuantity(item.menuItemId, -1)}
                            disabled={isSubmitting}
                          >
                            -
                          </button>
                          <span className="qty-display">{item.quantity}</span>
                          <button
                            type="button"
                            className="btn-qty"
                            onClick={() => handleUpdateQuantity(item.menuItemId, 1)}
                            disabled={isSubmitting}
                          >
                            +
                          </button>
                        </div>

                        <span className="cart-item-subtotal">
                          {CURRENCY_SYMBOL}
                          {(item.unitPrice * item.quantity).toFixed(2)}
                        </span>
                      </div>

                      <input
                        type="text"
                        className="form-input item-note-input"
                        placeholder="Item instructions (optional)"
                        value={item.notes}
                        onChange={(e) =>
                          handleItemNoteChange(item.menuItemId, e.target.value)
                        }
                        disabled={isSubmitting}
                      />
                    </div>
                  ))
                )}
              </div>

              {/* Total calculation preview */}
              <div className="cart-pricing-preview">
                <div className="pricing-row">
                  <span>Current Order Total:</span>
                  <span>
                    {CURRENCY_SYMBOL}
                    {currentOrderTotal.toFixed(2)}
                  </span>
                </div>
                <div className="pricing-row font-weight-bold" style={{ color: 'var(--wn-primary, #6366f1)' }}>
                  <span>Additional Items Subtotal:</span>
                  <span>
                    +{CURRENCY_SYMBOL}
                    {addedSubtotal.toFixed(2)}
                  </span>
                </div>
                <div className="pricing-row total-row">
                  <span>Estimated New Total:</span>
                  <span>
                    {CURRENCY_SYMBOL}
                    {estimatedNewTotal.toFixed(2)}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div className="modal-footer" style={{ marginTop: '1.25rem', display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
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
              disabled={isSubmitting || cartItems.length === 0}
            >
              {isSubmitting ? 'Adding Items...' : `Add Items (${CURRENCY_SYMBOL}${addedSubtotal.toFixed(2)})`}
            </button>
          </div>
        </form>
      )}
    </AdminModal>
  );
};

export default AddItemsModal;
