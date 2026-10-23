import test from 'node:test';
import assert from 'node:assert/strict';

// Simulation of TablesPage and BillModal state management workflow
// Validating consecutive billing without page refreshes

class TablesPageStateSimulator {
  constructor() {
    this.tables = [
      { id: 1, tableNumber: 1, capacity: 4, status: 'OCCUPIED' },
      { id: 2, tableNumber: 2, capacity: 2, status: 'OCCUPIED' },
      { id: 3, tableNumber: 3, capacity: 6, status: 'AVAILABLE' },
    ];
    this.activeOrders = {
      1: { id: 501, tableId: 1, status: 'READY', orderType: 'DINE_IN', totalAmount: '126.00' },
      2: { id: 502, tableId: 2, status: 'READY', orderType: 'DINE_IN', totalAmount: '84.00' },
    };
    this.selectedOrder = null;
    this.isOrderModalOpen = false;
    this.isBillModalOpen = false;
    this.bills = new Map(); // orderId -> bill
  }

  // Handle clicking on an occupied table
  handleTableCardClick(tableId) {
    const table = this.tables.find((t) => t.id === tableId);
    if (!table) throw new Error('Table not found');

    const activeOrd = this.activeOrders[table.id];
    if (activeOrd) {
      // Shallow copy matching TablesPage.jsx implementation
      this.selectedOrder = {
        ...activeOrd,
        tableName: `Table ${table.tableNumber}`,
        tableNumber: table.tableNumber,
      };
      this.isOrderModalOpen = true;
    }
  }

  handleGenerateBillFromOrderModal() {
    this.isOrderModalOpen = false;
    this.isBillModalOpen = true;
  }

  // Matching handleBillGenerated in TablesPage.jsx
  handleBillGenerated(newBill, billedOrder) {
    if (billedOrder?.tableId) {
      this.tables = this.tables.map((t) =>
        t.id === billedOrder.tableId ? { ...t, status: 'BILLING' } : t
      );
    }
    // Record generated bill
    this.bills.set(billedOrder.id, newBill);
  }

  // Matching BillModal onClose in TablesPage.jsx
  handleCloseBillModal() {
    this.isBillModalOpen = false;
    this.selectedOrder = null; // Clears selectedOrder so next table is clean
  }
}

class BillModalStateSimulator {
  constructor() {
    this.isOpen = false;
    this.order = null;
    this.bill = null;
    this.loadingBill = false;
    this.generatingBill = false;
    this.submittingPayment = false;
    this.error = null;
    this.successMsg = null;
  }

  open(order, existingBill = null) {
    this.isOpen = true;
    this.order = order;
    this.bill = existingBill;
    this.loadingBill = false;
    this.generatingBill = false;
    this.error = null;
    this.successMsg = null;
  }

  close() {
    this.isOpen = false;
    this.resetModalState();
  }

  resetModalState() {
    this.order = null;
    this.bill = null;
    this.loadingBill = false;
    this.generatingBill = false;
    this.submittingPayment = false;
    this.error = null;
    this.successMsg = null;
  }

  async generateBill(createBillApi) {
    // Guard against duplicate submissions while generating
    if (!this.order || !this.order.id || this.generatingBill) return;

    this.generatingBill = true;
    this.error = null;
    try {
      const newBill = await createBillApi(this.order.id);
      this.bill = newBill;
      this.successMsg = `Bill ${newBill.billNumber} generated successfully!`;
      return newBill;
    } catch (err) {
      this.bill = null;
      this.error = err.message || 'Failed to generate bill';
      throw err;
    } finally {
      this.generatingBill = false;
    }
  }
}

test('Consecutive billing: Table A then Table B without page refresh', async () => {
  const page = new TablesPageStateSimulator();
  const modal = new BillModalStateSimulator();

  // 1. Select Table A
  page.handleTableCardClick(1);
  assert.equal(page.selectedOrder.id, 501);
  assert.equal(page.selectedOrder.tableId, 1);
  assert.equal(page.isOrderModalOpen, true);

  // Transition to Bill Modal for Table A
  page.handleGenerateBillFromOrderModal();
  modal.open(page.selectedOrder);
  assert.equal(modal.isOpen, true);
  assert.equal(modal.order.id, 501);
  assert.equal(modal.bill, null);

  // Generate Bill for Table A
  const billA = await modal.generateBill(async (orderId) => ({
    id: 101,
    billNumber: 'BILL-001',
    orderId,
    totalAmount: '126.00',
  }));

  assert.equal(billA.orderId, 501);
  assert.equal(modal.bill.id, 101);
  assert.equal(modal.successMsg, 'Bill BILL-001 generated successfully!');

  // Trigger page callback
  page.handleBillGenerated(billA, modal.order);
  assert.equal(page.tables.find((t) => t.id === 1).status, 'BILLING');

  // Close Bill Modal for Table A
  modal.close();
  page.handleCloseBillModal();

  assert.equal(modal.isOpen, false);
  assert.equal(modal.bill, null, 'Modal internal bill state must be reset');
  assert.equal(modal.order, null, 'Modal internal order state must be reset');
  assert.equal(page.selectedOrder, null, 'Page selectedOrder must be null after closing');

  // 2. Select Table B immediately WITHOUT page refresh
  page.handleTableCardClick(2);
  assert.notEqual(page.selectedOrder, null);
  assert.equal(page.selectedOrder.id, 502, 'Must select Order B, not retained Order A');
  assert.equal(page.selectedOrder.tableId, 2);

  // Transition to Bill Modal for Table B
  page.handleGenerateBillFromOrderModal();
  modal.open(page.selectedOrder);
  assert.equal(modal.isOpen, true);
  assert.equal(modal.order.id, 502);
  assert.equal(modal.bill, null, 'Table B modal must start with null bill, not Table A bill');

  // Generate Bill for Table B
  const billB = await modal.generateBill(async (orderId) => ({
    id: 102,
    billNumber: 'BILL-002',
    orderId,
    totalAmount: '84.00',
  }));

  assert.equal(billB.orderId, 502);
  assert.equal(modal.bill.id, 102);
  assert.notEqual(billA.id, billB.id, 'Bill IDs must be distinct');
  assert.notEqual(billA.orderId, billB.orderId, 'Order IDs must be distinct');

  // Trigger page callback for Table B
  page.handleBillGenerated(billB, modal.order);
  assert.equal(page.tables.find((t) => t.id === 2).status, 'BILLING');

  modal.close();
  page.handleCloseBillModal();

  // Both tables are now in BILLING status without refreshing the page
  assert.equal(page.tables.find((t) => t.id === 1).status, 'BILLING');
  assert.equal(page.tables.find((t) => t.id === 2).status, 'BILLING');
});

test('Duplicate submission prevention during bill generation', async () => {
  const modal = new BillModalStateSimulator();
  modal.open({ id: 501 });

  let callCount = 0;
  const mockApi = async (orderId) => {
    callCount++;
    await new Promise((resolve) => setTimeout(resolve, 50));
    return { id: 101, billNumber: 'BILL-001', orderId };
  };

  // Launch first generation
  const p1 = modal.generateBill(mockApi);
  assert.equal(modal.generatingBill, true);

  // Concurrent second call while p1 is in flight
  const p2 = modal.generateBill(mockApi);

  await Promise.all([p1, p2]);
  assert.equal(callCount, 1, 'API should only be called once when duplicate submission is attempted');
});

test('Ineligible table error does not block subsequent table billing', async () => {
  const page = new TablesPageStateSimulator();
  const modal = new BillModalStateSimulator();

  // Table A order is IN PREPARATION (ineligible)
  page.activeOrders[1].status = 'PREPARING';

  // 1. Attempt to bill Table A
  page.handleTableCardClick(1);
  page.handleGenerateBillFromOrderModal();
  modal.open(page.selectedOrder);

  await assert.rejects(
    async () => {
      await modal.generateBill(async () => {
        throw new Error('Order is not in READY status');
      });
    },
    { message: 'Order is not in READY status' }
  );

  assert.equal(modal.bill, null, 'Bill must be null on failure');
  assert.equal(modal.error, 'Order is not in READY status');
  assert.equal(page.tables.find((t) => t.id === 1).status, 'OCCUPIED', 'Table A status must remain OCCUPIED');

  // Close modal for Table A
  modal.close();
  page.handleCloseBillModal();

  // 2. Select Table B immediately (eligible)
  page.handleTableCardClick(2);
  assert.equal(page.selectedOrder.id, 502);
  page.handleGenerateBillFromOrderModal();
  modal.open(page.selectedOrder);

  // Generate bill for Table B succeeds
  const billB = await modal.generateBill(async (orderId) => ({
    id: 102,
    billNumber: 'BILL-002',
    orderId,
  }));

  assert.equal(billB.orderId, 502);
  page.handleBillGenerated(billB, modal.order);
  assert.equal(page.tables.find((t) => t.id === 2).status, 'BILLING');
});
