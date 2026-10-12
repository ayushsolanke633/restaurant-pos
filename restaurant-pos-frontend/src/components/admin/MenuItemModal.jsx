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
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (initialData) {
      setName(initialData.name || '');
      setCategoryId(initialData.categoryId ? String(initialData.categoryId) : '');
      setDescription(initialData.description || '');
      setPrice(initialData.price !== undefined && initialData.price !== null ? String(initialData.price) : '');
      setIsAvailable(initialData.isAvailable ?? true);
    } else {
      setName('');
      setCategoryId(categories.length > 0 ? String(categories[0].id) : '');
      setDescription('');
      setPrice('');
      setIsAvailable(true);
    }
    setErrors({});
  }, [initialData, isOpen, categories]);

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

    if (description && description.length > 500) {
      newErrors.description = 'Description must not exceed 500 characters.';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;

    onSubmit({
      name: name.trim(),
      categoryId: Number(categoryId),
      description: description.trim() || null,
      price: Number(Number(price).toFixed(2)),
      isAvailable,
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
            placeholder="e.g. Butter Chicken, Paneer Tikka, Masala Dosa"
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

          <div className="form-group">
            <label htmlFor="item-price">Price ({CURRENCY_SYMBOL}) *</label>
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
        </div>

        <div className="form-group">
          <label htmlFor="item-desc">Description</label>
          <textarea
            id="item-desc"
            className={`form-input form-textarea ${errors.description ? 'input-error' : ''}`}
            placeholder="Brief ingredients or item description..."
            rows={3}
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
