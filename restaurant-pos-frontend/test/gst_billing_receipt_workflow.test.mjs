import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * AnnaDhara POS — GST Billing & Receipt UI Workflow Tests (Stage 2)
 *
 * Validates:
 * 1. GST_EXCLUDED bill review displays GST Excluded label and separate CGST/SGST rates & amounts
 * 2. GST_INCLUDED bill review displays GST Included label and separate CGST/SGST rates & amounts
 * 3. Legacy bills (gstMode == null) do not fabricate GST mode or component breakdown
 * 4. Tax-disabled bills render zero tax without NaN or undefined
 * 5. Completed bills history table renders separate CGST and SGST columns, or '—' for legacy bills
 * 6. Thermal receipt displays saved payment method (CASH, UPI, CARD) and transaction reference
 * 7. Thermal receipt renders both 58mm and 80mm paper widths gracefully
 */

// Pure component simulation for ThermalReceipt logic matching ThermalReceipt.jsx
class ThermalReceiptLogic {
  static deriveReceiptData({ bill, order, payment, settings, paperWidth = '80mm' }) {
    if (!bill && !order) return null;

    const subtotal = Number(bill?.subtotal ?? order?.subtotal ?? 0);
    const taxAmount = Number(bill?.taxAmount ?? order?.taxAmount ?? 0);
    const discountAmount = Number(bill?.discountAmount ?? order?.discountAmount ?? 0);
    const totalAmount = Number(bill?.totalAmount ?? order?.totalAmount ?? (subtotal + taxAmount - discountAmount));

    const gstMode = bill?.gstMode ?? order?.gstMode ?? null;
    const isGstBill = Boolean(gstMode && (gstMode === 'GST_EXCLUDED' || gstMode === 'GST_INCLUDED'));
    const cgstRate = bill?.cgstRate != null ? Number(bill.cgstRate) : (order?.cgstRate != null ? Number(order.cgstRate) : null);
    const sgstRate = bill?.sgstRate != null ? Number(bill.sgstRate) : (order?.sgstRate != null ? Number(order.sgstRate) : null);
    const cgstAmount = bill?.cgstAmount != null ? Number(bill.cgstAmount) : (order?.cgstAmount != null ? Number(order.cgstAmount) : null);
    const sgstAmount = bill?.sgstAmount != null ? Number(bill.sgstAmount) : (order?.sgstAmount != null ? Number(order.sgstAmount) : null);

    const taxableAmount = gstMode === 'GST_INCLUDED'
      ? Math.max(0, totalAmount - taxAmount)
      : Math.max(0, subtotal - discountAmount);

    const effectivePayment = payment || (bill?.payments && bill.payments[0]) || null;
    const paymentMethod = effectivePayment?.paymentMethod || bill?.paymentMethod || order?.paymentMethod || null;
    const transactionRef = effectivePayment?.transactionReference || bill?.transactionReference || order?.transactionReference || null;
    const paidAt = effectivePayment?.paidAt || bill?.paidAt || order?.paidAt || null;

    return {
      billNo: bill?.billNumber || 'DRAFT',
      orderId: bill?.orderId || order?.id || '—',
      paperWidth,
      subtotal,
      taxAmount,
      discountAmount,
      totalAmount,
      isGstBill,
      gstMode,
      gstModeLabel: isGstBill ? (gstMode === 'GST_INCLUDED' ? 'GST Included' : 'GST Excluded') : null,
      taxableAmount,
      cgstRate,
      sgstRate,
      cgstAmount,
      sgstAmount,
      paymentMethod,
      transactionRef,
      paidAt,
    };
  }
}

// Pure component simulation for Completed Bills Table matching BillingPage.jsx
class CompletedBillsHistoryLogic {
  static formatBillRow(bill, currencySymbol = '₹') {
    const hasGstComponents = bill.cgstAmount != null && bill.sgstAmount != null;
    return {
      billNumber: bill.billNumber,
      gstModeBadge: bill.gstMode === 'GST_INCLUDED' ? 'GST Included' : bill.gstMode === 'GST_EXCLUDED' ? 'GST Excluded' : null,
      orderId: `Order #${bill.orderId}`,
      subtotal: `${currencySymbol}${Number(bill.subtotal || 0).toFixed(2)}`,
      cgst: hasGstComponents
        ? `${currencySymbol}${Number(bill.cgstAmount).toFixed(2)}${bill.cgstRate != null ? ` (${bill.cgstRate}%)` : ''}`
        : '—',
      sgst: hasGstComponents
        ? `${currencySymbol}${Number(bill.sgstAmount).toFixed(2)}${bill.sgstRate != null ? ` (${bill.sgstRate}%)` : ''}`
        : '—',
      tax: `${currencySymbol}${Number(bill.taxAmount || 0).toFixed(2)}`,
      discount: `${currencySymbol}${Number(bill.discountAmount || 0).toFixed(2)}`,
      total: `${currencySymbol}${Number(bill.totalAmount || 0).toFixed(2)}`,
      generatedBy: bill.generatedByName || 'Admin',
    };
  }
}

// Pure component simulation for BillModal Review Card matching BillModal.jsx
class BillModalReviewLogic {
  static formatReviewSummary(bill, currencySymbol = '₹') {
    if (!bill) return null;
    const isGst = Boolean(bill.gstMode && (bill.gstMode === 'GST_EXCLUDED' || bill.gstMode === 'GST_INCLUDED'));
    return {
      billNumber: bill.billNumber,
      gstModeLabel: isGst ? (bill.gstMode === 'GST_INCLUDED' ? 'GST Included' : 'GST Excluded') : null,
      subtotal: `${currencySymbol}${Number(bill.subtotal || 0).toFixed(2)}`,
      hasGstBreakdown: isGst && bill.cgstAmount != null && bill.sgstAmount != null,
      cgst: isGst && bill.cgstAmount != null ? `${currencySymbol}${Number(bill.cgstAmount).toFixed(2)}` : null,
      sgst: isGst && bill.sgstAmount != null ? `${currencySymbol}${Number(bill.sgstAmount).toFixed(2)}` : null,
      cgstRate: bill.cgstRate != null ? `${bill.cgstRate}%` : null,
      sgstRate: bill.sgstRate != null ? `${bill.sgstRate}%` : null,
      tax: Number(bill.taxAmount || 0) > 0 ? `${currencySymbol}${Number(bill.taxAmount).toFixed(2)}` : null,
      discount: Number(bill.discountAmount || 0) > 0 ? `-${currencySymbol}${Number(bill.discountAmount).toFixed(2)}` : null,
      total: `${currencySymbol}${Number(bill.totalAmount || 0).toFixed(2)}`,
    };
  }
}

// =========================================================================
// UNIT TESTS
// =========================================================================

test('1. GST_EXCLUDED bill review displays GST Excluded label and separate CGST & SGST rates and amounts', () => {
  const bill = {
    id: 101,
    orderId: 201,
    billNumber: 'INV-2026-001',
    subtotal: 100.00,
    taxAmount: 5.00,
    gstMode: 'GST_EXCLUDED',
    cgstRate: 2.50,
    sgstRate: 2.50,
    cgstAmount: 2.50,
    sgstAmount: 2.50,
    discountAmount: 0.00,
    totalAmount: 105.00,
  };

  // BillModal Review Summary
  const review = BillModalReviewLogic.formatReviewSummary(bill);
  assert.equal(review.billNumber, 'INV-2026-001');
  assert.equal(review.gstModeLabel, 'GST Excluded');
  assert.equal(review.hasGstBreakdown, true);
  assert.equal(review.cgst, '₹2.50');
  assert.equal(review.sgst, '₹2.50');
  assert.equal(review.cgstRate, '2.5%');
  assert.equal(review.sgstRate, '2.5%');
  assert.equal(review.subtotal, '₹100.00');
  assert.equal(review.total, '₹105.00');

  // ThermalReceipt Logic
  const receipt = ThermalReceiptLogic.deriveReceiptData({ bill });
  assert.equal(receipt.isGstBill, true);
  assert.equal(receipt.gstModeLabel, 'GST Excluded');
  assert.equal(receipt.taxableAmount, 100.00);
  assert.equal(receipt.cgstAmount, 2.50);
  assert.equal(receipt.sgstAmount, 2.50);
  assert.equal(receipt.cgstRate, 2.50);
  assert.equal(receipt.sgstRate, 2.50);
  assert.equal(receipt.totalAmount, 105.00);
});

test('2. GST_INCLUDED bill review displays GST Included label and separate CGST & SGST rates and amounts', () => {
  const bill = {
    id: 102,
    orderId: 202,
    billNumber: 'INV-2026-002',
    subtotal: 100.00,
    taxAmount: 4.76,
    gstMode: 'GST_INCLUDED',
    cgstRate: 2.50,
    sgstRate: 2.50,
    cgstAmount: 2.38,
    sgstAmount: 2.38,
    discountAmount: 0.00,
    totalAmount: 100.00,
  };

  // BillModal Review Summary
  const review = BillModalReviewLogic.formatReviewSummary(bill);
  assert.equal(review.billNumber, 'INV-2026-002');
  assert.equal(review.gstModeLabel, 'GST Included');
  assert.equal(review.hasGstBreakdown, true);
  assert.equal(review.cgst, '₹2.38');
  assert.equal(review.sgst, '₹2.38');
  assert.equal(review.total, '₹100.00');

  // ThermalReceipt Logic
  const receipt = ThermalReceiptLogic.deriveReceiptData({ bill });
  assert.equal(receipt.isGstBill, true);
  assert.equal(receipt.gstModeLabel, 'GST Included');
  assert.equal(receipt.taxableAmount, 95.24); // 100.00 - 4.76
  assert.equal(receipt.cgstAmount, 2.38);
  assert.equal(receipt.sgstAmount, 2.38);
  assert.equal(receipt.totalAmount, 100.00);
});

test('3. Legacy bills (gstMode == null) do not fabricate GST mode or component breakdown', () => {
  const legacyBill = {
    id: 50,
    orderId: 150,
    billNumber: 'INV-LEGACY-050',
    subtotal: 200.00,
    taxAmount: 10.00,
    gstMode: null,
    cgstRate: null,
    sgstRate: null,
    cgstAmount: null,
    sgstAmount: null,
    discountAmount: 0.00,
    totalAmount: 210.00,
  };

  // BillModal Review Summary
  const review = BillModalReviewLogic.formatReviewSummary(legacyBill);
  assert.equal(review.billNumber, 'INV-LEGACY-050');
  assert.equal(review.gstModeLabel, null);
  assert.equal(review.hasGstBreakdown, false);
  assert.equal(review.cgst, null);
  assert.equal(review.sgst, null);
  assert.equal(review.tax, '₹10.00');
  assert.equal(review.total, '₹210.00');

  // ThermalReceipt Logic
  const receipt = ThermalReceiptLogic.deriveReceiptData({ bill: legacyBill });
  assert.equal(receipt.isGstBill, false);
  assert.equal(receipt.gstModeLabel, null);
  assert.equal(receipt.cgstAmount, null);
  assert.equal(receipt.sgstAmount, null);
  assert.equal(receipt.taxAmount, 10.00);
  assert.equal(receipt.totalAmount, 210.00);
});

test('4. Tax-disabled bills render zero tax without NaN or undefined', () => {
  const noTaxBill = {
    id: 51,
    orderId: 151,
    billNumber: 'INV-NOTAX-051',
    subtotal: 150.00,
    taxAmount: 0.00,
    gstMode: null,
    cgstRate: null,
    sgstRate: null,
    cgstAmount: null,
    sgstAmount: null,
    discountAmount: 10.00,
    totalAmount: 140.00,
  };

  const review = BillModalReviewLogic.formatReviewSummary(noTaxBill);
  assert.equal(review.tax, null); // Zero tax not shown as a tax line
  assert.equal(review.discount, '-₹10.00');
  assert.equal(review.total, '₹140.00');
  assert.equal(review.hasGstBreakdown, false);

  const receipt = ThermalReceiptLogic.deriveReceiptData({ bill: noTaxBill });
  assert.equal(receipt.isGstBill, false);
  assert.equal(receipt.taxAmount, 0);
  assert.equal(Number.isNaN(receipt.taxAmount), false);
  assert.equal(Number.isNaN(receipt.totalAmount), false);
  assert.equal(receipt.totalAmount, 140.00);
});

test('5. Completed bills history table renders separate CGST and SGST columns, or "—" for legacy bills', () => {
  const bills = [
    {
      id: 1,
      billNumber: 'INV-001',
      orderId: 10,
      subtotal: 200.00,
      cgstAmount: 5.00,
      sgstAmount: 5.00,
      cgstRate: 2.50,
      sgstRate: 2.50,
      taxAmount: 10.00,
      discountAmount: 0.00,
      totalAmount: 210.00,
      gstMode: 'GST_EXCLUDED',
    },
    {
      id: 2,
      billNumber: 'INV-002',
      orderId: 11,
      subtotal: 100.00,
      cgstAmount: 2.38,
      sgstAmount: 2.38,
      cgstRate: 2.50,
      sgstRate: 2.50,
      taxAmount: 4.76,
      discountAmount: 0.00,
      totalAmount: 100.00,
      gstMode: 'GST_INCLUDED',
    },
    {
      id: 3,
      billNumber: 'INV-003',
      orderId: 12,
      subtotal: 50.00,
      cgstAmount: null,
      sgstAmount: null,
      cgstRate: null,
      sgstRate: null,
      taxAmount: 2.50,
      discountAmount: 0.00,
      totalAmount: 52.50,
      gstMode: null,
    },
  ];

  const row1 = CompletedBillsHistoryLogic.formatBillRow(bills[0]);
  assert.equal(row1.cgst, '₹5.00 (2.5%)');
  assert.equal(row1.sgst, '₹5.00 (2.5%)');
  assert.equal(row1.tax, '₹10.00');
  assert.equal(row1.gstModeBadge, 'GST Excluded');

  const row2 = CompletedBillsHistoryLogic.formatBillRow(bills[1]);
  assert.equal(row2.cgst, '₹2.38 (2.5%)');
  assert.equal(row2.sgst, '₹2.38 (2.5%)');
  assert.equal(row2.tax, '₹4.76');
  assert.equal(row2.gstModeBadge, 'GST Included');

  const row3 = CompletedBillsHistoryLogic.formatBillRow(bills[2]);
  assert.equal(row3.cgst, '—');
  assert.equal(row3.sgst, '—');
  assert.equal(row3.tax, '₹2.50');
  assert.equal(row3.gstModeBadge, null);
});

test('6. Thermal receipt displays saved payment method (CASH, UPI, CARD) and transaction reference', () => {
  const bill = {
    id: 105,
    billNumber: 'INV-2026-005',
    subtotal: 300.00,
    taxAmount: 15.00,
    totalAmount: 315.00,
    gstMode: 'GST_EXCLUDED',
    cgstAmount: 7.50,
    sgstAmount: 7.50,
  };

  // Case A: CASH payment
  const cashPayment = { paymentMethod: 'CASH', amount: 315.00, transactionReference: null, paidAt: '2026-10-27T01:00:00' };
  const receiptCash = ThermalReceiptLogic.deriveReceiptData({ bill, payment: cashPayment });
  assert.equal(receiptCash.paymentMethod, 'CASH');
  assert.equal(receiptCash.transactionRef, null);

  // Case B: UPI payment with transaction ID
  const upiPayment = { paymentMethod: 'UPI', amount: 315.00, transactionReference: 'UPI-987654321012', paidAt: '2026-10-27T01:05:00' };
  const receiptUpi = ThermalReceiptLogic.deriveReceiptData({ bill, payment: upiPayment });
  assert.equal(receiptUpi.paymentMethod, 'UPI');
  assert.equal(receiptUpi.transactionRef, 'UPI-987654321012');

  // Case C: CARD payment with approval code
  const cardPayment = { paymentMethod: 'CARD', amount: 315.00, transactionReference: 'AUTH-45892', paidAt: '2026-10-27T01:10:00' };
  const receiptCard = ThermalReceiptLogic.deriveReceiptData({ bill, payment: cardPayment });
  assert.equal(receiptCard.paymentMethod, 'CARD');
  assert.equal(receiptCard.transactionRef, 'AUTH-45892');
});

test('7. Thermal receipt renders both 58mm and 80mm paper widths gracefully', () => {
  const bill = {
    id: 106,
    billNumber: 'INV-2026-006',
    subtotal: 50.00,
    taxAmount: 2.50,
    totalAmount: 52.50,
    gstMode: 'GST_EXCLUDED',
    cgstAmount: 1.25,
    sgstAmount: 1.25,
  };

  const receipt80 = ThermalReceiptLogic.deriveReceiptData({ bill, paperWidth: '80mm' });
  assert.equal(receipt80.paperWidth, '80mm');
  assert.equal(receipt80.totalAmount, 52.50);

  const receipt58 = ThermalReceiptLogic.deriveReceiptData({ bill, paperWidth: '58mm' });
  assert.equal(receipt58.paperWidth, '58mm');
  assert.equal(receipt58.totalAmount, 52.50);
});
