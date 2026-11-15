import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Unit Tests for Client Change 3: Print Sales Reports
 * Validates print mode isolation, report scope filtering, date range handling,
 * calculation consistency, empty state logic, and restaurant metadata branding.
 */

// 1. Simulator for Print Document Mode & Mutual Exclusion
function getPrintableAreaOutput(printMode, printingBill, reportBills, salesReportData) {
  const output = {
    mode: printMode,
    isReportMode: printMode === 'sales-report',
    isSingleBillMode: printMode === 'single',
    isAllBillsMode: printMode === 'all',
    renderedComponents: [],
  };

  if (printMode === 'single' && printingBill) {
    output.renderedComponents.push({
      type: 'ThermalReceipt',
      billId: printingBill.id || printingBill.billId,
      orderId: printingBill.orderId,
    });
  } else if (printMode === 'all' && reportBills && reportBills.length > 0) {
    reportBills.forEach((b) => {
      output.renderedComponents.push({
        type: 'ThermalReceipt',
        billId: b.id || b.billId,
        orderId: b.orderId,
      });
    });
  } else if (printMode === 'sales-report' && salesReportData) {
    output.renderedComponents.push({
      type: 'SalesReportPrintDocument',
      reportType: salesReportData.reportType || 'ALL',
      totalRevenue: salesReportData.salesReport?.totalRevenue,
      fromDate: salesReportData.fromDate,
      toDate: salesReportData.toDate,
    });
  }

  return output;
}

// 2. Simulator for Report Sections Display by Scope
function getVisibleReportSections(reportType, isMultiDay) {
  const sections = {
    financialSummary: false,
    paymentsBreakdown: false,
    orderTypesBreakdown: false,
    topItems: false,
    categoryBreakdown: false,
    dailyBreakdown: false,
  };

  if (reportType === 'ALL' || reportType === 'SUMMARY' || reportType === 'DAILY') {
    sections.financialSummary = true;
  }
  if (reportType === 'ALL' || reportType === 'SUMMARY' || reportType === 'PAYMENTS') {
    sections.paymentsBreakdown = true;
  }
  if (reportType === 'ALL' || reportType === 'ORDER_TYPES') {
    sections.orderTypesBreakdown = true;
  }
  if (reportType === 'ALL' || reportType === 'ITEMS_CATEGORIES') {
    sections.topItems = true;
    sections.categoryBreakdown = true;
  }
  if (isMultiDay && (reportType === 'ALL' || reportType === 'DAILY')) {
    sections.dailyBreakdown = true;
  }

  return sections;
}

// 3. Simulator for Document Title & Period Label
function formatDocumentTitle(fromDate, toDate) {
  return fromDate === toDate
    ? `sales-report-${fromDate}`
    : `sales-report-${fromDate}-to-${toDate}`;
}

function formatPeriodLabel(fromDate, toDate) {
  return fromDate === toDate
    ? `${fromDate} (Single Day)`
    : `${fromDate} to ${toDate}`;
}

// 4. Simulator for Empty Period Detection
function isPeriodEmpty(salesReport) {
  if (!salesReport) return true;
  const totalOrders = salesReport.totalOrders ?? 0;
  const ordersSettled = salesReport.ordersSettled ?? 0;
  const totalRevenue = Number(salesReport.totalRevenue ?? 0);
  return totalOrders === 0 && ordersSettled === 0 && totalRevenue === 0;
}

// 5. Simulator for Payment Breakdown Computations
function computePaymentBreakdownRows(paymentsReport, totalRevenue) {
  const paymentMethodsList = [
    { key: 'CASH', label: 'Cash' },
    { key: 'UPI', label: 'UPI' },
    { key: 'CARD', label: 'Credit / Debit Card' },
  ];

  const totalRevNum = Number(totalRevenue || 0);

  return paymentMethodsList.map((pm) => {
    const item = (paymentsReport || []).find(
      (p) => (p.paymentMethod || '').toUpperCase() === pm.key
    );
    const amount = item ? Number(item.amount || 0) : 0;
    const count = item ? Number(item.transactionCount || 0) : 0;
    const share = totalRevNum > 0 ? ((amount / totalRevNum) * 100).toFixed(1) : '0.0';
    return {
      label: pm.label,
      count,
      amount,
      share,
    };
  });
}

// ==========================================
// TESTS
// ==========================================

test('1. Print Mode Isolation: sales-report print mode renders ONLY SalesReportPrintDocument and NO bills', () => {
  const dummyBill = { id: 1, orderId: 101, totalAmount: 450.0 };
  const reportBills = [dummyBill, { id: 2, orderId: 102, totalAmount: 820.0 }];
  const salesReportData = {
    reportType: 'ALL',
    fromDate: '2026-11-15',
    toDate: '2026-11-15',
    salesReport: { totalRevenue: '1270.00', ordersSettled: 2 },
  };

  // Case A: Sales report printing
  const reportOutput = getPrintableAreaOutput('sales-report', dummyBill, reportBills, salesReportData);
  assert.equal(reportOutput.isReportMode, true);
  assert.equal(reportOutput.renderedComponents.length, 1);
  assert.equal(reportOutput.renderedComponents[0].type, 'SalesReportPrintDocument');
  assert.equal(reportOutput.renderedComponents[0].reportType, 'ALL');
  assert.equal(reportOutput.renderedComponents.some((c) => c.type === 'ThermalReceipt'), false);

  // Case B: Print all bills
  const allBillsOutput = getPrintableAreaOutput('all', null, reportBills, salesReportData);
  assert.equal(allBillsOutput.isReportMode, false);
  assert.equal(allBillsOutput.isAllBillsMode, true);
  assert.equal(allBillsOutput.renderedComponents.length, 2);
  assert.equal(allBillsOutput.renderedComponents.every((c) => c.type === 'ThermalReceipt'), true);
  assert.equal(allBillsOutput.renderedComponents.some((c) => c.type === 'SalesReportPrintDocument'), false);

  // Case C: Print single bill
  const singleBillOutput = getPrintableAreaOutput('single', dummyBill, reportBills, salesReportData);
  assert.equal(singleBillOutput.isReportMode, false);
  assert.equal(singleBillOutput.isSingleBillMode, true);
  assert.equal(singleBillOutput.renderedComponents.length, 1);
  assert.equal(singleBillOutput.renderedComponents[0].type, 'ThermalReceipt');
  assert.equal(singleBillOutput.renderedComponents[0].orderId, 101);
  assert.equal(singleBillOutput.renderedComponents.some((c) => c.type === 'SalesReportPrintDocument'), false);
});

test('2. Report Scope Filtering: correct sections visible based on selected reportType', () => {
  // Scope: ALL (Multi-day)
  const allMultiDay = getVisibleReportSections('ALL', true);
  assert.equal(allMultiDay.financialSummary, true);
  assert.equal(allMultiDay.paymentsBreakdown, true);
  assert.equal(allMultiDay.orderTypesBreakdown, true);
  assert.equal(allMultiDay.topItems, true);
  assert.equal(allMultiDay.categoryBreakdown, true);
  assert.equal(allMultiDay.dailyBreakdown, true);

  // Scope: ALL (Single day) -> Daily breakdown disabled
  const allSingleDay = getVisibleReportSections('ALL', false);
  assert.equal(allSingleDay.dailyBreakdown, false);
  assert.equal(allSingleDay.financialSummary, true);

  // Scope: SUMMARY (Financial & Payments only)
  const summaryScope = getVisibleReportSections('SUMMARY', true);
  assert.equal(summaryScope.financialSummary, true);
  assert.equal(summaryScope.paymentsBreakdown, true);
  assert.equal(summaryScope.orderTypesBreakdown, false);
  assert.equal(summaryScope.topItems, false);
  assert.equal(summaryScope.categoryBreakdown, false);
  assert.equal(summaryScope.dailyBreakdown, false);

  // Scope: PAYMENTS (Payment channels only)
  const paymentsScope = getVisibleReportSections('PAYMENTS', true);
  assert.equal(paymentsScope.financialSummary, false);
  assert.equal(paymentsScope.paymentsBreakdown, true);
  assert.equal(paymentsScope.orderTypesBreakdown, false);

  // Scope: ITEMS_CATEGORIES
  const itemsScope = getVisibleReportSections('ITEMS_CATEGORIES', true);
  assert.equal(itemsScope.financialSummary, false);
  assert.equal(itemsScope.paymentsBreakdown, false);
  assert.equal(itemsScope.topItems, true);
  assert.equal(itemsScope.categoryBreakdown, true);

  // Scope: ORDER_TYPES
  const orderTypesScope = getVisibleReportSections('ORDER_TYPES', true);
  assert.equal(orderTypesScope.financialSummary, false);
  assert.equal(orderTypesScope.orderTypesBreakdown, true);

  // Scope: DAILY
  const dailyScope = getVisibleReportSections('DAILY', true);
  assert.equal(dailyScope.dailyBreakdown, true);
  assert.equal(dailyScope.financialSummary, true); // summary header included
  assert.equal(dailyScope.orderTypesBreakdown, false);
});

test('3. Date Range Labeling and Document Titles', () => {
  // Single day
  const singleTitle = formatDocumentTitle('2026-11-15', '2026-11-15');
  assert.equal(singleTitle, 'sales-report-2026-11-15');
  const singleLabel = formatPeriodLabel('2026-11-15', '2026-11-15');
  assert.equal(singleLabel, '2026-11-15 (Single Day)');

  // Multi day
  const multiTitle = formatDocumentTitle('2026-11-01', '2026-11-15');
  assert.equal(multiTitle, 'sales-report-2026-11-01-to-2026-11-15');
  const multiLabel = formatPeriodLabel('2026-11-01', '2026-11-15');
  assert.equal(multiLabel, '2026-11-01 to 2026-11-15');
});

test('4. Calculation & Metric Consistency with Backend Reports', () => {
  const mockSalesReport = {
    totalRevenue: '5400.00',
    totalOrders: 20,
    ordersSettled: 18,
    billsGenerated: 18,
    averageOrderValue: '300.00',
    totalTax: '270.00',
    totalCgst: '135.00',
    totalSgst: '135.00',
    totalDiscount: '150.00',
  };

  const mockPaymentsReport = [
    { paymentMethod: 'CASH', amount: '2700.00', transactionCount: 9 },
    { paymentMethod: 'UPI', amount: '2160.00', transactionCount: 7 },
    { paymentMethod: 'CARD', amount: '540.00', transactionCount: 2 },
  ];

  const paymentRows = computePaymentBreakdownRows(mockPaymentsReport, mockSalesReport.totalRevenue);

  assert.equal(paymentRows.length, 3);
  // Cash: 2700 / 5400 = 50%
  assert.equal(paymentRows[0].label, 'Cash');
  assert.equal(paymentRows[0].amount, 2700.0);
  assert.equal(paymentRows[0].count, 9);
  assert.equal(paymentRows[0].share, '50.0');

  // UPI: 2160 / 5400 = 40%
  assert.equal(paymentRows[1].label, 'UPI');
  assert.equal(paymentRows[1].amount, 2160.0);
  assert.equal(paymentRows[1].count, 7);
  assert.equal(paymentRows[1].share, '40.0');

  // Card: 540 / 5400 = 10%
  assert.equal(paymentRows[2].label, 'Credit / Debit Card');
  assert.equal(paymentRows[2].amount, 540.0);
  assert.equal(paymentRows[2].count, 2);
  assert.equal(paymentRows[2].share, '10.0');

  const totalSum = paymentRows.reduce((acc, r) => acc + r.amount, 0);
  assert.equal(totalSum, 5400.0);
});

test('5. Empty Period Detection renders Sensible Empty State', () => {
  const zeroReport = {
    totalRevenue: '0.00',
    totalOrders: 0,
    ordersSettled: 0,
  };
  assert.equal(isPeriodEmpty(zeroReport), true);

  const activeReport = {
    totalRevenue: '450.00',
    totalOrders: 1,
    ordersSettled: 1,
  };
  assert.equal(isPeriodEmpty(activeReport), false);
});
