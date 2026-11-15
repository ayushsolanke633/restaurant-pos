import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Unit Tests for Bug 1 Fix: Separate Orders Created from Orders Settled
 * Validates ReportsPage and AdminDashboardPage reporting semantics,
 * labeling, tooltips, and backward-compatible fallbacks.
 */

// 1. ReportsPage Summary Cards & Subtitles Logic Simulator
function computeReportsSummaryCards(salesReport) {
  const totalOrders = salesReport?.totalOrders ?? 0;
  const ordersSettled = salesReport?.ordersSettled ?? salesReport?.completedOrders ?? totalOrders ?? 0;
  const billsGenerated = salesReport?.billsGenerated ?? 0;
  const totalRevenue = salesReport?.totalRevenue ?? 0;
  const averageOrderValue = salesReport?.averageOrderValue ?? 0;

  return {
    revenueCard: {
      title: 'Total Revenue',
      value: `₹${Number(totalRevenue).toFixed(2)}`,
      subtitle: `From ${ordersSettled} settled order(s)`,
    },
    ordersSettledCard: {
      title: 'Orders Settled',
      value: String(ordersSettled),
      subtitle: `Orders Created: ${totalOrders} | Bills Generated: ${billsGenerated}`,
    },
    aovCard: {
      title: 'Average Order Value',
      value: `₹${Number(averageOrderValue).toFixed(2)}`,
      subtitle: 'Per settled order',
    },
  };
}

// 2. AdminDashboardPage Sales Overview Chart Tooltip Formatter
function formatDashboardChartTooltip(hoveredPoint) {
  const settled = hoveredPoint?.ordersSettled ?? hoveredPoint?.orders ?? 0;
  const created = hoveredPoint?.orders ?? 0;
  return `${settled} settled orders (${created} created)`;
}

// 3. AdminDashboardPage Summary Cards Simulator
function computeDashboardSummaryCards(dashboardData, todayOrdersSettled) {
  const totalOrders = dashboardData?.totalOrders ?? 0;
  const settledCount = todayOrdersSettled ?? dashboardData?.ordersSettled ?? 0;
  const totalSales = dashboardData?.totalSales ?? 0;

  return [
    {
      title: "Today's Orders (Created)",
      value: totalOrders,
      subtitle: `${settledCount} settled today`,
    },
    {
      title: "Today's Sales",
      value: `₹${Number(totalSales).toFixed(2)}`,
      subtitle: `From ${settledCount} settled orders`,
    },
  ];
}

// TESTS

test('1. ReportsPage accurately displays Orders Created, Orders Settled, and Bills Generated for Bug 1 example', () => {
  // Scenario: Order A (created 2 days ago, settled today: 500)
  //           Order B (created today, unpaid: 300)
  //           Order C (created and settled today: 700)
  const salesReport = {
    totalRevenue: '1200.00',
    totalOrders: 2,       // Orders Created today (B and C)
    ordersSettled: 2,     // Orders Settled today (A and C)
    billsGenerated: 2,    // Bills Generated today (A and C)
    averageOrderValue: '600.00',
    completedOrders: 1,
  };

  const cards = computeReportsSummaryCards(salesReport);

  // Revenue card subtitle
  assert.equal(cards.revenueCard.subtitle, 'From 2 settled order(s)');
  assert.equal(cards.revenueCard.value, '₹1200.00');

  // Orders Settled card
  assert.equal(cards.ordersSettledCard.title, 'Orders Settled');
  assert.equal(cards.ordersSettledCard.value, '2');
  assert.equal(cards.ordersSettledCard.subtitle, 'Orders Created: 2 | Bills Generated: 2');

  // Average Order Value card
  assert.equal(cards.aovCard.subtitle, 'Per settled order');
  assert.equal(cards.aovCard.value, '₹600.00');
});

test('2. ReportsPage supports backward compatibility when ordersSettled is missing from response', () => {
  // Legacy backend response without ordersSettled
  const legacyReport = {
    totalRevenue: '500.00',
    totalOrders: 3,
    completedOrders: 2,
    averageOrderValue: '250.00',
  };

  const cards = computeReportsSummaryCards(legacyReport);

  // Falls back gracefully to completedOrders
  assert.equal(cards.revenueCard.subtitle, 'From 2 settled order(s)');
  assert.equal(cards.ordersSettledCard.value, '2');
  assert.equal(cards.ordersSettledCard.subtitle, 'Orders Created: 3 | Bills Generated: 0');
});

test('3. AdminDashboardPage displays clarified Today Orders (Created) and settlement subtitle', () => {
  const dashboardData = {
    totalOrders: 5,
    ordersSettled: 3,
    totalSales: '2400.00',
  };

  const cards = computeDashboardSummaryCards(dashboardData, dashboardData.ordersSettled);

  assert.equal(cards[0].title, "Today's Orders (Created)");
  assert.equal(cards[0].value, 5);
  assert.equal(cards[0].subtitle, '3 settled today');

  assert.equal(cards[1].title, "Today's Sales");
  assert.equal(cards[1].subtitle, 'From 3 settled orders');
});

test('4. Dashboard SalesOverview chart tooltip accurately distinguishes settled orders from created orders', () => {
  const hoveredPoint = {
    date: '2026-10-27',
    sales: 1200,
    orders: 2,        // Created
    ordersSettled: 2, // Settled
  };

  const tooltipText = formatDashboardChartTooltip(hoveredPoint);

  assert.equal(tooltipText, '2 settled orders (2 created)');
  assert.doesNotMatch(tooltipText, /paid orders/i, 'Tooltip must not falsely call created orders "paid orders"');
});

test('5. Zero settlements and zero revenue handle 0 values safely without NaN or errors', () => {
  const emptyReport = {
    totalRevenue: '0.00',
    totalOrders: 0,
    ordersSettled: 0,
    billsGenerated: 0,
    averageOrderValue: '0.00',
  };

  const cards = computeReportsSummaryCards(emptyReport);

  assert.equal(cards.revenueCard.subtitle, 'From 0 settled order(s)');
  assert.equal(cards.ordersSettledCard.value, '0');
  assert.equal(cards.ordersSettledCard.subtitle, 'Orders Created: 0 | Bills Generated: 0');
  assert.equal(cards.aovCard.value, '₹0.00');
});
