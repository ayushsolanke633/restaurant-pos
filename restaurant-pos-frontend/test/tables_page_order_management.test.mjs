import test from 'node:test';
import assert from 'node:assert/strict';

// Simulator for TablesPage order management workflows
class TablesPageManagerSimulator {
  constructor() {
    this.tables = [
      { id: 1, tableNumber: 1, capacity: 4, status: 'AVAILABLE' },
      { id: 2, tableNumber: 2, capacity: 2, status: 'OCCUPIED' },
      { id: 3, tableNumber: 3, capacity: 6, status: 'OCCUPIED' },
      { id: 4, tableNumber: 4, capacity: 4, status: 'BILLING' },
    ];

    // activeOrdersByTable: tableId -> [OrderResponse]
    this.activeOrdersByTable = {
      2: [
        {
          id: 101,
          tableId: 2,
          tableNumber: 2,
          status: 'NEW',
          orderType: 'DINE_IN',
          subtotal: 50.00,
          totalAmount: 50.00,
          items: [
            { id: 1, menuItemId: 10, menuItemName: 'Pasta', quantity: 1, unitPrice: 50.00, subtotal: 50.00, batchNumber: 1, status: 'NEW' }
          ],
          createdAt: new Date('2026-10-15T12:00:00Z').toISOString(),
        }
      ],
      // Table 3 has multiple active orders (legacy data test)
      3: [
        {
          id: 103,
          tableId: 3,
          tableNumber: 3,
          status: 'PREPARING',
          orderType: 'DINE_IN',
          subtotal: 75.00,
          totalAmount: 75.00,
          items: [
            { id: 3, menuItemId: 20, menuItemName: 'Pizza', quantity: 1, unitPrice: 75.00, subtotal: 75.00, batchNumber: 1, status: 'PREPARING' }
          ],
          createdAt: new Date('2026-10-15T12:30:00Z').toISOString(),
        },
        {
          id: 102,
          tableId: 3,
          tableNumber: 3,
          status: 'NEW',
          orderType: 'DINE_IN',
          subtotal: 30.00,
          totalAmount: 30.00,
          items: [
            { id: 2, menuItemId: 30, menuItemName: 'Garlic Bread', quantity: 2, unitPrice: 15.00, subtotal: 30.00, batchNumber: 1, status: 'NEW' }
          ],
          createdAt: new Date('2026-10-15T12:15:00Z').toISOString(),
        }
      ],
      4: [
        {
          id: 104,
          tableId: 4,
          tableNumber: 4,
          status: 'READY',
          orderType: 'DINE_IN',
          subtotal: 100.00,
          totalAmount: 100.00,
          items: [
            { id: 4, menuItemId: 40, menuItemName: 'Steak', quantity: 1, unitPrice: 100.00, subtotal: 100.00, batchNumber: 1, status: 'READY' }
          ],
          createdAt: new Date('2026-10-15T11:45:00Z').toISOString(),
        }
      ]
    };

    this.bills = new Map();
    this.bills.set(104, { id: 999, orderId: 104, totalAmount: 100.00 });

    // UI state
    this.selectedAvailableTable = null;
    this.isAvailableModalOpen = false;

    this.selectedTable = null;
    this.selectedOrder = null;
    this.isOrderModalOpen = false;

    this.multipleOrdersTable = null;
    this.multipleActiveOrders = [];
    this.isMultipleOrdersModalOpen = false;

    this.isAddItemsModalOpen = false;
    this.isAddOrderModalOpen = false;

    this.kitchenEnabled = true;
  }

  // Admin clicks table card
  handleTableClick(tableId) {
    const table = this.tables.find(t => t.id === tableId);
    if (!table) throw new Error('Table not found');

    if (table.status === 'AVAILABLE') {
      this.selectedAvailableTable = table;
      this.isAvailableModalOpen = true;
      return { modal: 'AVAILABLE_MODAL', table };
    }

    if (table.status === 'OCCUPIED' || table.status === 'BILLING') {
      const orders = this.activeOrdersByTable[table.id] || [];
      if (orders.length > 1) {
        this.multipleOrdersTable = table;
        this.multipleActiveOrders = orders;
        this.isMultipleOrdersModalOpen = true;
        return { modal: 'MULTIPLE_ORDERS_MODAL', table, orders };
      }

      if (orders.length === 1) {
        this.selectedOrder = {
          ...orders[0],
          tableName: `Table ${table.tableNumber}`,
          tableNumber: table.tableNumber,
          tableStatus: table.status,
        };
        this.isOrderModalOpen = true;
        return { modal: 'ORDER_DETAILS_MODAL', order: this.selectedOrder };
      }

      throw new Error(`No active order found for Table ${table.tableNumber}`);
    }

    return null;
  }

  // Admin chooses to Add Order from Available Table Modal
  startAddOrder() {
    if (!this.selectedAvailableTable) throw new Error('No available table selected');
    this.isAvailableModalOpen = false;
    this.isAddOrderModalOpen = true;
    return { tableId: this.selectedAvailableTable.id, locked: true };
  }

  // Admin submits new order for available table
  createDineInOrder(items, notes) {
    if (!this.selectedAvailableTable) throw new Error('No table selected');
    const table = this.selectedAvailableTable;

    if (table.status !== 'AVAILABLE') {
      throw new Error('Table is not available');
    }

    let subtotal = 0;
    const orderItems = items.map((i, idx) => {
      const itemSubtotal = i.quantity * i.unitPrice;
      subtotal += itemSubtotal;
      return {
        id: Date.now() + idx,
        menuItemId: i.menuItemId,
        menuItemName: i.name,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
        subtotal: itemSubtotal,
        batchNumber: 1,
        status: 'NEW',
      };
    });

    const newOrder = {
      id: 200 + Date.now() % 1000,
      tableId: table.id,
      tableNumber: table.tableNumber,
      status: 'NEW',
      orderType: 'DINE_IN',
      notes,
      subtotal,
      totalAmount: subtotal,
      items: orderItems,
      createdAt: new Date().toISOString(),
    };

    // Table turns OCCUPIED
    table.status = 'OCCUPIED';
    this.activeOrdersByTable[table.id] = [newOrder];
    this.isAddOrderModalOpen = false;
    this.selectedAvailableTable = null;

    return newOrder;
  }

  // Admin selects an order from the multiple orders list
  selectOrderFromMultiple(orderId) {
    const order = this.multipleActiveOrders.find(o => o.id === orderId);
    if (!order) throw new Error('Selected order not found');

    this.isMultipleOrdersModalOpen = false;
    this.selectedOrder = {
      ...order,
      tableName: `Table ${this.multipleOrdersTable.tableNumber}`,
      tableNumber: this.multipleOrdersTable.tableNumber,
      tableStatus: this.multipleOrdersTable.status,
    };
    this.isOrderModalOpen = true;
    return this.selectedOrder;
  }

  // Check cancellation eligibility for Admin
  canCancelOrder(order) {
    if (!order) return { allowed: false, reason: 'No order' };
    const billExists = this.bills.has(order.id) || order.tableStatus === 'BILLING';
    if (billExists) {
      return { allowed: false, reason: 'Cancellation unavailable: A bill has already been generated for this order.' };
    }
    if (order.status === 'PREPARING' || order.status === 'READY') {
      return { allowed: false, reason: 'Cancellation unavailable: Order is in kitchen preparation or ready.' };
    }
    if (order.status === 'COMPLETED') {
      return { allowed: false, reason: 'Cancellation unavailable: Order is completed and paid.' };
    }
    if (order.status === 'CANCELLED') {
      return { allowed: false, reason: 'Order is already cancelled.' };
    }
    if (order.status === 'NEW' || order.status === 'ACCEPTED') {
      return { allowed: true, reason: null };
    }
    return { allowed: false, reason: 'Status not eligible for cancellation.' };
  }

  // Check adding items eligibility
  canAddItems(order) {
    if (!order) return { allowed: false, reason: 'No order' };
    const billExists = this.bills.has(order.id) || order.tableStatus === 'BILLING';
    if (billExists) {
      return { allowed: false, reason: 'Cannot add items because a bill has already been generated for this order.' };
    }
    if (order.status === 'COMPLETED' || order.status === 'CANCELLED') {
      return { allowed: false, reason: `Cannot add items: Order is ${order.status.toLowerCase()}.` };
    }
    const allowedStatuses = ['NEW', 'ACCEPTED', 'PREPARING', 'READY'];
    if (allowedStatuses.includes(order.status)) {
      return { allowed: true, reason: null };
    }
    return { allowed: false, reason: 'Cannot add items to order with current status.' };
  }

  // Add items to selected order
  addItemsToOrder(orderId, newItems) {
    const order = this.selectedOrder?.id === orderId
      ? this.selectedOrder
      : (this.activeOrdersByTable[this.selectedOrder?.tableId] || []).find(o => o.id === orderId);

    if (!order) throw new Error('Order not found');

    const eligibility = this.canAddItems(order);
    if (!eligibility.allowed) {
      throw new Error(eligibility.reason);
    }

    if (this.kitchenEnabled) {
      // Incremental batches
      const maxBatch = order.items.reduce((max, i) => Math.max(max, i.batchNumber || 1), 1);
      const nextBatch = maxBatch + 1;

      newItems.forEach((newItem, idx) => {
        const itemSubtotal = newItem.quantity * newItem.unitPrice;
        order.items.push({
          id: Date.now() + idx,
          menuItemId: newItem.menuItemId,
          menuItemName: newItem.name,
          quantity: newItem.quantity,
          unitPrice: newItem.unitPrice,
          subtotal: itemSubtotal,
          batchNumber: nextBatch,
          status: 'NEW',
          notes: newItem.notes || null,
        });
      });

      if (order.status === 'READY') {
        order.status = 'PREPARING';
      }
    } else {
      // Kitchen disabled: combine quantities or append batch 1
      newItems.forEach((newItem) => {
        const existing = order.items.find(
          (i) => i.menuItemId === newItem.menuItemId && (i.notes || null) === (newItem.notes || null)
        );
        if (existing) {
          existing.quantity += newItem.quantity;
          existing.subtotal = existing.quantity * existing.unitPrice;
        } else {
          order.items.push({
            id: Date.now(),
            menuItemId: newItem.menuItemId,
            menuItemName: newItem.name,
            quantity: newItem.quantity,
            unitPrice: newItem.unitPrice,
            subtotal: newItem.quantity * newItem.unitPrice,
            batchNumber: 1,
            status: 'NEW',
            notes: newItem.notes || null,
          });
        }
      });
    }

    // Authoritative total recalculation
    order.subtotal = order.items.reduce((sum, i) => sum + i.subtotal, 0);
    order.totalAmount = order.subtotal;

    return order;
  }

  // Cancel order
  cancelOrder(orderId) {
    const tableId = this.selectedOrder?.tableId;
    const order = this.selectedOrder?.id === orderId ? this.selectedOrder : null;
    if (!order) throw new Error('Order not found');

    const eligibility = this.canCancelOrder(order);
    if (!eligibility.allowed) {
      throw new Error(eligibility.reason);
    }

    order.status = 'CANCELLED';

    // Remove from active orders for this table
    const remaining = (this.activeOrdersByTable[tableId] || []).filter(o => o.id !== orderId && o.status !== 'CANCELLED');
    this.activeOrdersByTable[tableId] = remaining;

    // Check if table should be released
    const table = this.tables.find(t => t.id === tableId);
    if (table) {
      if (remaining.length === 0) {
        table.status = 'AVAILABLE';
      } else {
        // Table remains occupied because other active orders exist!
        table.status = 'OCCUPIED';
      }
    }

    this.isOrderModalOpen = false;
    this.selectedOrder = null;
    return order;
  }
}

// -------------------------------------------------------------
// TESTS
// -------------------------------------------------------------

test('1. Admin selects Available Table -> Opens Available Table modal and creates Dine-In Order', () => {
  const sim = new TablesPageManagerSimulator();

  // Select Table 1 (AVAILABLE)
  const clickResult = sim.handleTableClick(1);
  assert.equal(clickResult.modal, 'AVAILABLE_MODAL');
  assert.equal(clickResult.table.status, 'AVAILABLE');
  assert.equal(sim.isAvailableModalOpen, true);

  // Click Add Order
  const addOrderInit = sim.startAddOrder();
  assert.equal(addOrderInit.tableId, 1);
  assert.equal(addOrderInit.locked, true);
  assert.equal(sim.isAddOrderModalOpen, true);

  // Submit order
  const created = sim.createDineInOrder([
    { menuItemId: 50, name: 'Burger', unitPrice: 25.00, quantity: 2 }
  ], 'Near window');

  assert.equal(created.orderType, 'DINE_IN');
  assert.equal(created.totalAmount, 50.00);
  assert.equal(created.status, 'NEW');

  // Table 1 must now be OCCUPIED
  const table1 = sim.tables.find(t => t.id === 1);
  assert.equal(table1.status, 'OCCUPIED');
  assert.equal(sim.activeOrdersByTable[1].length, 1);
});

test('2. Admin selects Occupied Table with single order -> Opens OrderDetailsModal directly', () => {
  const sim = new TablesPageManagerSimulator();

  const clickResult = sim.handleTableClick(2);
  assert.equal(clickResult.modal, 'ORDER_DETAILS_MODAL');
  assert.equal(clickResult.order.id, 101);
  assert.equal(clickResult.order.status, 'NEW');
  assert.equal(clickResult.order.items.length, 1);
  assert.equal(clickResult.order.totalAmount, 50.00);
});

test('3. Admin selects Occupied Table with multiple active orders (legacy data) -> Displays selection modal and does not modify arbitrary order', () => {
  const sim = new TablesPageManagerSimulator();

  // Table 3 has orders #103 and #102
  const clickResult = sim.handleTableClick(3);
  assert.equal(clickResult.modal, 'MULTIPLE_ORDERS_MODAL');
  assert.equal(clickResult.orders.length, 2);
  assert.equal(sim.isMultipleOrdersModalOpen, true);

  // Admin explicitly selects Order #102
  const selected = sim.selectOrderFromMultiple(102);
  assert.equal(selected.id, 102);
  assert.equal(selected.status, 'NEW');
  assert.equal(sim.isOrderModalOpen, true);
  assert.equal(sim.isMultipleOrdersModalOpen, false);
});

test('4. Admin adds items with KDS enabled -> Increments batch number and keeps existing items unchanged', () => {
  const sim = new TablesPageManagerSimulator();
  sim.kitchenEnabled = true;

  sim.handleTableClick(2); // Order #101 has Batch 1 item
  const originalItem = { ...sim.selectedOrder.items[0] };

  // Add new item
  const updatedOrder = sim.addItemsToOrder(101, [
    { menuItemId: 99, name: 'Cold Drink', unitPrice: 10.00, quantity: 2, notes: 'Chilled' }
  ]);

  assert.equal(updatedOrder.items.length, 2);
  // Existing item untouched
  assert.equal(updatedOrder.items[0].id, originalItem.id);
  assert.equal(updatedOrder.items[0].quantity, 1);
  assert.equal(updatedOrder.items[0].batchNumber, 1);

  // New item has Batch #2 and NEW status
  const newItem = updatedOrder.items[1];
  assert.equal(newItem.batchNumber, 2);
  assert.equal(newItem.status, 'NEW');
  assert.equal(newItem.notes, 'Chilled');

  // Total recalculated authoritatively
  assert.equal(updatedOrder.totalAmount, 70.00); // 50 + (2 * 10)
});

test('5. Admin adds items with KDS disabled -> Consolidates items in Batch 1 without kitchen incremental batching', () => {
  const sim = new TablesPageManagerSimulator();
  sim.kitchenEnabled = false;

  sim.handleTableClick(2); // Order #101 has 1x Pasta (menuItemId: 10)

  // Add 2 more Pasta
  const updatedOrder = sim.addItemsToOrder(101, [
    { menuItemId: 10, name: 'Pasta', unitPrice: 50.00, quantity: 2 }
  ]);

  // Consolidates into single item with quantity 3
  assert.equal(updatedOrder.items.length, 1);
  assert.equal(updatedOrder.items[0].quantity, 3);
  assert.equal(updatedOrder.items[0].batchNumber, 1);
  assert.equal(updatedOrder.totalAmount, 150.00);
});

test('6. Billing safeguard: Adding items is blocked if a bill has already been generated', () => {
  const sim = new TablesPageManagerSimulator();

  // Table 4 is in BILLING status with existing bill #999 for Order #104
  sim.handleTableClick(4);

  const check = sim.canAddItems(sim.selectedOrder);
  assert.equal(check.allowed, false);
  assert.match(check.reason, /bill has already been generated/i);

  assert.throws(() => {
    sim.addItemsToOrder(104, [{ menuItemId: 10, name: 'Cake', unitPrice: 15.00, quantity: 1 }]);
  }, /bill has already been generated/i);
});

test('7. Cancellation safeguard: Admin can cancel NEW or ACCEPTED order without a bill', () => {
  const sim = new TablesPageManagerSimulator();

  sim.handleTableClick(2); // Order #101 is NEW, no bill
  const check = sim.canCancelOrder(sim.selectedOrder);
  assert.equal(check.allowed, true);

  const cancelled = sim.cancelOrder(101);
  assert.equal(cancelled.status, 'CANCELLED');

  // Table 2 had only Order 101, so it must now be released to AVAILABLE
  const table2 = sim.tables.find(t => t.id === 2);
  assert.equal(table2.status, 'AVAILABLE');
});

test('8. Cancellation safeguard: Cancellation is blocked for PREPARING, READY, or billed orders with clear explanations', () => {
  const sim = new TablesPageManagerSimulator();

  // Order 103 on Table 3 is PREPARING
  const prepOrder = sim.activeOrdersByTable[3][0];
  const prepCheck = sim.canCancelOrder(prepOrder);
  assert.equal(prepCheck.allowed, false);
  assert.match(prepCheck.reason, /kitchen preparation/i);

  // Order 104 on Table 4 has a bill generated
  const billOrder = sim.activeOrdersByTable[4][0];
  billOrder.tableStatus = 'BILLING';
  const billCheck = sim.canCancelOrder(billOrder);
  assert.equal(billCheck.allowed, false);
  assert.match(billCheck.reason, /bill has already been generated/i);
});

test('9. Table occupancy safeguard: Never release table while another active order still occupies it', () => {
  const sim = new TablesPageManagerSimulator();

  // Table 3 has two active orders: #103 (PREPARING) and #102 (NEW)
  sim.handleTableClick(3);
  sim.selectOrderFromMultiple(102);

  // Cancel order 102
  const cancelled = sim.cancelOrder(102);
  assert.equal(cancelled.status, 'CANCELLED');

  // Table 3 still has active order #103 -> Must remain OCCUPIED!
  const table3 = sim.tables.find(t => t.id === 3);
  assert.equal(table3.status, 'OCCUPIED');
  assert.equal(sim.activeOrdersByTable[3].length, 1);
  assert.equal(sim.activeOrdersByTable[3][0].id, 103);
});
