import test from 'node:test';
import assert from 'node:assert/strict';

// Simulator for Admin-Only Order Cancellation permissions and behavior
class OrderCancellationPolicySimulator {
  constructor(currentUser) {
    this.currentUser = currentUser; // { id, role: 'ADMIN' | 'WAITER' | 'KITCHEN' }
    this.tables = [
      { id: 1, tableNumber: 1, status: 'OCCUPIED' },
      { id: 2, tableNumber: 2, status: 'OCCUPIED' },
    ];
    this.orders = [
      { id: 101, tableId: 1, status: 'NEW', orderType: 'DINE_IN' },
      { id: 102, tableId: 1, status: 'ACCEPTED', orderType: 'DINE_IN' },
      { id: 103, tableId: 2, status: 'PREPARING', orderType: 'DINE_IN' },
      { id: 104, tableId: 2, status: 'READY', orderType: 'DINE_IN' },
      { id: 105, tableId: null, status: 'COMPLETED', orderType: 'TAKEAWAY' },
      { id: 106, tableId: null, status: 'CANCELLED', orderType: 'TAKEAWAY' },
    ];
    this.bills = new Set(); // orderIds that have bills
  }

  isAdmin() {
    return this.currentUser?.role === 'ADMIN' || this.currentUser?.role === 'SUPER_ADMIN';
  }

  canCancelOrder(orderId) {
    if (!this.isAdmin()) {
      return { allowed: false, reason: 'Access Denied: Only ADMIN can cancel orders.' };
    }
    const order = this.orders.find((o) => o.id === orderId);
    if (!order) {
      return { allowed: false, reason: 'Order not found.' };
    }
    if (this.bills.has(orderId)) {
      return { allowed: false, reason: 'Cancellation unavailable: A bill has already been generated for this order.' };
    }
    const status = (order.status || '').toUpperCase();
    if (status === 'PREPARING' || status === 'READY') {
      return { allowed: false, reason: 'Cancellation unavailable: Order is in kitchen preparation or ready.' };
    }
    if (status === 'COMPLETED') {
      return { allowed: false, reason: 'Cancellation unavailable: Order is completed and paid.' };
    }
    if (status === 'CANCELLED') {
      return { allowed: false, reason: 'Order is already cancelled.' };
    }
    if (status === 'NEW' || status === 'ACCEPTED') {
      return { allowed: true, reason: '' };
    }
    return { allowed: false, reason: 'Invalid cancellation status.' };
  }

  cancelOrder(orderId) {
    const check = this.canCancelOrder(orderId);
    if (!check.allowed) {
      const err = new Error(check.reason);
      if (!this.isAdmin()) {
        err.status = 403;
      } else {
        err.status = 409;
      }
      throw err;
    }

    const order = this.orders.find((o) => o.id === orderId);
    order.status = 'CANCELLED';

    // Release table if dine in and no other active orders on table
    if (order.tableId) {
      const remainingActive = this.orders.some(
        (o) => o.tableId === order.tableId && o.id !== orderId && ['NEW', 'ACCEPTED', 'PREPARING', 'READY'].includes(o.status)
      );
      if (!remainingActive) {
        const table = this.tables.find((t) => t.id === order.tableId);
        if (table) table.status = 'AVAILABLE';
      }
    }

    return order;
  }
}

test('1. Non-admin roles (WAITER, KITCHEN) cannot cancel orders', () => {
  const waiterSim = new OrderCancellationPolicySimulator({ id: 2, role: 'WAITER' });
  assert.equal(waiterSim.isAdmin(), false);
  const waiterCheck = waiterSim.canCancelOrder(101);
  assert.equal(waiterCheck.allowed, false);
  assert.match(waiterCheck.reason, /Only ADMIN can cancel orders/);
  assert.throws(() => waiterSim.cancelOrder(101), { status: 403 });

  const kitchenSim = new OrderCancellationPolicySimulator({ id: 3, role: 'KITCHEN' });
  assert.equal(kitchenSim.isAdmin(), false);
  const kitchenCheck = kitchenSim.canCancelOrder(101);
  assert.equal(kitchenCheck.allowed, false);
  assert.throws(() => kitchenSim.cancelOrder(101), { status: 403 });
});

test('2. ADMIN can cancel NEW and ACCEPTED orders when unbilled', () => {
  const adminSim = new OrderCancellationPolicySimulator({ id: 1, role: 'ADMIN' });
  assert.equal(adminSim.isAdmin(), true);

  // Can cancel NEW order
  const checkNew = adminSim.canCancelOrder(101);
  assert.equal(checkNew.allowed, true);
  const cancelledNew = adminSim.cancelOrder(101);
  assert.equal(cancelledNew.status, 'CANCELLED');

  // Can cancel ACCEPTED order
  const checkAccepted = adminSim.canCancelOrder(102);
  assert.equal(checkAccepted.allowed, true);
  const cancelledAccepted = adminSim.cancelOrder(102);
  assert.equal(cancelledAccepted.status, 'CANCELLED');

  // Table 1 had orders 101 and 102; now that both are cancelled, table status becomes AVAILABLE
  assert.equal(adminSim.tables.find((t) => t.id === 1).status, 'AVAILABLE');
});

test('3. ADMIN cannot cancel order when a bill has been generated', () => {
  const adminSim = new OrderCancellationPolicySimulator({ id: 1, role: 'ADMIN' });
  adminSim.bills.add(101); // Bill exists for order 101

  const check = adminSim.canCancelOrder(101);
  assert.equal(check.allowed, false);
  assert.match(check.reason, /A bill has already been generated/);
  assert.throws(() => adminSim.cancelOrder(101), { status: 409 });
});

test('4. ADMIN cannot cancel order in PREPARING, READY, COMPLETED, or CANCELLED status', () => {
  const adminSim = new OrderCancellationPolicySimulator({ id: 1, role: 'ADMIN' });

  // PREPARING
  const checkPrep = adminSim.canCancelOrder(103);
  assert.equal(checkPrep.allowed, false);
  assert.match(checkPrep.reason, /in kitchen preparation or ready/);

  // READY
  const checkReady = adminSim.canCancelOrder(104);
  assert.equal(checkReady.allowed, false);
  assert.match(checkReady.reason, /in kitchen preparation or ready/);

  // COMPLETED
  const checkComp = adminSim.canCancelOrder(105);
  assert.equal(checkComp.allowed, false);
  assert.match(checkComp.reason, /completed and paid/);

  // CANCELLED
  const checkCanc = adminSim.canCancelOrder(106);
  assert.equal(checkCanc.allowed, false);
  assert.match(checkCanc.reason, /already cancelled/);
});

test('5. Table remains OCCUPIED if other active orders remain when one is cancelled', () => {
  const adminSim = new OrderCancellationPolicySimulator({ id: 1, role: 'ADMIN' });
  // Add second active order on table 1
  adminSim.orders.push({ id: 199, tableId: 1, status: 'ACCEPTED', orderType: 'DINE_IN' });

  // Cancel order 101 on table 1
  adminSim.cancelOrder(101);
  // Table 1 must REMAIN OCCUPIED because order 102 and order 199 are still active on it
  assert.equal(adminSim.tables.find((t) => t.id === 1).status, 'OCCUPIED');
});
