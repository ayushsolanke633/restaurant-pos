import React, { useState, useEffect, useCallback } from 'react';
import inventoryService from '../../services/inventoryService';

export const InventoryHistoryModal = ({ item, isOpen, onClose }) => {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchTransactions = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      let data = [];
      if (item && item.id) {
        data = await inventoryService.getItemTransactions(item.id);
      } else {
        data = await inventoryService.getAllTransactions();
      }
      setTransactions(data || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to fetch transaction history');
    } finally {
      setLoading(false);
    }
  }, [item]);

  useEffect(() => {
    if (isOpen) {
      fetchTransactions();
    }
  }, [isOpen, fetchTransactions]);

  if (!isOpen) return null;

  const formatDate = (isoString) => {
    if (!isoString) return '—';
    try {
      const date = new Date(isoString);
      return date.toLocaleString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const getTxTypeBadgeClass = (type) => {
    switch (type) {
      case 'PURCHASE':
        return 'badge-purchase';
      case 'ADJUSTMENT':
        return 'badge-adjustment';
      case 'WASTAGE':
        return 'badge-wastage';
      default:
        return 'badge-secondary';
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-container history-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>
            Transaction Audit History — {item ? item.name : 'All Inventory'}
          </h3>
          <button className="modal-close-btn" onClick={onClose} aria-label="Close">Close</button>
        </div>

        <div className="modal-body p-4">
          {error && <div className="alert alert-danger mb-3">{error}</div>}

          {loading ? (
            <div className="loading-container py-4 text-center">
              <div className="spinner mb-2"></div>
              <p className="text-muted">Loading transaction records...</p>
            </div>
          ) : transactions.length === 0 ? (
            <div className="empty-state p-4 text-center">
              <p className="text-muted m-0">No transaction records found for this item.</p>
            </div>
          ) : (
            <div className="table-responsive card-flush">
              <table className="data-table data-table-sm">
                <thead>
                  <tr>
                    <th>Date & Time</th>
                    {!item && <th>Item Name</th>}
                    <th>Type</th>
                    <th className="text-right">Quantity</th>
                    <th>Reference</th>
                    <th>User</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((tx) => {
                    const isPositive = tx.type === 'PURCHASE' || (tx.type === 'ADJUSTMENT' && Number(tx.quantity) > 0);
                    return (
                      <tr key={tx.id}>
                        <td className="text-xs text-muted whitespace-nowrap">{formatDate(tx.createdAt)}</td>
                        {!item && <td className="font-semibold">{tx.inventoryItemName || '—'}</td>}
                        <td>
                          <span className={`status-badge ${getTxTypeBadgeClass(tx.type)}`}>
                            {tx.type}
                          </span>
                        </td>
                        <td className={`text-right font-extrabold ${isPositive ? 'text-success' : 'text-danger'}`}>
                          {isPositive ? `+${Number(tx.quantity).toFixed(3)}` : Number(tx.quantity).toFixed(3)}
                        </td>
                        <td className="text-xs text-muted">
                          {tx.referenceType || tx.referenceId ? `${tx.referenceType || 'Ref'} #${tx.referenceId || ''}` : '—'}
                        </td>
                        <td className="text-xs">{tx.createdByName || 'Admin'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

export default InventoryHistoryModal;
