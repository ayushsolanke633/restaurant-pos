import React, { useState, useEffect } from 'react';
import AdminModal from './AdminModal';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const MenuItemModal = ({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  categories = [],
  isSubmitting,
}) => {
  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [isAvailable, setIsAvailable] = useState(true);

  // Variation support
  const [hasVariations, setHasVariations] = useState(false);
  const [variations, setVariations] = useState([]);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (initialData) {
      setName(initialData.name || '');
      setCategoryId(initialData.categoryId ? String(initialData.categoryId) : '');
      setDescription(initialData.description || '');
      setPrice(initialData.price !== undefined && initialData.price !== null ? String(initialData.price) : '');
      setIsAvailable(initialData.isAvailable ?? true);

      if (Array.isArray(initialData.variations) && initialData.variations.length > 0) {
        setHasVariations(true);
        setVariations(
          initialData.variations.map((v) => ({
            id: v.id || null,
            name: v.name || '',
            price: v.price !== undefined && v.price !== null ? String(v.price) : '',
            isAvailable: v.isAvailable ?? true,
          }))
        );
      } else {
        setHasVariations(false);
        setVariations([]);
      }
    } else {
      setName('');
      setCategoryId(categories.length > 0 ? String(categories[0].id) : '');
      setDescription('');
      setPrice('');
      setIsAvailable(true);
      setHasVariations(false);
      setVariations([]);
    }
    setErrors({});
  }, [initialData, isOpen, categories]);

  // Handle adding a new variation row
  const handleAddVariation = () => {
    setVariations((prev) => [
      ...prev,
      {
        id: null,
        name: '',
        price: '',
        isAvailable: true,
      },
    ]);
  };

  // Handle removing a variation row
  const handleRemoveVariation = (index) => {
    setVariations((prev) => prev.filter((_, idx) => idx !== index));
  };

  // Handle updating a variation row
  const handleVariationChange = (index, field, value) => {
    setVariations((prev) =>
      prev.map((v, idx) => (idx === index ? { ...v, [field]: value } : v))
    );
  };

  const validate = () => {
    const newErrors = {};
    const trimmedName = name.trim();

    if (!trimmedName) {
      newErrors.name = 'Menu item name is required.';
    } else if (trimmedName.length > 150) {
      newErrors.name = 'Name must not exceed 150 characters.';
    }

    if (!categoryId) {
      newErrors.categoryId = 'Please select a category.';
    }

    if (hasVariations) {
      if (variations.length === 0) {
        newErrors.variations = 'Please add at least one variation or uncheck variations.';
      } else {
        const variationNamesSeen = new Set();
        const variationErrors = [];

        variations.forEach((v, idx) => {
          const vErrors = {};
          const vName = (v.name || '').trim();

          if (!vName) {
            vErrors.name = 'Name required';
          } else if (vName.length > 100) {
            vErrors.name = 'Max 100 chars';
          } else {
            const lowerName = vName.toLowerCase();
            if (variationNamesSeen.has(lowerName)) {
              vErrors.name = 'Duplicate name';
            } else {
              variationNamesSeen.add(lowerName);
            }
          }

          if (v.price === '' || v.price === null || v.price === undefined) {
            vErrors.price = 'Price required';
          } else {
            const numPrice = Number(v.price);
            if (isNaN(numPrice)) {
              vErrors.price = 'Invalid';
            } else if (numPrice < 0) {
              vErrors.price = 'Must be ≥ 0';
            }
          }

          if (Object.keys(vErrors).length > 0) {
            variationErrors[idx] = vErrors;
          }
        });

        if (variationErrors.length > 0) {
          newErrors.variationRows = variationErrors;
        }
      }
    } else {
      if (price === '' || price === null || price === undefined) {
        newErrors.price = 'Price is required.';
      } else {
        const numPrice = Number(price);
        if (isNaN(numPrice)) {
          newErrors.price = 'Please enter a valid numeric price.';
        } else if (numPrice < 0) {
          newErrors.price = 'Price must be greater than or equal to 0.';
        }
      }
    }

    if (description && description.length > 500) {
      newErrors.description = 'Description must not exceed 500 characters.';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;

    let payloadPrice;
    let payloadVariations = [];

    if (hasVariations) {
      const validVariations = variations.map((v, idx) => ({
        id: v.id || null,
        name: v.name.trim(),
        price: Number(Number(v.price).toFixed(2)),
        isAvailable: v.isAvailable ?? true,
        sortOrder: idx + 1,
      }));

      // Calculate baseline price as minimum variation price
      const minVarPrice = Math.min(...validVariations.map((v) => v.price));
      payloadPrice = Number(minVarPrice.toFixed(2));
      payloadVariations = validVariations;
    } else {
      payloadPrice = Number(Number(price).toFixed(2));
      payloadVariations = [];
    }

    onSubmit({
      name: name.trim(),
      categoryId: Number(categoryId),
      description: description.trim() || null,
      price: payloadPrice,
      isAvailable,
      variations: payloadVariations,
    });
  };

  const isEditing = !!initialData;

  return (
    <AdminModal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? 'Edit Menu Item' : 'Add New Menu Item'}
    >
      <form onSubmit={handleSubmit} className="admin-form" noValidate>
        <div className="form-group">
          <label htmlFor="item-name">Item Name *</label>
          <input
            id="item-name"
            type="text"
            className={`form-input ${errors.name ? 'input-error' : ''}`}
            placeholder="e.g. Butter Chicken, Paneer Tikka, Biryani"
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={isSubmitting}
            autoFocus
          />
          {errors.name && <span className="error-message">{errors.name}</span>}
        </div>

        <div className="grid-form-row">
          <div className="form-group">
            <label htmlFor="item-category">Category *</label>
            <select
              id="item-category"
              className={`form-input form-select ${errors.categoryId ? 'input-error' : ''}`}
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              disabled={isSubmitting || categories.length === 0}
            >
              <option value="">Select a category</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name} {!cat.isActive ? '(Inactive)' : ''}
                </option>
              ))}
            </select>
            {errors.categoryId && (
              <span className="error-message">{errors.categoryId}</span>
            )}
          </div>

          {!hasVariations && (
            <div className="form-group">
              <label htmlFor="item-price">Base Price ({CURRENCY_SYMBOL}) *</label>
              <div className="input-prefix-wrapper">
                <span className="input-prefix">{CURRENCY_SYMBOL}</span>
                <input
                  id="item-price"
                  type="number"
                  step="0.01"
                  min="0"
                  className={`form-input input-with-prefix ${
                    errors.price ? 'input-error' : ''
                  }`}
                  placeholder="0.00"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  disabled={isSubmitting}
                />
              </div>
              {errors.price && <span className="error-message">{errors.price}</span>}
            </div>
          )}

          {hasVariations && (
            <div className="form-group">
              <label>Base Price ({CURRENCY_SYMBOL})</label>
              <div className="input-prefix-wrapper">
                <span className="input-prefix">{CURRENCY_SYMBOL}</span>
                <input
                  type="text"
                  className="form-input input-with-prefix"
                  value={
                    variations.length > 0 && variations.some((v) => v.price !== '')
                      ? `From ${CURRENCY_SYMBOL}${Math.min(
                          ...variations
                            .filter((v) => v.price !== '' && !isNaN(Number(v.price)))
                            .map((v) => Number(v.price))
                        ).toFixed(2)}`
                      : 'Auto-calculated'
                  }
                  disabled
                  style={{ backgroundColor: '#f3f4f6', color: '#6b7280' }}
                />
              </div>
              <span className="text-muted" style={{ fontSize: '0.75rem', marginTop: '0.25rem', display: 'block' }}>
                Auto-synced with lowest variation price
              </span>
            </div>
          )}
        </div>

        {/* Variations Section Toggle */}
        <div className="form-group form-checkbox-group" style={{ margin: '0.75rem 0' }}>
          <label className="checkbox-label" style={{ fontWeight: 600 }}>
            <input
              type="checkbox"
              checked={hasVariations}
              onChange={(e) => {
                const checked = e.target.checked;
                setHasVariations(checked);
                if (checked && variations.length === 0) {
                  // Pre-populate with typical Half / Full variations or single blank variation
                  setVariations([
                    { id: null, name: 'Half', price: price || '', isAvailable: true },
                    { id: null, name: 'Full', price: '', isAvailable: true },
                  ]);
                }
              }}
              disabled={isSubmitting}
            />
            <span>This item has multiple portions or variations (e.g. Half, Full)</span>
          </label>
        </div>

        {/* Variations Management Section */}
        {hasVariations && (
          <div
            className="variations-management-card p-3 mb-3"
            style={{
              backgroundColor: '#fafaf9',
              border: '1px solid #e7e5e4',
              borderRadius: '8px',
            }}
          >
            <div className="d-flex justify-content-between align-items-center mb-2">
              <label style={{ margin: 0, fontWeight: 700, fontSize: '0.9rem', color: '#292524' }}>
                Item Variations ({variations.length})
              </label>
              <button
                type="button"
                className="btn btn-secondary btn-xs"
                onClick={handleAddVariation}
                disabled={isSubmitting}
                style={{ fontSize: '0.78rem', padding: '0.25rem 0.55rem' }}
              >
                + Add Variation
              </button>
            </div>

            {errors.variations && (
              <div className="error-message mb-2" style={{ display: 'block' }}>
                {errors.variations}
              </div>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              {variations.map((v, idx) => {
                const rowErrors = errors.variationRows?.[idx] || {};
                return (
                  <div
                    key={idx}
                    className="variation-row-item"
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '0.5rem',
                      padding: '0.5rem',
                      backgroundColor: '#ffffff',
                      border: '1px solid #e5e7eb',
                      borderRadius: '6px',
                    }}
                  >
                    <div style={{ flex: 2 }}>
                      <input
                        type="text"
                        className={`form-input ${rowErrors.name ? 'input-error' : ''}`}
                        placeholder="Name (e.g. Half)"
                        value={v.name}
                        onChange={(e) => handleVariationChange(idx, 'name', e.target.value)}
                        disabled={isSubmitting}
                        style={{ fontSize: '0.85rem', padding: '0.4rem 0.6rem' }}
                      />
                      {rowErrors.name && (
                        <span className="error-message" style={{ fontSize: '0.72rem' }}>
                          {rowErrors.name}
                        </span>
                      )}
                    </div>

                    <div style={{ flex: 2 }}>
                      <div className="input-prefix-wrapper">
                        <span className="input-prefix" style={{ fontSize: '0.8rem', padding: '0 0.4rem' }}>
                          {CURRENCY_SYMBOL}
                        </span>
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          className={`form-input input-with-prefix ${
                            rowErrors.price ? 'input-error' : ''
                          }`}
                          placeholder="Price"
                          value={v.price}
                          onChange={(e) => handleVariationChange(idx, 'price', e.target.value)}
                          disabled={isSubmitting}
                          style={{ fontSize: '0.85rem', padding: '0.4rem 0.6rem 0.4rem 1.6rem' }}
                        />
                      </div>
                      {rowErrors.price && (
                        <span className="error-message" style={{ fontSize: '0.72rem' }}>
                          {rowErrors.price}
                        </span>
                      )}
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', height: '34px', padding: '0 0.25rem' }}>
                      <label
                        className="checkbox-label"
                        title={v.isAvailable ? 'Available' : 'Unavailable'}
                        style={{ margin: 0, fontSize: '0.8rem' }}
                      >
                        <input
                          type="checkbox"
                          checked={v.isAvailable}
                          onChange={(e) => handleVariationChange(idx, 'isAvailable', e.target.checked)}
                          disabled={isSubmitting}
                        />
                        <span style={{ fontSize: '0.75rem' }}>Active</span>
                      </label>
                    </div>

                    <button
                      type="button"
                      className="btn btn-secondary btn-xs"
                      onClick={() => handleRemoveVariation(idx)}
                      disabled={isSubmitting}
                      title="Remove Variation"
                      style={{
                        height: '34px',
                        padding: '0 0.5rem',
                        color: '#dc2626',
                        borderColor: '#fca5a5',
                        backgroundColor: '#fff',
                      }}
                    >
                      ✕
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        <div className="form-group">
          <label htmlFor="item-desc">Description</label>
          <textarea
            id="item-desc"
            className={`form-input form-textarea ${errors.description ? 'input-error' : ''}`}
            placeholder="Brief ingredients or item description..."
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            disabled={isSubmitting}
          />
          {errors.description && (
            <span className="error-message">{errors.description}</span>
          )}
        </div>

        <div className="form-group form-checkbox-group">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={isAvailable}
              onChange={(e) => setIsAvailable(e.target.checked)}
              disabled={isSubmitting}
            />
            <span>Available for Ordering</span>
          </label>
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
                ? 'Saving...'
                : 'Creating...'
              : isEditing
              ? 'Update Item'
              : 'Add Menu Item'}
          </button>
        </div>
      </form>
    </AdminModal>
  );
};

export default MenuItemModal;
