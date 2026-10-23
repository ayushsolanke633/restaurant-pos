import test from 'node:test';
import assert from 'node:assert/strict';

// Simulator for the Simplified BillModal & Billing Workflow
// Matches the exact implementation of BillModal.jsx

class SimplifiedBillModalSimulator {
  constructor(billingServiceMock, callbacks = {}) {
    this.billingService = billingServiceMock;
    this.callbacks = callbacks;

    this.isOpen = false;
    this.order = null;
    this.bill = null;
    this.loadingBill = false;
    this.generatingBill = false;
    this.submittingPayment = false;
    this.paymentMethod = 'CASH';
    this.transactionRef = '';
    this.error = null;
    this.successMsg = null;
    this.paymentCompleted = false;
    this.initializeInProgress = false;
  }

  // Corresponds to useEffect([isOpen, order])
  async open(order) {
    this.isOpen = true;
    this.order = order;
    this.paymentMethod = 'CASH';
    this.transactionRef = '';
    await this.initializeBill(order);
  }

  close() {
    if (this.submittingPayment || this.generatingBill) return;
    this.isOpen = false;
    this.resetModalState();
    if (this.callbacks.onClose) {
      this.callbacks.onClose();
    }
  }

  resetModalState() {
    this.order = null;
    this.bill = null;
    this.loadingBill = false;
    this.generatingBill = false;
    this.submittingPayment = false;
    this.paymentMethod = 'CASH';
    this.transactionRef = '';
    this.error = null;
    this.successMsg = null;
    this.paymentCompleted = false;
    this.initializeInProgress = false;
  }

  // Corresponds to initializeBill in BillModal.jsx
  async initializeBill(orderToBill) {
    if (!orderToBill || !orderToBill.id || this.initializeInProgress) return;
    this.initializeInProgress = true;
    this.loadingBill = true;
    this.error = null;
    this.successMsg = null;

    const isAlreadyCompleted = orderToBill.status === 'COMPLETED';
    this.paymentCompleted = isAlreadyCompleted;

    try {
      // 1. Check if bill already exists for this order
      let existingBill = null;
      try {
        existingBill = await this.billingService.getBillByOrderId(orderToBill.id);
      } catch {
        existingBill = null;
      }

      if (existingBill) {
        this.bill = existingBill;
        return;
      }

      // If order is completed or cancelled, do not create a new bill
      if (isAlreadyCompleted || orderToBill.status === 'CANCELLED') {
        this.bill = null;
        return;
      }

      // 2. Automatically generate bill in background using existing billing workflow
      this.generatingBill = true;
      const newBill = await this.billingService.createBill(orderToBill.id);
      this.bill = newBill;
      if (this.callbacks.onBillGenerated) {
        this.callbacks.onBillGenerated(newBill, orderToBill);
      }
    } catch (err) {
      const serverMsg = err.message || '';
      if (serverMsg.toLowerCase().includes('already exists')) {
        try {
          const existing = await this.billingService.getBillByOrderId(orderToBill.id);
          this.bill = existing;
        } catch {
          this.error = serverMsg;
        }
      } else if (serverMsg.toLowerCase().includes('not in ready status')) {
        this.bill = null;
        this.error = `Order #${orderToBill.id} is currently ${orderToBill.status || 'IN PREPARATION'}. All kitchen batches must be marked as READY before generating a bill.`;
      } else {
        this.bill = null;
        this.error = serverMsg || 'Failed to initialize bill';
      }
    } finally {
      this.initializeInProgress = false;
      this.loadingBill = false;
      this.generatingBill = false;
    }
  }

  // Corresponds to handleProcessPayment in BillModal.jsx
  // Directly processes payment with one click without secondary confirmation box
  async handleProcessPayment() {
    if (!this.bill || this.submittingPayment) {
      if (!this.bill) this.error = 'Please wait for bill initialization before collecting payment.';
      return;
    }

    this.submittingPayment = true;
    this.error = null;
    try {
      const paymentPayload = {
        amount: this.bill.totalAmount,
        paymentMethod: this.paymentMethod.toUpperCase(),
        transactionReference: this.transactionRef.trim() || null,
      };

      const paymentResult = await this.billingService.processPayment(this.bill.id, paymentPayload);
      this.paymentCompleted = true;
      this.successMsg = `Payment of ₹${Number(paymentResult.amount).toFixed(2)} recorded via ${paymentResult.paymentMethod}. Order COMPLETED and Table Released!`;

      if (this.callbacks.onPaymentSuccess) {
        this.callbacks.onPaymentSuccess(paymentResult);
      }
      return paymentResult;
    } catch (err) {
      const msg = err.message || 'Failed to process payment';
      this.error = msg;
      throw err;
    } finally {
      this.submittingPayment = false;
    }
  }
}

// ----------------------------------------------------
// Test Suite: Simplified Billing Workflow
// ----------------------------------------------------

test('1. CASH payment completes with one payment-action click after selecting method', async () => {
  let createdBillsCount = 0;
  let paymentProcessCount = 0;
  const existingBills = new Map();

  const mockBillingService = {
    async getBillByOrderId(orderId) {
      if (existingBills.has(orderId)) return existingBills.get(orderId);
      throw new Error('Bill not found with order id: ' + orderId);
    },
    async createBill(orderId) {
      createdBillsCount++;
      const b = { id: 101, orderId, billNumber: 'BILL-000101', totalAmount: '250.00' };
      existingBills.set(orderId, b);
      return b;
    },
    async processPayment(billId, payload) {
      paymentProcessCount++;
      return {
        id: 1,
        billId,
        amount: payload.amount,
        paymentMethod: payload.paymentMethod,
        transactionReference: payload.transactionReference,
        paymentStatus: 'PAID',
      };
    },
  };

  const modal = new SimplifiedBillModalSimulator(mockBillingService);

  // Open modal for READY order
  await modal.open({ id: 501, status: 'READY', orderType: 'DINE_IN' });

  // Bill is generated automatically in the background
  assert.equal(modal.isOpen, true);
  assert.notEqual(modal.bill, null);
  assert.equal(modal.bill.id, 101);
  assert.equal(createdBillsCount, 1);
  assert.equal(modal.paymentMethod, 'CASH');

  // Admin clicks one primary action: Collect Payment & Complete
  const result = await modal.handleProcessPayment();

  // One click completes the payment
  assert.equal(paymentProcessCount, 1);
  assert.equal(result.paymentStatus, 'PAID');
  assert.equal(result.paymentMethod, 'CASH');
  assert.equal(result.transactionReference, null);
  assert.equal(modal.paymentCompleted, true);
  assert.match(modal.successMsg, /Payment of ₹250\.00 recorded via CASH/);
});

test('2. UPI payment works with and without a reference ID', async () => {
  const existingBills = new Map();
  let paymentPayloads = [];

  const mockBillingService = {
    async getBillByOrderId(orderId) {
      if (existingBills.has(orderId)) return existingBills.get(orderId);
      throw new Error('404');
    },
    async createBill(orderId) {
      const b = { id: 102, orderId, billNumber: 'BILL-000102', totalAmount: '150.00' };
      existingBills.set(orderId, b);
      return b;
    },
    async processPayment(billId, payload) {
      paymentPayloads.push(payload);
      return { id: 2, billId, ...payload, paymentStatus: 'PAID' };
    },
  };

  // Case A: UPI without reference ID
  const modalA = new SimplifiedBillModalSimulator(mockBillingService);
  await modalA.open({ id: 502, status: 'READY' });
  modalA.paymentMethod = 'UPI';
  modalA.transactionRef = ''; // Empty optional reference
  await modalA.handleProcessPayment();

  assert.equal(paymentPayloads[0].paymentMethod, 'UPI');
  assert.equal(paymentPayloads[0].transactionReference, null);
  assert.equal(modalA.paymentCompleted, true);

  // Case B: UPI with reference ID
  const modalB = new SimplifiedBillModalSimulator(mockBillingService);
  await modalB.open({ id: 503, status: 'READY' });
  modalB.paymentMethod = 'UPI';
  modalB.transactionRef = 'UPI-TXN-987654321';
  await modalB.handleProcessPayment();

  assert.equal(paymentPayloads[1].paymentMethod, 'UPI');
  assert.equal(paymentPayloads[1].transactionReference, 'UPI-TXN-987654321');
  assert.equal(modalB.paymentCompleted, true);
});

test('3. CARD payment works with optional approval reference', async () => {
  const existingBills = new Map();
  let processedPayload = null;

  const mockBillingService = {
    async getBillByOrderId(orderId) {
      if (existingBills.has(orderId)) return existingBills.get(orderId);
      throw new Error('404');
    },
    async createBill(orderId) {
      const b = { id: 103, orderId, billNumber: 'BILL-000103', totalAmount: '450.00' };
      existingBills.set(orderId, b);
      return b;
    },
    async processPayment(billId, payload) {
      processedPayload = payload;
      return { id: 3, billId, ...payload, paymentStatus: 'PAID' };
    },
  };

  const modal = new SimplifiedBillModalSimulator(mockBillingService);
  await modal.open({ id: 504, status: 'READY' });
  modal.paymentMethod = 'CARD';
  modal.transactionRef = 'AUTH-778899';
  await modal.handleProcessPayment();

  assert.equal(processedPayload.paymentMethod, 'CARD');
  assert.equal(processedPayload.transactionReference, 'AUTH-778899');
  assert.equal(modal.paymentCompleted, true);
  assert.match(modal.successMsg, /Payment of ₹450\.00 recorded via CARD/);
});

test('4. Repeated clicks do not create duplicate payments or bills', async () => {
  let paymentCallCount = 0;
  const mockBillingService = {
    async getBillByOrderId(orderId) {
      return { id: 104, orderId, billNumber: 'BILL-000104', totalAmount: '300.00' };
    },
    async processPayment(billId, payload) {
      paymentCallCount++;
      // Simulate network delay
      await new Promise((r) => setTimeout(r, 60));
      return { id: 4, billId, ...payload, paymentStatus: 'PAID' };
    },
  };

  const modal = new SimplifiedBillModalSimulator(mockBillingService);
  await modal.open({ id: 505, status: 'READY' });

  // Rapidly click "Collect Payment & Complete" twice while in-flight
  const p1 = modal.handleProcessPayment();
  const p2 = modal.handleProcessPayment();

  await Promise.all([p1, p2]);

  assert.equal(paymentCallCount, 1, 'Only one payment request must be dispatched on concurrent clicks');
});

test('5. Failed requests can be retried safely without duplicating the bill or payment', async () => {
  let attemptCount = 0;
  const mockBillingService = {
    async getBillByOrderId(orderId) {
      return { id: 105, orderId, billNumber: 'BILL-000105', totalAmount: '200.00' };
    },
    async processPayment(billId, payload) {
      attemptCount++;
      if (attemptCount === 1) {
        throw new Error('Payment gateway timeout. Please retry.');
      }
      return { id: 5, billId, ...payload, paymentStatus: 'PAID' };
    },
  };

  const modal = new SimplifiedBillModalSimulator(mockBillingService);
  await modal.open({ id: 506, status: 'READY' });
  assert.equal(modal.bill.id, 105);

  // Attempt 1: Fails
  await assert.rejects(async () => {
    await modal.handleProcessPayment();
  }, { message: 'Payment gateway timeout. Please retry.' });

  assert.equal(modal.error, 'Payment gateway timeout. Please retry.');
  assert.equal(modal.submittingPayment, false, 'Button must be re-enabled after failure');
  assert.equal(modal.bill.id, 105, 'Bill ID must remain unchanged');
  assert.equal(modal.paymentCompleted, false);

  // Attempt 2: Safe retry succeeds
  const retryResult = await modal.handleProcessPayment();
  assert.equal(retryResult.paymentStatus, 'PAID');
  assert.equal(modal.paymentCompleted, true);
  assert.equal(attemptCount, 2);
});

test('6. Closing/reopening the billing screen does not create another bill', async () => {
  let createBillCalls = 0;
  let getBillCalls = 0;
  const existingBills = new Map();

  const mockBillingService = {
    async getBillByOrderId(orderId) {
      getBillCalls++;
      if (existingBills.has(orderId)) return existingBills.get(orderId);
      throw new Error('404 Not Found');
    },
    async createBill(orderId) {
      createBillCalls++;
      const b = { id: 106, orderId, billNumber: 'BILL-000106', totalAmount: '500.00' };
      existingBills.set(orderId, b);
      return b;
    },
  };

  const modal = new SimplifiedBillModalSimulator(mockBillingService);

  // 1st Open: generates bill 106
  await modal.open({ id: 507, status: 'READY' });
  assert.equal(modal.bill.id, 106);
  assert.equal(createBillCalls, 1);

  // Close modal without paying
  modal.close();
  assert.equal(modal.isOpen, false);
  assert.equal(modal.bill, null);

  // 2nd Open: fetches existing bill 106; createBill is NOT called again
  await modal.open({ id: 507, status: 'READY' });
  assert.equal(modal.bill.id, 106);
  assert.equal(createBillCalls, 1, 'createBill must NOT be called again upon reopening');
  assert.equal(getBillCalls, 2);
});

test('7. Billing is blocked until all required kitchen batches are READY when KDS is enabled', async () => {
  const mockBillingService = {
    async getBillByOrderId() {
      throw new Error('404 Not Found');
    },
    async createBill() {
      // Backend throws 409 OrderWorkflowConflictException when order not in READY status
      throw new Error('Order is not in READY status');
    },
  };

  const modal = new SimplifiedBillModalSimulator(mockBillingService);

  // Open modal for order still in PREPARING (e.g. batch 2 still cooking)
  await modal.open({ id: 508, status: 'PREPARING' });

  assert.equal(modal.bill, null, 'No bill should be created for non-ready order');
  assert.match(modal.error, /Order #508 is currently PREPARING\. All kitchen batches must be marked as READY/);
  assert.equal(modal.paymentCompleted, false);
});
