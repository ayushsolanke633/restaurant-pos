import React, { useState, useEffect } from 'react';
import inventoryService from '../../services/inventoryService';

export const InventoryTransactionModal = ({ item, isOpen, onClose, onSuccess }) => {
  const [txType, setTxType] = useState('PURCHASE'); // 'PURCHASE', 'ADJUSTMENT', 'WASTAGE'
  const [quantityInput, setQuantityInput] = useState('');
  const [adjustDirection, setAdjustDirection] = useState('ADD'); // 'ADD' or 'DEDUCT' for adjustment mode
  const [referenceType, setReferenceType] = useState('');
  const [referenceId, setReferenceId] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen) {
      setError('');
      setTxType('PURCHASE');
      setQuantityInput('');
      setAdjustDirection('ADD');
      setReferenceType('');
      setReferenceId('');
    }
  }, [isOpen, item]);

  if (!isOpen || !item) return null;

  const currentStock = Number(item.currentQuantity) || 0;
  const qtyNumber = Number(quantityInput) || 0;

  // Frontend-only calculation for live stock preview
  let netDelta = 0;
  if (txType === 'PURCHASE') {
    netDelta = Math.abs(qtyNumber);
  } else if (txType === 'WASTAGE') {
    netDelta = -Math.abs(qtyNumber);
  } else if (txType === 'ADJUSTMENT') {
    netDelta = adjustDirection === 'ADD' ? Math.abs(qtyNumber) : -Math.abs(qtyNumber);
  }

  const projectedStock = currentStock + netDelta;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!quantityInput || qtyNumber === 0) {
      setError('Please enter a valid non-zero quantity.');
      return;
    }

    if (txType === 'PURCHASE' && qtyNumber <= 0) {
      setError('Purchase quantity must be greater than 0.');
      return;
    }

    if (txType === 'WASTAGE') {
      if (qtyNumber <= 0) {
        setError('Wastage quantity must be greater than 0.');
        return;
      }
      if (qtyNumber > currentStock) {
        setError(`Wastage quantity (${qtyNumber} ${item.unit}) cannot exceed current stock (${currentStock} ${item.unit}).`);
        return;
      }
    }

    if (txType === 'ADJUSTMENT' && projectedStock < 0) {
      setError(`Adjustment would result in negative stock (${projectedStock.toFixed(3)} ${item.unit}). Not allowed.`);
      return;
    }

    // Determine final backend quantity field
    let backendQuantity = 0;
    if (txType === 'PURCHASE') {
      backendQuantity = Math.abs(qtyNumber);
    } else if (txType === 'WASTAGE') {
      backendQuantity = Math.abs(qtyNumber);
    } else if (txType === 'ADJUSTMENT') {
      backendQuantity = adjustDirection === 'ADD' ? Math.abs(qtyNumber) : -Math.abs(qtyNumber);
    }

    // Payload sending ONLY quantity, referenceType, and referenceId (NEVER sending currentQuantity)
    const payload = {
      quantity: backendQuantity,
      referenceType: referenceType.trim() || null,
      referenceId: referenceId ? Number(referenceId) : null,
    };

    setIsSubmitting(true);
    try {
      if (txType === 'PURCHASE') {
        await inventoryService.recordPurchase(item.id, payload);
      } else if (txType === 'WASTAGE') {
        await inventoryService.recordWastage(item.id, payload);
      } else if (txType === 'ADJUSTMENT') {
        await inventoryService.recordAdjustment(item.id, payload);
      }

      onSuccess();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Stock transaction failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-container transaction-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Stock Transaction — {item.name}</h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close">Close</button>
        </div>

        <form onSubmit={handleSubmit} className="modal-form">
          <div className="modal-body">
            {error && <div className="alert alert-danger mb-3">{error}</div>}

            {/* Current Stock Context Box */}
            <div className="stock-preview-box mb-3">
              <div className="preview-stat">
                <span className="stat-label">Current Stock:</span>
                <span className="stat-value font-bold">{currentStock.toFixed(3)} {item.unit}</span>
              </div>
              <div className="preview-stat">
                <span className="stat-label">Min Stock Level:</span>
                <span className="stat-value text-muted">{Number(item.minimumQuantity).toFixed(3)} {item.unit}</span>
              </div>
            </div>

            {/* Transaction Type Selector */}
            <div className="form-group mb-3">
              <label className="form-label">Transaction Type *</label>
              <div className="tx-type-selector grid grid-3 gap-2">
                <button
                  type="button"
                  className={`btn btn-outline ${txType === 'PURCHASE' ? 'btn-tx-active tx-purchase' : ''}`}
                  onClick={() => {
                    setTxType('PURCHASE');
                    setError('');
                  }}
                >
                  Purchase (+ Stock)
                </button>

                <button
                  type="button"
                  className={`btn btn-outline ${txType === 'ADJUSTMENT' ? 'btn-tx-active tx-adjustment' : ''}`}
                  onClick={() => {
                    setTxType('ADJUSTMENT');
                    setError('');
                  }}
                >
                  Adjustment (Audit)
                </button>

                <button
                  type="button"
                  className={`btn btn-outline ${txType === 'WASTAGE' ? 'btn-tx-active tx-wastage' : ''}`}
                  onClick={() => {
                    setTxType('WASTAGE');
                    setError('');
                  }}
                >
                  Wastage (- Stock)
                </button>
              </div>
            </div>

            {/* Adjustment Direction toggle if in ADJUSTMENT mode */}
            {txType === 'ADJUSTMENT' && (
              <div className="form-group mb-3">
                <label className="form-label">Adjustment Direction *</label>
                <div className="d-flex gap-2">
                  <button
                    type="button"
                    className={`btn btn-sm flex-1 ${adjustDirection === 'ADD' ? 'btn-success' : 'btn-secondary'}`}
                    onClick={() => setAdjustDirection('ADD')}
                  >
                    Add to Stock (+ Quantity)
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm flex-1 ${adjustDirection === 'DEDUCT' ? 'btn-danger' : 'btn-secondary'}`}
                    onClick={() => setAdjustDirection('DEDUCT')}
                  >
                    Reduce Stock (- Quantity)
                  </button>
                </div>
              </div>
            )}

            {/* Quantity Input */}
            <div className="form-group mb-3">
              <label className="form-label">Quantity ({item.unit}) *</label>
              <input
                type="number"
                step="0.001"
                min="0.001"
                className="form-input text-lg font-semibold"
                placeholder="e.g. 10.000"
                value={quantityInput}
                onChange={(e) => setQuantityInput(e.target.value)}
                required
              />
            </div>

            {/* Projected Stock Preview (Frontend-only) */}
            {qtyNumber > 0 && (
              <div className={`projected-stock-box mb-3 ${projectedStock < 0 ? 'projected-negative' : ''}`}>
                <span className="text-xs text-muted">Projected New Stock Preview:</span>
                <div className="d-flex align-items-center justify-content-between mt-1">
                  <span className="font-semibold">{currentStock.toFixed(3)} {item.unit}</span>
                  <span className="font-bold">{netDelta >= 0 ? `+${netDelta.toFixed(3)}` : netDelta.toFixed(3)}</span>
                  <span className={`text-base font-extrabold ${projectedStock <= Number(item.minimumQuantity) ? 'text-warning' : 'text-success'}`}>
                    = {projectedStock.toFixed(3)} {item.unit}
                  </span>
                </div>
              </div>
            )}

            {/* Reference Details */}
            <div className="grid grid-2 mb-3">
              <div className="form-group">
                <label className="form-label">Reference Type (Optional)</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. PO, Invoice, Audit"
                  value={referenceType}
                  onChange={(e) => setReferenceType(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Reference ID # (Optional)</label>
                <input
                  type="number"
                  className="form-input"
                  placeholder="e.g. 1001"
                  value={referenceId}
                  onChange={(e) => setReferenceId(e.target.value)}
                />
              </div>
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <span className="spinner-sm"></span> Processing...
                </>
              ) : (
                'Submit Transaction'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default InventoryTransactionModal;
