import React, { useState, useEffect, useCallback } from 'react';
import reportService from '../../services/reportService';
import ThermalReceipt from '../../components/admin/ThermalReceipt';
import { CURRENCY_SYMBOL } from '../../utils/constants';

export const ReportsPage = () => {
  // Date Helpers
  const formatDateISO = (d) => {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  const getTodayISO = () => formatDateISO(new Date());
  const getDaysAgoISO = (days) => {
    const d = new Date();
    d.setDate(d.getDate() - days);
    return formatDateISO(d);
  };

  // State
  const [activePreset, setActivePreset] = useState('TODAY'); // TODAY, YESTERDAY, LAST_7, LAST_30, CUSTOM
  const [fromDate, setFromDate] = useState(getTodayISO());
  const [toDate, setToDate] = useState(getTodayISO());

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Report Data States
  const [salesReport, setSalesReport] = useState(null);
  const [paymentsReport, setPaymentsReport] = useState([]);
  const [statusReport, setStatusReport] = useState(null);
  const [orderTypesReport, setOrderTypesReport] = useState([]);
  const [topItemsReport, setTopItemsReport] = useState([]);
  const [categorySalesReport, setCategorySalesReport] = useState([]);
  const [dailySalesBreakdown, setDailySalesBreakdown] = useState([]);

  // Report Bills States
  const [reportBills, setReportBills] = useState([]);
  const [billsLoading, setBillsLoading] = useState(false);
  const [billsError, setBillsError] = useState('');
  const [selectedBillForModal, setSelectedBillForModal] = useState(null);
  const [paperWidth, setPaperWidth] = useState('80mm');
  const [printMode, setPrintMode] = useState(null);
  const [printingBill, setPrintingBill] = useState(null);
  const [isPrinting, setIsPrinting] = useState(false);

  const handlePresetChange = (preset) => {
    setActivePreset(preset);
    const todayStr = getTodayISO();

    if (preset === 'TODAY') {
      setFromDate(todayStr);
      setToDate(todayStr);
    } else if (preset === 'YESTERDAY') {
      const yesterdayStr = getDaysAgoISO(1);
      setFromDate(yesterdayStr);
      setToDate(yesterdayStr);
    } else if (preset === 'LAST_7') {
      setFromDate(getDaysAgoISO(6));
      setToDate(todayStr);
    } else if (preset === 'LAST_30') {
      setFromDate(getDaysAgoISO(29));
      setToDate(todayStr);
    }
  };

  const fetchReports = useCallback(async () => {
    if (!fromDate || !toDate) return;
    if (fromDate > toDate) {
      setError("'From' date cannot be after 'To' date.");
      return;
    }

    setLoading(true);
    setBillsLoading(true);
    setError('');
    setBillsError('');
    setPrintMode(null);
    setPrintingBill(null);
    setIsPrinting(false);

    try {
      const [sales, payments, status, types, topItems, categories, daily, bills] = await Promise.all([
        reportService.getSalesReport(fromDate, toDate),
        reportService.getPaymentMethodBreakdown(fromDate, toDate),
        reportService.getOrderStatusStatistics(fromDate, toDate),
        reportService.getOrderTypeReport(fromDate, toDate),
        reportService.getTopSellingItems(fromDate, toDate, 10),
        reportService.getCategorySalesReport(fromDate, toDate),
        fromDate !== toDate ? reportService.getDailySalesBreakdown(fromDate, toDate) : Promise.resolve([]),
        reportService.getReportBills(fromDate, toDate).catch((e) => {
          setBillsError(e.response?.data?.message || e.message || 'Unable to load bills.');
          return [];
        }),
      ]);

      setSalesReport(sales);
      setPaymentsReport(payments || []);
      setStatusReport(status);
      setOrderTypesReport(types || []);
      setTopItemsReport(topItems || []);
      setCategorySalesReport(categories || []);
      setDailySalesBreakdown(daily || []);
      setReportBills(bills || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load report metrics');
    } finally {
      setLoading(false);
      setBillsLoading(false);
    }
  }, [fromDate, toDate]);

  useEffect(() => {
    fetchReports();
  }, [fetchReports]);

  // Extract payment breakdown values helper
  const getPaymentMethodVal = (methodName) => {
    const item = paymentsReport.find((p) => p.paymentMethod?.toUpperCase() === methodName.toUpperCase());
    return {
      amount: item ? Number(item.amount) : 0,
      count: item ? Number(item.transactionCount) : 0,
    };
  };

  const cashInfo = getPaymentMethodVal('CASH');
  const upiInfo = getPaymentMethodVal('UPI');
  const cardInfo = getPaymentMethodVal('CARD');

  // Formatters
  const formatCurrency = (val) => `${CURRENCY_SYMBOL}${Number(val || 0).toFixed(2)}`;
  const formatDateLabel = (isoString) => {
    if (!isoString) return '—';
    try {
      const parts = isoString.split('T')[0].split('-').map(Number);
      if (parts.length === 3 && !parts.some(isNaN)) {
        const [y, m, d] = parts;
        const date = new Date(y, m - 1, d);
        return date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
      }
      return isoString;
    } catch {
      return isoString;
    }
  };

  const isMultiDay = fromDate !== toDate;

  // Print & Download Handlers
  const triggerPrint = useCallback((mode, bill = null) => {
    if (isPrinting) return;
    if (mode === 'all' && (!reportBills || reportBills.length === 0)) return;

    setIsPrinting(true);
    setPrintMode(mode);
    setPrintingBill(bill);

    const origTitle = document.title;
    if (mode === 'single' && bill) {
      document.title = `bill-order-${bill.orderId || bill.id}`;
    } else if (mode === 'all') {
      const titleStr = fromDate === toDate
        ? `restaurant-bills-${fromDate}`
        : `restaurant-bills-${fromDate}-to-${toDate}`;
      document.title = titleStr;
    }

    const cleanup = () => {
      document.title = origTitle;
      setPrintMode(null);
      setPrintingBill(null);
      setIsPrinting(false);
      window.removeEventListener('afterprint', cleanup);
    };

    window.addEventListener('afterprint', cleanup, { once: true });

    setTimeout(() => {
      try {
        window.print();
      } catch (err) {
        console.error('Print error:', err);
      } finally {
        setTimeout(cleanup, 500);
      }
    }, 100);
  }, [isPrinting, reportBills, fromDate, toDate]);

  const handlePrintSingleBill = (bill) => triggerPrint('single', bill);
  const handleDownloadSingleBill = (bill) => triggerPrint('single', bill);
  const handlePrintAllBills = () => triggerPrint('all');
  const handleDownloadAllBills = () => triggerPrint('all');

  const totalReportBillsCount = reportBills.length;
  const totalReportBillsAmount = reportBills.reduce((acc, b) => acc + Number(b.totalAmount || b.total || 0), 0);

  return (
    <div className="admin-page reports-page">
      {/* Header */}
      <div className="page-header">
        <div className="header-title">
          <h1>Analytics & Reports</h1>
          <p className="subtitle">Comprehensive financial summary, sales performance, payment channels, and item rankings</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-secondary btn-icon" onClick={fetchReports} title="Refresh Report Data">
            Refresh Reports
          </button>
        </div>
      </div>

      {/* Date Filter Toolbar */}
      <div className="billing-toolbar reports-toolbar mb-4">
        <div className="d-flex align-items-center gap-2 flex-wrap flex-1">
          <span className="text-xs font-semibold text-muted uppercase">Period Preset:</span>
          <div className="preset-buttons d-flex gap-1 flex-wrap">
            <button
              className={`btn btn-sm ${activePreset === 'TODAY' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => handlePresetChange('TODAY')}
            >
              Today
            </button>
            <button
              className={`btn btn-sm ${activePreset === 'YESTERDAY' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => handlePresetChange('YESTERDAY')}
            >
              Yesterday
            </button>
            <button
              className={`btn btn-sm ${activePreset === 'LAST_7' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => handlePresetChange('LAST_7')}
            >
              Last 7 Days
            </button>
            <button
              className={`btn btn-sm ${activePreset === 'LAST_30' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => handlePresetChange('LAST_30')}
            >
              Last 30 Days
            </button>
            <button
              className={`btn btn-sm ${activePreset === 'CUSTOM' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setActivePreset('CUSTOM')}
            >
              Custom
            </button>
          </div>

          {/* Date Pickers */}
          <div className="d-flex align-items-center gap-2 ml-auto date-pickers-group">
            <div className="date-input-box">
              <label className="text-xs text-muted">From:</label>
              <input
                type="date"
                className="form-input form-input-sm"
                value={fromDate}
                onChange={(e) => {
                  setFromDate(e.target.value);
                  setActivePreset('CUSTOM');
                }}
              />
            </div>
            <div className="date-input-box">
              <label className="text-xs text-muted">To:</label>
              <input
                type="date"
                className="form-input form-input-sm"
                value={toDate}
                onChange={(e) => {
                  setToDate(e.target.value);
                  setActivePreset('CUSTOM');
                }}
              />
            </div>
          </div>
        </div>
      </div>

      {error && <div className="alert alert-danger mb-4">{error}</div>}

      {/* Loading State */}
      {loading ? (
        <div className="loading-container py-5 text-center">
          <div className="spinner mb-3"></div>
          <p className="text-muted">Generating backend analytics reports...</p>
        </div>
      ) : (
        <>
          {/* Section A: Summary Metric Cards */}
          <div className="metrics-grid mb-4">
            <div className="metric-card card highlight-card">
              <div className="metric-header">
                <span className="metric-title">Total Revenue</span>
              </div>
              <div className="metric-value text-success font-extrabold">
                {formatCurrency(salesReport?.totalRevenue)}
              </div>
              <div className="text-xs text-muted mt-1">
                From {salesReport?.completedOrders || 0} paid completed orders
              </div>
            </div>

            <div className="metric-card card">
              <div className="metric-header">
                <span className="metric-title">Total Orders</span>
              </div>
              <div className="metric-value">{salesReport?.totalOrders || 0}</div>
              <div className="text-xs text-muted mt-1">
                Completed: {salesReport?.completedOrders || 0} | Cancelled: {salesReport?.cancelledOrders || 0}
              </div>
            </div>

            <div className="metric-card card">
              <div className="metric-header">
                <span className="metric-title">Average Order Value</span>
              </div>
              <div className="metric-value text-info">
                {formatCurrency(salesReport?.averageOrderValue)}
              </div>
              <div className="text-xs text-muted mt-1">Per paid completed order</div>
            </div>

            <div className="metric-card card">
              <div className="metric-header">
                <span className="metric-title">Taxes & Discounts</span>
              </div>
              <div className="metric-value text-warning text-base font-bold">
                Tax: {formatCurrency(salesReport?.totalTax)}
              </div>
              <div className="text-xs text-muted mt-1">
                Discount Given: {formatCurrency(salesReport?.totalDiscount)}
              </div>
            </div>
          </div>

          {/* Section B: Payment Channel Breakdown */}
          <div className="card p-4 mb-4">
            <h3 className="section-title mb-3">Payment Channel Breakdown</h3>
            <div className="grid grid-3 gap-3">
              <div className="payment-channel-card card p-3 border-left-cash">
                <div className="d-flex justify-content-between align-items-center mb-1">
                  <span className="font-bold text-main">Cash</span>
                  <span className="badge badge-cash">{cashInfo.count} Txns</span>
                </div>
                <div className="text-xl font-extrabold text-main mt-2">
                  {formatCurrency(cashInfo.amount)}
                </div>
              </div>

              <div className="payment-channel-card card p-3 border-left-upi">
                <div className="d-flex justify-content-between align-items-center mb-1">
                  <span className="font-bold text-main">UPI Payment</span>
                  <span className="badge badge-upi">{upiInfo.count} Txns</span>
                </div>
                <div className="text-xl font-extrabold text-main mt-2">
                  {formatCurrency(upiInfo.amount)}
                </div>
              </div>

              <div className="payment-channel-card card p-3 border-left-card">
                <div className="d-flex justify-content-between align-items-center mb-1">
                  <span className="font-bold text-main">Credit/Debit Card</span>
                  <span className="badge badge-card">{cardInfo.count} Txns</span>
                </div>
                <div className="text-xl font-extrabold text-main mt-2">
                  {formatCurrency(cardInfo.amount)}
                </div>
              </div>
            </div>
          </div>

          {/* Section C: Order Types & Status Pipeline */}
          <div className="grid grid-2 gap-4 mb-4">
            {/* Order Types */}
            <div className="card p-4">
              <h3 className="section-title mb-3">Order Type Breakdown</h3>
              <div className="d-flex flex-column gap-3">
                {orderTypesReport.map((t) => (
                  <div key={t.orderType} className="type-report-row p-3 card">
                    <div className="d-flex justify-content-between align-items-center">
                      <div>
                        <span className={`badge ${t.orderType === 'DINE_IN' ? 'badge-dinein' : 'badge-takeaway'} text-sm uppercase font-bold`}>
                          {t.orderType === 'DINE_IN' ? 'Dine-In' : 'Takeaway'}
                        </span>
                        <div className="text-xs text-muted mt-1">
                          Total Orders: <strong>{t.orderCount}</strong> (Completed: {t.completedCount}, Cancelled: {t.cancelledCount})
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="text-lg font-extrabold text-success">
                          {formatCurrency(t.revenue)}
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Order Status Pipeline */}
            <div className="card p-4">
              <h3 className="section-title mb-3">Order Status Pipeline</h3>
              <div className="status-grid grid grid-3 gap-2">
                <div className="status-metric-box card p-2 text-center">
                  <span className="badge badge-new text-xs mb-1">New</span>
                  <div className="text-lg font-extrabold">{statusReport?.newOrders || 0}</div>
                </div>

                <div className="status-metric-box card p-2 text-center">
                  <span className="badge badge-accepted text-xs mb-1">Accepted</span>
                  <div className="text-lg font-extrabold">{statusReport?.acceptedOrders || 0}</div>
                </div>

                <div className="status-metric-box card p-2 text-center">
                  <span className="badge badge-preparing text-xs mb-1">Preparing</span>
                  <div className="text-lg font-extrabold">{statusReport?.preparingOrders || 0}</div>
                </div>

                <div className="status-metric-box card p-2 text-center">
                  <span className="badge badge-ready text-xs mb-1">Ready</span>
                  <div className="text-lg font-extrabold">{statusReport?.readyOrders || 0}</div>
                </div>

                <div className="status-metric-box card p-2 text-center">
                  <span className="badge badge-completed text-xs mb-1">Completed</span>
                  <div className="text-lg font-extrabold text-success">{statusReport?.completedOrders || 0}</div>
                </div>

                <div className="status-metric-box card p-2 text-center">
                  <span className="badge badge-cancelled text-xs mb-1">Cancelled</span>
                  <div className="text-lg font-extrabold text-danger">{statusReport?.cancelledOrders || 0}</div>
                </div>
              </div>
            </div>
          </div>

          {/* Section D: Top Selling Items & Category Performance */}
          <div className="grid grid-2 gap-4 mb-4">
            {/* Top Items */}
            <div className="card p-4">
              <h3 className="section-title mb-3">Top 10 Selling Items</h3>
              {topItemsReport.length === 0 ? (
                <div className="empty-state py-4 text-center">
                  <p className="text-muted m-0">No item sales recorded for this period.</p>
                </div>
              ) : (
                <div className="table-responsive card-flush">
                  <table className="data-table data-table-sm">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>Item Name</th>
                        <th className="text-right">Qty Sold</th>
                        <th className="text-right">Revenue</th>
                      </tr>
                    </thead>
                    <tbody>
                      {topItemsReport.map((item, idx) => (
                        <tr key={item.menuItemId || idx}>
                          <td className="font-bold text-muted text-xs">{idx + 1}</td>
                          <td className="font-semibold text-main">{item.menuItemName}</td>
                          <td className="text-right font-extrabold">{item.quantitySold}</td>
                          <td className="text-right font-bold text-success">
                            {formatCurrency(item.revenue)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Category Performance */}
            <div className="card p-4">
              <h3 className="section-title mb-3">Category Sales Breakdown</h3>
              {categorySalesReport.length === 0 ? (
                <div className="empty-state py-4 text-center">
                  <p className="text-muted m-0">No category sales recorded for this period.</p>
                </div>
              ) : (
                <div className="table-responsive card-flush">
                  <table className="data-table data-table-sm">
                    <thead>
                      <tr>
                        <th>Category Name</th>
                        <th className="text-right">Qty Sold</th>
                        <th className="text-right">Total Revenue</th>
                      </tr>
                    </thead>
                    <tbody>
                      {categorySalesReport.map((cat, idx) => (
                        <tr key={cat.categoryId || idx}>
                          <td className="font-semibold text-main">{cat.categoryName}</td>
                          <td className="text-right font-extrabold">{cat.quantitySold}</td>
                          <td className="text-right font-bold text-success">
                            {formatCurrency(cat.revenue)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>

          {/* Section E: Daily Sales Breakdown (For multi-day ranges) */}
          {isMultiDay && (
            <div className="card p-4 mb-4">
              <h3 className="section-title mb-3">📅 Daily Sales History</h3>
              {dailySalesBreakdown.length === 0 ? (
                <div className="empty-state py-3 text-center">
                  <p className="text-muted m-0">No daily sales data found for selected range.</p>
                </div>
              ) : (
                <div className="table-responsive card-flush">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Total Orders</th>
                        <th>Completed</th>
                        <th>Cancelled</th>
                        <th className="text-right">Revenue</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dailySalesBreakdown.map((row, idx) => (
                        <tr key={idx}>
                          <td className="font-semibold">{formatDateLabel(row.date)}</td>
                          <td>{row.orders}</td>
                          <td><span className="text-success font-semibold">{row.completedOrders}</span></td>
                          <td><span className="text-danger font-semibold">{row.cancelledOrders}</span></td>
                          <td className="text-right font-extrabold text-success">
                            {formatCurrency(row.revenue)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* Section F: Bills for Selected Period */}
          <div className="card p-4 mb-4 reports-bills-section">
            <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
              <div>
                <h3 className="section-title m-0">🧾 Bills for Selected Period</h3>
                <div className="text-xs text-muted mt-1">
                  {fromDate === toDate
                    ? `Showing paid bills for ${formatDateLabel(fromDate)}`
                    : `Showing paid bills from ${formatDateLabel(fromDate)} to ${formatDateLabel(toDate)}`}
                </div>
              </div>

              {/* Action Toolbar */}
              <div className="d-flex align-items-center gap-2 flex-wrap">
                {/* Paper width toggle */}
                <div className="paper-width-toggle m-0">
                  <button
                    type="button"
                    className={`toggle-btn ${paperWidth === '80mm' ? 'active' : ''}`}
                    onClick={() => setPaperWidth('80mm')}
                  >
                    80mm
                  </button>
                  <button
                    type="button"
                    className={`toggle-btn ${paperWidth === '58mm' ? 'active' : ''}`}
                    onClick={() => setPaperWidth('58mm')}
                  >
                    58mm
                  </button>
                </div>

                <button
                  className="btn btn-secondary btn-sm d-flex align-items-center gap-1"
                  onClick={handlePrintAllBills}
                  disabled={totalReportBillsCount === 0 || billsLoading || isPrinting}
                  title="Print all bills for the selected period"
                >
                  Print All Bills
                </button>

                <button
                  className="btn btn-primary btn-sm d-flex align-items-center gap-1"
                  onClick={handleDownloadAllBills}
                  disabled={totalReportBillsCount === 0 || billsLoading || isPrinting}
                  title="Download all bills as PDF"
                >
                  Download All Bills
                </button>
              </div>
            </div>

            {/* Summary metrics header */}
            <div className="d-flex align-items-center justify-content-between p-3 card mb-3 reports-bills-summary-bar">
              <div>
                <span className="badge badge-completed font-bold text-sm">
                  {totalReportBillsCount} {totalReportBillsCount === 1 ? 'Paid Bill' : 'Paid Bills'}
                </span>
              </div>
              <div className="text-right">
                <span className="text-xs text-muted uppercase font-semibold mr-2">Total Amount:</span>
                <span className="text-xl font-extrabold text-success">
                  {formatCurrency(totalReportBillsAmount)}
                </span>
              </div>
            </div>

            {/* Bills List / Loading / Error / Empty States */}
            {billsLoading ? (
              <div className="loading-container py-4 text-center">
                <div className="spinner mb-2"></div>
                <p className="text-muted m-0">Loading bills...</p>
              </div>
            ) : billsError ? (
              <div className="alert alert-danger d-flex justify-content-between align-items-center">
                <span>Unable to load bills. ({billsError})</span>
                <button
                  className="btn btn-sm btn-secondary"
                  onClick={() => {
                    setBillsLoading(true);
                    setBillsError('');
                    reportService
                      .getReportBills(fromDate, toDate)
                      .then((data) => setReportBills(data || []))
                      .catch((e) => setBillsError(e.response?.data?.message || e.message || 'Unable to load bills.'))
                      .finally(() => setBillsLoading(false));
                  }}
                >
                  Retry
                </button>
              </div>
            ) : reportBills.length === 0 ? (
              <div className="empty-state py-4 text-center">
                <div className="empty-state-icon">🧾</div>
                <p className="empty-state-message">No paid bills found for this period.</p>
                <p className="empty-state-sub">Only completed orders with successful payment will appear here.</p>
              </div>
            ) : (
              <div className="grid grid-2 gap-3 report-bills-grid">
                {reportBills.map((b) => {
                  const billTotal = Number(b.totalAmount || b.total || 0);
                  const isDineIn = (b.orderType || '').toUpperCase() === 'DINE_IN';
                  const tableDisp = isDineIn && (b.tableNumber !== undefined && b.tableNumber !== null)
                    ? `Table ${b.tableNumber}`
                    : isDineIn && b.tableName
                    ? b.tableName
                    : null;

                  return (
                    <div key={b.billId || b.id} className="card p-3 report-bill-card d-flex flex-column justify-content-between">
                      <div>
                        <div className="d-flex justify-content-between align-items-start mb-2">
                          <div>
                            <div className="font-extrabold text-lg text-main">
                              Order #{b.orderId}
                            </div>
                            <div className="text-xs text-muted font-mono">
                              Bill {b.billNumber}
                            </div>
                          </div>
                          <div className="text-right">
                            <div className="text-lg font-extrabold text-success">
                              {formatCurrency(billTotal)}
                            </div>
                            <span className="badge badge-completed text-xs uppercase font-bold">
                              {b.paymentStatus || 'PAID'}
                            </span>
                          </div>
                        </div>

                        <div className="d-flex align-items-center gap-2 flex-wrap text-xs text-muted mb-3">
                          <span className={`badge ${isDineIn ? 'badge-dinein' : 'badge-takeaway'} font-bold`}>
                            {isDineIn ? 'Dine-In' : 'Takeaway'}
                          </span>
                          {tableDisp && (
                            <span className="badge badge-secondary font-semibold">
                              {tableDisp}
                            </span>
                          )}
                          <span className="badge badge-cash uppercase font-semibold">
                            {b.paymentMethod || 'CASH'}
                          </span>
                          {b.waiterName && (
                            <span>Waiter: {b.waiterName}</span>
                          )}
                        </div>
                      </div>

                      {/* Card Action Buttons */}
                      <div className="d-flex gap-2 pt-2 border-top-thin">
                        <button
                          type="button"
                          className="btn btn-secondary btn-xs flex-1"
                          onClick={() => setSelectedBillForModal(b)}
                          disabled={isPrinting}
                          title="View thermal receipt details"
                        >
                          View
                        </button>
                        <button
                          type="button"
                          className="btn btn-secondary btn-xs flex-1"
                          onClick={() => handlePrintSingleBill(b)}
                          disabled={isPrinting}
                          title="Print individual thermal receipt"
                        >
                          Print
                        </button>
                        <button
                          type="button"
                          className="btn btn-primary btn-xs flex-1"
                          onClick={() => handleDownloadSingleBill(b)}
                          disabled={isPrinting}
                          title="Download receipt as PDF"
                        >
                          Download
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </>
      )}

      {/* Individual Bill Modal */}
      {selectedBillForModal && (
        <div
          className="modal-overlay modal-backdrop"
          onClick={() => setSelectedBillForModal(null)}
          role="dialog"
          aria-modal="true"
        >
          <div
            className="modal-container modal-content bill-modal"
            style={{ maxWidth: '640px' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <div className="bill-modal-title">
                <h3>Receipt — Order #{selectedBillForModal.orderId} ({selectedBillForModal.billNumber})</h3>
              </div>
              <button
                className="modal-close-btn"
                onClick={() => setSelectedBillForModal(null)}
                aria-label="Close modal"
              >
                Close
              </button>
            </div>

            <div className="modal-body">
              <div className="d-flex justify-content-between align-items-center mb-3">
                <div className="paper-width-toggle m-0">
                  <button
                    type="button"
                    className={`toggle-btn ${paperWidth === '80mm' ? 'active' : ''}`}
                    onClick={() => setPaperWidth('80mm')}
                  >
                    80mm
                  </button>
                  <button
                    type="button"
                    className={`toggle-btn ${paperWidth === '58mm' ? 'active' : ''}`}
                    onClick={() => setPaperWidth('58mm')}
                  >
                    58mm
                  </button>
                </div>

                <div className="d-flex gap-2">
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => handlePrintSingleBill(selectedBillForModal)}
                    disabled={isPrinting}
                  >
                    Print
                  </button>
                  <button
                    type="button"
                    className="btn btn-primary btn-sm"
                    onClick={() => handleDownloadSingleBill(selectedBillForModal)}
                    disabled={isPrinting}
                  >
                    Download PDF
                  </button>
                </div>
              </div>

              <div className="printable-receipt-area-preview">
                <ThermalReceipt
                  bill={selectedBillForModal}
                  order={selectedBillForModal}
                  paperWidth={paperWidth}
                />
              </div>
            </div>

            <div className="modal-footer">
              <button
                className="btn btn-secondary"
                onClick={() => setSelectedBillForModal(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Hidden printable area for @media print */}
      <div className="printable-receipt-area">
        {printMode === 'single' && printingBill && (
          <ThermalReceipt bill={printingBill} order={printingBill} paperWidth={paperWidth} />
        )}
        {printMode === 'all' && reportBills && reportBills.map((b, idx) => (
          <div key={b.billId || b.id || idx} className="print-bill-page-break">
            <ThermalReceipt bill={b} order={b} paperWidth={paperWidth} />
          </div>
        ))}
      </div>
    </div>
  );
};

export default ReportsPage;
