import React, { useState, useEffect } from 'react';
import AdminModal from './AdminModal';

export const CategoryModal = ({ isOpen, onClose, onSubmit, initialData, isSubmitting }) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (initialData) {
      setName(initialData.name || '');
      setDescription(initialData.description || '');
      setIsActive(initialData.isActive ?? true);
    } else {
      setName('');
      setDescription('');
      setIsActive(true);
    }
    setErrors({});
  }, [initialData, isOpen]);

  const validate = () => {
    const newErrors = {};
    const trimmedName = name.trim();

    if (!trimmedName) {
      newErrors.name = 'Category name is required.';
    } else if (trimmedName.length > 100) {
      newErrors.name = 'Category name must not exceed 100 characters.';
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
      description: description.trim() || null,
      isActive,
    });
  };

  const isEditing = !!initialData;

  return (
    <AdminModal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? 'Edit Category' : 'Add New Category'}
    >
      <form onSubmit={handleSubmit} className="admin-form" noValidate>
        <div className="form-group">
          <label htmlFor="cat-name">Category Name *</label>
          <input
            id="cat-name"
            type="text"
            className={`form-input ${errors.name ? 'input-error' : ''}`}
            placeholder="e.g. Main Course, Beverages, Desserts"
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={isSubmitting}
            autoFocus
          />
          {errors.name && <span className="error-message">{errors.name}</span>}
        </div>

        <div className="form-group">
          <label htmlFor="cat-desc">Description</label>
          <textarea
            id="cat-desc"
            className={`form-input form-textarea ${errors.description ? 'input-error' : ''}`}
            placeholder="Brief description of the category..."
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
              checked={isActive}
              onChange={(e) => setIsActive(e.target.checked)}
              disabled={isSubmitting}
            />
            <span>Active Category</span>
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
              ? 'Update Category'
              : 'Add Category'}
          </button>
        </div>
      </form>
    </AdminModal>
  );
};

export default CategoryModal;
