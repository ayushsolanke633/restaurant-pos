import React, { useState, useEffect } from 'react';
import inventoryService from '../../services/inventoryService';

export const InventoryModal = ({ item, isOpen, onClose, onSuccess }) => {
  const [name, setName] = useState('');
  const [unit, setUnit] = useState('kg');
  const [minimumQuantity, setMinimumQuantity] = useState('');
  const [costPerUnit, setCostPerUnit] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const isEditing = Boolean(item);

  useEffect(() => {
    if (isOpen) {
      setError('');
      if (item) {
        setName(item.name || '');
        setUnit(item.unit || 'kg');
        setMinimumQuantity(item.minimumQuantity !== undefined && item.minimumQuantity !== null ? String(item.minimumQuantity) : '0');
        setCostPerUnit(item.costPerUnit !== undefined && item.costPerUnit !== null ? String(item.costPerUnit) : '0');
        setIsActive(item.isActive !== undefined ? item.isActive : true);
      } else {
        setName('');
        setUnit('kg');
        setMinimumQuantity('5.0');
        setCostPerUnit('0.00');
        setIsActive(true);
      }
    }
  }, [isOpen, item]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    // Validations
    if (!name.trim()) {
      setError('Item name is required.');
      return;
    }
    if (!unit.trim()) {
      setError('Unit of measurement is required.');
      return;
    }
    if (minimumQuantity === '' || Number(minimumQuantity) < 0) {
      setError('Minimum stock quantity must be 0 or greater.');
      return;
    }
    if (costPerUnit === '' || Number(costPerUnit) < 0) {
      setError('Cost per unit must be 0 or greater.');
      return;
    }

    const payload = {
      name: name.trim(),
      unit: unit.trim(),
      minimumQuantity: Number(minimumQuantity),
      costPerUnit: Number(costPerUnit),
      isActive: Boolean(isActive),
    };

    setIsSubmitting(true);
    try {
      if (isEditing) {
        await inventoryService.updateInventoryItem(item.id, payload);
      } else {
        await inventoryService.createInventoryItem(payload);
      }
      onSuccess();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to save inventory item details');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-container inventory-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{isEditing ? `Edit Inventory Item — ${item.name}` : 'Add New Inventory Item'}</h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close">Close</button>
        </div>

        <form onSubmit={handleSubmit} className="modal-form">
          <div className="modal-body">
            {error && <div className="alert alert-danger mb-3">{error}</div>}

            <div className="form-group mb-3">
              <label className="form-label">Item Name *</label>
              <input
                type="text"
                className="form-input"
                placeholder="e.g. Basmati Rice, Cooking Oil, Tomatoes"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>

            <div className="grid grid-2 mb-3">
              <div className="form-group">
                <label className="form-label">Unit of Measurement *</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. kg, liters, pcs, boxes"
                  value={unit}
                  onChange={(e) => setUnit(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Cost per Unit (₹) *</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  className="form-input"
                  placeholder="0.00"
                  value={costPerUnit}
                  onChange={(e) => setCostPerUnit(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="form-group mb-3">
              <label className="form-label">Minimum Stock Level (Reorder Threshold) *</label>
              <input
                type="number"
                step="0.001"
                min="0"
                className="form-input"
                placeholder="5.000"
                value={minimumQuantity}
                onChange={(e) => setMinimumQuantity(e.target.value)}
                required
              />
              <small className="form-help-text">
                When stock falls at or below this level, item will be flagged as Low Stock.
              </small>
            </div>

            {!isEditing && (
              <div className="alert alert-info mb-3">
                Initial stock starts at <strong>0.000</strong>. Use <strong>"Stock Transaction"</strong> to purchase or add initial stock.
              </div>
            )}

            {isEditing && (
              <div className="form-group mb-3">
                <label className="form-checkbox-label">
                  <input
                    type="checkbox"
                    checked={isActive}
                    onChange={(e) => setIsActive(e.target.checked)}
                  />
                  <span>Item is Active</span>
                </label>
              </div>
            )}
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <span className="spinner-sm"></span> Saving...
                </>
              ) : isEditing ? (
                'Update Inventory Item'
              ) : (
                'Create Inventory Item'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default InventoryModal;
