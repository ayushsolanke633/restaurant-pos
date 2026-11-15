import React, { useState, useEffect, useMemo } from 'react';
import AdminModal from './AdminModal';
import VariationSelectModal from './VariationSelectModal';
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

  // Variation selection dialog state
  const [selectedMenuItemForVariation, setSelectedMenuItemForVariation] = useState(null);

  // Picker filters
  const [menuSearch, setMenuSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const getCartItemKey = (item) => `${item.menuItemId}_${item.variationId || 'base'}`;

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

  // Handling menu item click
  const handleItemClick = (item) => {
    const variations = Array.isArray(item.variations) ? item.variations : [];
    if (variations.length > 1) {
      // Multiple variations: open variation selection dialog
      setSelectedMenuItemForVariation(item);
    } else if (variations.length === 1) {
      // Exactly 1 variation: auto-select it
      handleAddItemWithVariation(item, variations[0]);
    } else {
      // No variations: add normally with base price
      handleAddItemWithVariation(item, null);
    }
  };

  const handleAddItemWithVariation = (item, variation) => {
    const variationId = variation ? variation.id : null;
    const variationName = variation ? variation.name : null;
    const unitPrice = variation ? Number(variation.price) : Number(item.price || 0);

    setCartItems((prev) => {
      const existingIndex = prev.findIndex(
        (i) => i.menuItemId === item.id && (i.variationId || null) === (variationId || null)
      );

      if (existingIndex > -1) {
        const updated = [...prev];
        updated[existingIndex].quantity += 1;
        return updated;
      } else {
        return [
          ...prev,
          {
            menuItemId: item.id,
            variationId: variationId || null,
            variationName: variationName || null,
            name: item.name,
            unitPrice,
            quantity: 1,
            notes: '',
          },
        ];
      }
    });
  };

  const handleUpdateQuantity = (itemKey, delta) => {
    setCartItems((prev) =>
      prev
        .map((i) => {
          if (getCartItemKey(i) === itemKey) {
            const newQty = i.quantity + delta;
            return newQty > 0 ? { ...i, quantity: newQty } : null;
          }
          return i;
        })
        .filter(Boolean)
    );
  };

  const handleItemNoteChange = (itemKey, noteText) => {
    setCartItems((prev) =>
      prev.map((i) =>
        getCartItemKey(i) === itemKey ? { ...i, notes: noteText } : i
      )
    );
  };

  const handleRemoveItem = (itemKey) => {
    setCartItems((prev) => prev.filter((i) => getCartItemKey(i) !== itemKey));
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
        variationId: item.variationId || null,
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

  const tableLabel = order.tableName || (order.tableNumber ? `Table ${order.tableNumber}` : '');

  return (
    <>
      <AdminModal
        isOpen={isOpen}
        onClose={onClose}
        title={`Add Items to Order #${order.id} ${tableLabel ? `(${tableLabel})` : ''}`}
      >
        {loadingData ? (
          <div className="modal-loading-state">Loading menu items...</div>
        ) : (
          <form onSubmit={handleSubmit} className="admin-form add-items-form" noValidate>
            {error && (
              <div className="alert alert-danger mb-3" role="alert">
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

            <div className="form-group mb-3">
              <label htmlFor="addition-notes">Addition Instructions / Notes (Optional)</label>
              <input
                id="addition-notes"
                type="text"
                className="form-input"
                placeholder="e.g. Added by guest, Urgent, Special request..."
                value={additionNotes}
                onChange={(e) => setAdditionNotes(e.target.value)}
                disabled={isSubmitting}
              />
            </div>

            <div className="add-items-layout-grid">
              {/* Left side: Menu items picker */}
              <div className="menu-picker-column">
                <div className="picker-filters-row mb-2">
                  <input
                    type="text"
                    className="form-input filter-search-sm"
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

                <div className="menu-items-scroll-list">
                  {filteredMenuItems.length === 0 ? (
                    <div className="empty-picker-msg text-muted">No items found</div>
                  ) : (
                    filteredMenuItems.map((item) => {
                      const hasVars = Array.isArray(item.variations) && item.variations.length > 0;
                      const multipleVars = Array.isArray(item.variations) && item.variations.length > 1;

                      return (
                        <div key={item.id} className="picker-item-row">
                          <div className="picker-item-info">
                            <span className="picker-item-name">{item.name}</span>
                            <span className="picker-item-price">
                              {hasVars ? (
                                <span>
                                  <small style={{ fontSize: '0.72rem', fontWeight: 'normal', color: '#6b7280' }}>From </small>
                                  {CURRENCY_SYMBOL}{Number(item.price).toFixed(2)}
                                </span>
                              ) : (
                                `${CURRENCY_SYMBOL}${Number(item.price).toFixed(2)}`
                              )}
                            </span>
                            {multipleVars && (
                              <span className="text-muted" style={{ fontSize: '0.72rem', display: 'block' }}>
                                {item.variations.length} portions
                              </span>
                            )}
                          </div>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm btn-add-picker"
                            onClick={() => handleItemClick(item)}
                            disabled={isSubmitting}
                          >
                            {multipleVars ? 'Select...' : '+ Add'}
                          </button>
                        </div>
                      );
                    })
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
                    cartItems.map((item) => {
                      const itemKey = getCartItemKey(item);
                      return (
                        <div key={itemKey} className="cart-item-row">
                          <div className="cart-item-header">
                            <span className="cart-item-name">
                              {item.name}
                              {item.variationName && (
                                <span
                                  className="badge badge-info ml-1"
                                  style={{
                                    fontSize: '0.75rem',
                                    fontWeight: 600,
                                    padding: '0.15rem 0.4rem',
                                    borderRadius: '4px',
                                  }}
                                >
                                  {item.variationName}
                                </span>
                              )}
                            </span>
                            <button
                              type="button"
                              className="btn-remove-item"
                              onClick={() => handleRemoveItem(itemKey)}
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
                                onClick={() => handleUpdateQuantity(itemKey, -1)}
                                disabled={isSubmitting}
                              >
                                -
                              </button>
                              <span className="qty-display">{item.quantity}</span>
                              <button
                                type="button"
                                className="btn-qty"
                                onClick={() => handleUpdateQuantity(itemKey, 1)}
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
                              handleItemNoteChange(itemKey, e.target.value)
                            }
                            disabled={isSubmitting}
                          />
                        </div>
                      );
                    })
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

      {/* Variation Selection Dialog */}
      <VariationSelectModal
        isOpen={!!selectedMenuItemForVariation}
        onClose={() => setSelectedMenuItemForVariation(null)}
        menuItem={selectedMenuItemForVariation}
        onSelectVariation={(variation) => {
          if (selectedMenuItemForVariation) {
            handleAddItemWithVariation(selectedMenuItemForVariation, variation);
          }
        }}
      />
    </>
  );
};

export default AddItemsModal;
