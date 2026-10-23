import React, { useState, useEffect } from 'react';
import settingsService from '../../services/settingsService';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const ThermalReceipt = ({ bill, order, settings: propSettings, paperWidth = '80mm' }) => {
  const [settings, setSettings] = useState(propSettings || null);

  useEffect(() => {
    if (!propSettings) {
      settingsService.getSettings()
        .then((data) => setSettings(data))
        .catch(() => {});
    } else {
      setSettings(propSettings);
    }
  }, [propSettings]);

  if (!bill && !order) return null;

  // Derive values from bill or order fallback
  const billNo = bill?.billNumber || 'DRAFT';
  const orderId = bill?.orderId || order?.id || '—';
  const createdAtStr = bill?.createdAt || order?.createdAt;
  const createdAt = createdAtStr ? new Date(createdAtStr) : new Date();

  const formattedDate = createdAt.toLocaleDateString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });

  const formattedTime = createdAt.toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });

  const orderType = (order?.orderType || bill?.orderType || 'DINE_IN').toUpperCase();
  const tableName = order?.tableName || ((order?.tableNumber !== undefined && order?.tableNumber !== null) ? `Table ${order.tableNumber}` : order?.tableId ? `Table ${order.tableId}` : null);
  const waiterName = order?.waiterName || (order?.waiterId ? `Waiter #${order.waiterId}` : null);
  const items = order?.items || bill?.items || [];

  const subtotal = Number(bill?.subtotal ?? order?.subtotal ?? 0);
  const taxAmount = Number(bill?.taxAmount ?? order?.taxAmount ?? 0);
  const discountAmount = Number(bill?.discountAmount ?? order?.discountAmount ?? 0);
  const totalAmount = Number(bill?.totalAmount ?? order?.totalAmount ?? subtotal + taxAmount - discountAmount);

  const restaurantName = settings?.restaurantName || 'AnnaDhara';
  const address = settings?.address || '';
  const phone = settings?.phone || '';
  const gstNumber = settings?.gstNumber || '';
  const receiptHeader = settings?.receiptHeader || '';
  const receiptFooter = settings?.receiptFooter || 'Thank you for dining with us! Please visit again.';

  const is58mm = paperWidth === '58mm';

  return (
    <div className={`thermal-receipt-wrapper ${is58mm ? 'width-58mm' : 'width-80mm'}`}>
      <div className="thermal-receipt">
        {/* Restaurant Header */}
        <div className="thermal-header text-center">
          <h2 className="restaurant-title">{restaurantName}</h2>
          {address && <p className="restaurant-sub">{address}</p>}
          {phone && <p className="restaurant-sub">Phone: {phone}</p>}
          {gstNumber && <p className="restaurant-sub">GSTIN: {gstNumber}</p>}
          {receiptHeader && <p className="receipt-custom-header">{receiptHeader}</p>}
        </div>

        <div className="thermal-divider-double"></div>

        {/* Bill Title */}
        <div className="thermal-bill-title text-center font-bold">
          <span>TAX INVOICE / BILL</span>
        </div>

        <div className="thermal-divider-single"></div>

        {/* Bill Metadata Grid */}
        <div className="thermal-meta-grid">
          <div className="meta-row">
            <span className="lbl">Bill No:</span>
            <span className="val font-bold">{billNo}</span>
          </div>
          <div className="meta-row">
            <span className="lbl">Order No:</span>
            <span className="val">#{orderId}</span>
          </div>
          <div className="meta-row">
            <span className="lbl">Date:</span>
            <span className="val">{formattedDate} {formattedTime}</span>
          </div>
          <div className="meta-row">
            <span className="lbl">Type:</span>
            <span className="val font-bold">{orderType === 'DINE_IN' ? 'DINE IN' : 'TAKEAWAY'}</span>
          </div>
          {tableName && (
            <div className="meta-row">
              <span className="lbl">Table:</span>
              <span className="val font-bold">{tableName}</span>
            </div>
          )}
          {waiterName && (
            <div className="meta-row">
              <span className="lbl">Waiter:</span>
              <span className="val">{waiterName}</span>
            </div>
          )}
        </div>

        <div className="thermal-divider-single"></div>

        {/* Items Table */}
        <table className="thermal-items-table">
          <thead>
            <tr>
              <th className="th-item">ITEM</th>
              <th className="th-qty text-center">QTY</th>
              <th className="th-price text-right">RATE</th>
              <th className="th-amount text-right">AMOUNT</th>
            </tr>
          </thead>
          <tbody>
            {items && items.length > 0 ? (
              items.map((item, idx) => (
                <tr key={idx}>
                  <td className="td-item">
                    <div className="item-name font-bold">{item.menuItemName || `Item #${item.menuItemId}`}</div>
                    {item.notes && <div className="item-note">Note: {item.notes}</div>}
                  </td>
                  <td className="td-qty text-center">{item.quantity}</td>
                  <td className="td-price text-right">{Number(item.unitPrice || 0).toFixed(2)}</td>
                  <td className="td-amount text-right">{Number(item.subtotal || item.unitPrice * item.quantity).toFixed(2)}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan="4" className="text-center py-2 text-muted">
                  No items listed
                </td>
              </tr>
            )}
          </tbody>
        </table>

        <div className="thermal-divider-single"></div>

        {/* Totals Summary */}
        <div className="thermal-totals-section">
          <div className="total-row">
            <span>Subtotal:</span>
            <span>{CURRENCY_SYMBOL}{subtotal.toFixed(2)}</span>
          </div>
          {discountAmount > 0 && (
            <div className="total-row">
              <span>Discount:</span>
              <span>-{CURRENCY_SYMBOL}{discountAmount.toFixed(2)}</span>
            </div>
          )}
          {taxAmount > 0 && (
            <div className="total-row">
              <span>Tax:</span>
              <span>{CURRENCY_SYMBOL}{taxAmount.toFixed(2)}</span>
            </div>
          )}
          <div className="thermal-divider-single"></div>
          <div className="total-row grand-total-row font-bold">
            <span>GRAND TOTAL:</span>
            <span>{CURRENCY_SYMBOL}{totalAmount.toFixed(2)}</span>
          </div>
        </div>

        <div className="thermal-divider-double"></div>

        {/* Footer Greetings */}
        <div className="thermal-footer text-center">
          {receiptFooter && <p className="footer-msg">{receiptFooter}</p>}
          <p className="footer-system font-mono">=== POS SYSTEM GENERATED RECEIPT ===</p>
        </div>
      </div>
    </div>
  );
};

export default ThermalReceipt;
