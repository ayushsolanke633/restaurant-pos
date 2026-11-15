import React, { useState, useEffect, useMemo } from 'react';
import AdminModal from './AdminModal';
import VariationSelectModal from './VariationSelectModal';
import useAuth from '../../hooks/useAuth';
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
  initialTableId = null,
  lockTable = false,
}) => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
  const isEditing = !!initialData;

  const [orderType, setOrderType] = useState('DINE_IN');
  const [tableId, setTableId] = useState('');
  const [notes, setNotes] = useState('');
  
  // Menu items & Categories data
  const [availableTables, setAvailableTables] = useState([]);
  const [menuItems, setMenuItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loadingData, setLoadingData] = useState(false);

  // Cart state: Array of { menuItemId, variationId, variationName, name, unitPrice, quantity, notes }
  const [cartItems, setCartItems] = useState([]);
  const [initialItemQuantities, setInitialItemQuantities] = useState({});

  // Variation selection dialog state
  const [selectedMenuItemForVariation, setSelectedMenuItemForVariation] = useState(null);

  // Picker filters
  const [menuSearch, setMenuSearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');

  const [errors, setErrors] = useState({});

  const getCartItemKey = (item) => `${item.menuItemId}_${item.variationId || 'base'}`;

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
              variationId: item.variationId || null,
              variationName: item.variationName || null,
              name: item.menuItemName || 'Item',
              unitPrice: Number(item.unitPrice || 0),
              quantity: item.quantity || 1,
              notes: item.notes || '',
            }));
            setCartItems(mappedCart);
            const quantities = {};
            mappedCart.forEach((item) => {
              const key = getCartItemKey(item);
              quantities[key] = (quantities[key] || 0) + (item.quantity || 1);
            });
            setInitialItemQuantities(quantities);
          } else {
            setCartItems([]);
            setInitialItemQuantities({});
          }
        } else {
          // Creating new order
          setOrderType('DINE_IN');
          setNotes('');
          setCartItems([]);
          setInitialItemQuantities({});

          // Filter only AVAILABLE tables (or selected table if provided)
          const targetTableId = initialTableId ? Number(initialTableId) : null;
          const avail = allTables
            .filter(
              (t) => (t.status || '').toUpperCase() === 'AVAILABLE' || (targetTableId && t.id === targetTableId)
            )
            .sort((a, b) => Number(a.tableNumber) - Number(b.tableNumber));
          setAvailableTables(avail);
          if (targetTableId) {
            setTableId(String(targetTableId));
          } else {
            setTableId(avail.length > 0 ? String(avail[0].id) : '');
          }
        }
      } catch (err) {
        console.error('Failed to load dependencies for OrderModal:', err);
      } finally {
        setLoadingData(false);
      }
    };

    loadFormDependencies();
    setErrors({});
  }, [isOpen, initialData, initialTableId]);

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

  // Handling click on a menu item
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
            const initialQty = isEditing ? (initialItemQuantities[itemKey] || 0) : 0;
            if (!isAdmin && isEditing && initialQty > 0 && delta < 0 && i.quantity <= initialQty) {
              return i; // Only ADMIN can decrease existing items
            }
            const newQty = i.quantity + delta;
            if (newQty <= 0) {
              if (!isAdmin && isEditing && initialQty > 0) {
                return i; // Only ADMIN can delete existing items by decreasing to zero
              }
              return null;
            }
            return { ...i, quantity: newQty };
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
    const initialQty = isEditing ? (initialItemQuantities[itemKey] || 0) : 0;
    if (!isAdmin && isEditing && initialQty > 0) {
      return; // Only ADMIN can delete existing items
    }
    setCartItems((prev) => prev.filter((i) => getCartItemKey(i) !== itemKey));
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
        variationId: item.variationId || null,
        quantity: item.quantity,
        notes: item.notes.trim() || null,
      })),
    };

    onSubmit(payload);
  };

  const selectedTableObj = useMemo(() => {
    return availableTables.find((t) => String(t.id) === String(tableId));
  }, [availableTables, tableId]);

  const modalTitle = isEditing
    ? `Edit Order #${initialData.id}`
    : lockTable && selectedTableObj
    ? `Create Dine-In Order - Table ${selectedTableObj.tableNumber}`
    : 'Create New Order';

  return (
    <>
      <AdminModal
        isOpen={isOpen}
        onClose={onClose}
        title={modalTitle}
      >
        {loadingData ? (
          <div className="modal-loading-state">Loading form data...</div>
        ) : (
          <form onSubmit={handleSubmit} className="admin-form order-form" noValidate>
            {/* Order Settings Section */}
            <div className="grid-form-row">
              <div className="form-group">
                <label htmlFor="order-type">Order Type *</label>
                <select
                  id="order-type"
                  className="form-input form-select"
                  value={orderType}
                  onChange={(e) => setOrderType(e.target.value)}
                  disabled={isSubmitting || lockTable}
                >
                  <option value="DINE_IN">Dine In</option>
                  <option value="TAKEAWAY">Takeaway</option>
                </select>
              </div>

              {orderType === 'DINE_IN' && (
                <div className="form-group">
                  <label htmlFor="order-table">Table *</label>
                  <select
                    id="order-table"
                    className={`form-input form-select ${
                      errors.tableId ? 'input-error' : ''
                    }`}
                    value={tableId}
                    onChange={(e) => setTableId(e.target.value)}
                    disabled={isSubmitting || lockTable || availableTables.length === 0}
                  >
                    <option value="">Select a Table</option>
                    {availableTables.map((tbl) => (
                      <option key={tbl.id} value={tbl.id}>
                        Table {tbl.tableNumber} ({tbl.capacity} seats)
                      </option>
                    ))}
                  </select>
                  {errors.tableId && (
                    <span className="error-message">{errors.tableId}</span>
                  )}
                  {availableTables.length === 0 && !lockTable && (
                    <span className="text-warning text-xs mt-1">
                      No tables currently marked as Available.
                    </span>
                  )}
                </div>
              )}
            </div>

            {/* Notes Section */}
            <div className="form-group">
              <label htmlFor="order-notes">Order Notes / Customer Instructions</label>
              <input
                id="order-notes"
                type="text"
                className="form-input"
                placeholder="e.g. Less spicy, Allergy details, Customer name..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                disabled={isSubmitting}
              />
            </div>

            {/* Menu Item Picker Section */}
            <div className="menu-picker-section">
              <div className="picker-header">
                <label className="section-subtitle-label">Add Items to Order</label>
                <span className="picker-badge">{filteredMenuItems.length} items</span>
              </div>

              <div className="picker-filters-row">
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

              <div className="menu-items-grid">
                {filteredMenuItems.length === 0 ? (
                  <div className="picker-empty-text">No available menu items found</div>
                ) : (
                  filteredMenuItems.map((item) => {
                    const hasVars = Array.isArray(item.variations) && item.variations.length > 0;
                    const multipleVars = Array.isArray(item.variations) && item.variations.length > 1;

                    return (
                      <div key={item.id} className="picker-item-card">
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
                            <span className="text-muted" style={{ fontSize: '0.72rem', marginTop: '0.1rem' }}>
                              {item.variations.length} portions
                            </span>
                          )}
                        </div>
                        <button
                          type="button"
                          className="btn-add-item"
                          onClick={() => handleItemClick(item)}
                        >
                          {multipleVars ? 'Select...' : '+ Add'}
                        </button>
                      </div>
                    );
                  })
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
                  {cartItems.map((cartItem) => {
                    const itemKey = getCartItemKey(cartItem);
                    const initialQty = isEditing ? (initialItemQuantities[itemKey] || 0) : 0;
                    const canDecrease = isAdmin || !isEditing || initialQty === 0 || cartItem.quantity > initialQty;
                    const canRemove = isAdmin || !isEditing || initialQty === 0;

                    return (
                      <div key={itemKey} className="cart-item-row">
                        <div className="cart-item-main">
                          <div className="cart-item-details">
                            <span className="cart-item-title">
                              {cartItem.name}
                              {cartItem.variationName && (
                                <span
                                  className="badge badge-info ml-1"
                                  style={{
                                    fontSize: '0.75rem',
                                    fontWeight: 600,
                                    padding: '0.15rem 0.4rem',
                                    borderRadius: '4px',
                                  }}
                                >
                                  {cartItem.variationName}
                                </span>
                              )}
                            </span>
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
                                disabled={isSubmitting || !canDecrease}
                                onClick={() => handleUpdateQuantity(itemKey, -1)}
                                title={
                                  !canDecrease
                                    ? 'Only ADMIN can decrease quantity of existing items'
                                    : 'Decrease quantity'
                                }
                              >
                                -
                              </button>
                              <span className="qty-value">{cartItem.quantity}</span>
                              <button
                                type="button"
                                className="btn-qty"
                                disabled={isSubmitting}
                                onClick={() => handleUpdateQuantity(itemKey, 1)}
                                title="Increase quantity"
                              >
                                +
                              </button>
                            </div>
                            {canRemove ? (
                              <button
                                type="button"
                                className="btn-remove-cart"
                                disabled={isSubmitting}
                                onClick={() => handleRemoveItem(itemKey)}
                                title="Remove item"
                              >
                                Remove
                              </button>
                            ) : null}
                          </div>
                        </div>

                        <input
                          type="text"
                          className="form-input form-input-sm cart-item-note-input"
                          placeholder="Item note (e.g. Extra cheese)..."
                          value={cartItem.notes}
                          onChange={(e) => handleItemNoteChange(itemKey, e.target.value)}
                        />
                      </div>
                    );
                  })}
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

export default OrderModal;
