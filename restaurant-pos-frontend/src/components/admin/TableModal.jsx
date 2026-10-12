import React, { useState, useEffect } from 'react';
import AdminModal from './AdminModal';

export const TableModal = ({
  isOpen,
  onClose,
  onSubmit,
  initialData,
  isSubmitting,
}) => {
  const [tableNumber, setTableNumber] = useState('');
  const [capacity, setCapacity] = useState('');
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (initialData) {
      setTableNumber(initialData.tableNumber !== undefined ? String(initialData.tableNumber) : '');
      setCapacity(initialData.capacity !== undefined ? String(initialData.capacity) : '');
    } else {
      setTableNumber('');
      setCapacity('');
    }
    setErrors({});
  }, [initialData, isOpen]);

  const validate = () => {
    const newErrors = {};

    if (!tableNumber && tableNumber !== 0) {
      newErrors.tableNumber = 'Table number is required.';
    } else {
      const num = Number(tableNumber);
      if (isNaN(num) || !Number.isInteger(num)) {
        newErrors.tableNumber = 'Table number must be a valid integer.';
      } else if (num < 1) {
        newErrors.tableNumber = 'Table number must be at least 1.';
      }
    }

    if (!capacity && capacity !== 0) {
      newErrors.capacity = 'Capacity is required.';
    } else {
      const cap = Number(capacity);
      if (isNaN(cap) || !Number.isInteger(cap)) {
        newErrors.capacity = 'Capacity must be a valid integer.';
      } else if (cap < 1) {
        newErrors.capacity = 'Capacity must be at least 1.';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;

    onSubmit({
      tableNumber: Number(tableNumber),
      capacity: Number(capacity),
    });
  };

  const isEditing = !!initialData;

  return (
    <AdminModal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? 'Edit Table' : 'Add New Table'}
    >
      <form onSubmit={handleSubmit} className="admin-form" noValidate>
        <div className="form-group">
          <label htmlFor="tbl-number">Table Number *</label>
          <input
            id="tbl-number"
            type="number"
            min="1"
            step="1"
            className={`form-input ${errors.tableNumber ? 'input-error' : ''}`}
            placeholder="e.g. 1, 2, 10"
            value={tableNumber}
            onChange={(e) => setTableNumber(e.target.value)}
            disabled={isSubmitting}
            autoFocus
          />
          {errors.tableNumber && (
            <span className="error-message">{errors.tableNumber}</span>
          )}
        </div>

        <div className="form-group">
          <label htmlFor="tbl-capacity">Seating Capacity *</label>
          <input
            id="tbl-capacity"
            type="number"
            min="1"
            step="1"
            className={`form-input ${errors.capacity ? 'input-error' : ''}`}
            placeholder="e.g. 2, 4, 8"
            value={capacity}
            onChange={(e) => setCapacity(e.target.value)}
            disabled={isSubmitting}
          />
          {errors.capacity && (
            <span className="error-message">{errors.capacity}</span>
          )}
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
              ? 'Update Table'
              : 'Add Table'}
          </button>
        </div>
      </form>
    </AdminModal>
  );
};

export default TableModal;
