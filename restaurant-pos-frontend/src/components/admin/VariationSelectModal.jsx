import React, { useEffect } from 'react';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const VariationSelectModal = ({
  isOpen,
  onClose,
  menuItem,
  onSelectVariation,
}) => {
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen || !menuItem) return null;

  const variations = Array.isArray(menuItem.variations) ? menuItem.variations : [];

  const handleSelect = (variation) => {
    if (variation.isAvailable === false) return;
    onSelectVariation(variation);
    onClose();
  };

  return (
    <div
      className="modal-backdrop variation-modal-backdrop"
      style={{
        zIndex: 10150,
        backgroundColor: 'rgba(0, 0, 0, 0.65)',
      }}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="variation-select-title"
    >
      <div
        className="modal-content variation-select-dialog"
        style={{
          maxWidth: '460px',
          width: '90%',
          backgroundColor: '#ffffff',
          borderRadius: '12px',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className="modal-header d-flex justify-content-between align-items-center"
          style={{
            padding: '1rem 1.25rem',
            borderBottom: '1px solid #e5e7eb',
            backgroundColor: '#f9fafb',
          }}
        >
          <div>
            <h3
              id="variation-select-title"
              className="modal-title"
              style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: '#111827' }}
            >
              Select Portion / Size
            </h3>
            <p style={{ margin: '0.2rem 0 0', fontSize: '0.85rem', color: '#6b7280' }}>
              {menuItem.name} {menuItem.categoryName ? `• ${menuItem.categoryName}` : ''}
            </p>
          </div>
          <button
            type="button"
            className="btn-modal-close"
            onClick={onClose}
            aria-label="Close"
            style={{
              background: 'none',
              border: 'none',
              fontSize: '1.25rem',
              cursor: 'pointer',
              color: '#6b7280',
            }}
          >
            ✕
          </button>
        </div>

        <div className="modal-body" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {variations.length === 0 ? (
              <p className="text-muted text-center py-3">No variations configured for this item.</p>
            ) : (
              variations.map((v) => {
                const isAvail = v.isAvailable !== false;
                return (
                  <button
                    key={v.id || v.name}
                    type="button"
                    className={`variation-option-card ${!isAvail ? 'disabled' : ''}`}
                    onClick={() => handleSelect(v)}
                    disabled={!isAvail}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '0.9rem 1.1rem',
                      borderRadius: '8px',
                      border: isAvail ? '1.5px solid #d1d5db' : '1.5px dashed #e5e7eb',
                      backgroundColor: isAvail ? '#ffffff' : '#f3f4f6',
                      cursor: isAvail ? 'pointer' : 'not-allowed',
                      textAlign: 'left',
                      transition: 'all 0.15s ease-in-out',
                      width: '100%',
                    }}
                    onMouseEnter={(e) => {
                      if (isAvail) {
                        e.currentTarget.style.borderColor = '#b45309';
                        e.currentTarget.style.backgroundColor = '#fffbeb';
                      }
                    }}
                    onMouseLeave={(e) => {
                      if (isAvail) {
                        e.currentTarget.style.borderColor = '#d1d5db';
                        e.currentTarget.style.backgroundColor = '#ffffff';
                      }
                    }}
                  >
                    <div>
                      <span
                        style={{
                          fontSize: '1rem',
                          fontWeight: 600,
                          color: isAvail ? '#1f2937' : '#9ca3af',
                          display: 'block',
                        }}
                      >
                        {v.name}
                      </span>
                      {!isAvail && (
                        <span
                          className="badge badge-secondary"
                          style={{
                            fontSize: '0.7rem',
                            marginTop: '0.2rem',
                            display: 'inline-block',
                          }}
                        >
                          Out of stock
                        </span>
                      )}
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <span
                        style={{
                          fontSize: '1.1rem',
                          fontWeight: 700,
                          color: isAvail ? '#b45309' : '#9ca3af',
                        }}
                      >
                        {CURRENCY_SYMBOL}
                        {Number(v.price || 0).toFixed(2)}
                      </span>
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </div>

        <div
          className="modal-footer"
          style={{
            padding: '0.75rem 1.25rem',
            borderTop: '1px solid #e5e7eb',
            backgroundColor: '#f9fafb',
            display: 'flex',
            justifyContent: 'flex-end',
          }}
        >
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={onClose}
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
};

export default VariationSelectModal;
