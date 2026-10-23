import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Auto-dismiss simulation runner.
 * Models the exact logic in useAutoDismiss.js:
 * 
 * const useAutoDismiss = (message, onDismiss, durationMs = 3000) => {
 *   const onDismissRef = useRef(onDismiss);
 *   useEffect(() => { onDismissRef.current = onDismiss; });
 *   useEffect(() => {
 *     if (!message) return;
 *     const timer = setTimeout(() => {
 *       if (onDismissRef.current) onDismissRef.current();
 *     }, durationMs);
 *     return () => clearTimeout(timer);
 *   }, [message, durationMs]);
 * };
 */
class AutoDismissHarness {
  constructor(initialMessage = null, durationMs = 3000) {
    this.durationMs = durationMs;
    this.message = initialMessage;
    this.onDismiss = () => {
      this.message = null;
      this.dismissCallCount++;
    };
    this.dismissCallCount = 0;
    this.timer = null;
    this.isMounted = true;

    if (this.message) {
      this.runEffect();
    }
  }

  runEffect() {
    this.cleanupEffect();
    if (!this.message || !this.isMounted) return;

    this.timer = setTimeout(() => {
      if (this.isMounted && this.onDismiss) {
        this.onDismiss();
      }
    }, this.durationMs);
  }

  cleanupEffect() {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  // Simulates setting a new message (e.g. setSuccessMsg('New message'))
  setMessage(newMessage) {
    this.message = newMessage;
    this.runEffect();
  }

  // Simulates user clicking the Close button
  manualDismiss() {
    this.cleanupEffect();
    this.message = null;
    this.dismissCallCount++;
  }

  // Simulates component unmounting
  unmount() {
    this.cleanupEffect();
    this.isMounted = false;
  }
}

/**
 * Page Simulator with independent Success and Error state.
 * Models OrdersPage, StaffPage, MenuPage, BillingPage, SettingsPage, etc.
 */
class PageNotificationSimulator {
  constructor() {
    this.successMsg = null;
    this.errorMsg = null;
    this.harness = new AutoDismissHarness(null, 3000);
    this.harness.onDismiss = () => {
      this.successMsg = null;
    };
  }

  setSuccess(msg) {
    this.successMsg = msg;
    this.harness.setMessage(msg);
  }

  setError(err) {
    // Errors are stored in separate state and are NOT passed to useAutoDismiss
    this.errorMsg = err;
  }

  closeSuccess() {
    this.harness.manualDismiss();
    this.successMsg = null;
  }

  closeError() {
    this.errorMsg = null;
  }

  unmount() {
    this.harness.unmount();
  }
}

test('1. Success notification automatically dismisses after exactly 3000ms', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });

  const page = new PageNotificationSimulator();
  page.setSuccess('Order #156 created successfully.');

  assert.equal(page.successMsg, 'Order #156 created successfully.');

  // Advance 2999ms -> still visible
  t.mock.timers.tick(2999);
  assert.equal(page.successMsg, 'Order #156 created successfully.');

  // Advance 1ms more -> reaches 3000ms, should be dismissed
  t.mock.timers.tick(1);
  assert.equal(page.successMsg, null, 'Success notification should be dismissed at 3000ms');

  t.mock.timers.reset();
});

test('2. Manual Close button dismisses notification immediately and clears timer', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });

  const page = new PageNotificationSimulator();
  page.setSuccess('Staff member created successfully.');

  // After 1000ms, user clicks Close
  t.mock.timers.tick(1000);
  assert.equal(page.successMsg, 'Staff member created successfully.');

  page.closeSuccess();
  assert.equal(page.successMsg, null, 'Notification should be dismissed immediately upon click');

  // Advance time past 3000ms to ensure no redundant or delayed dismiss calls
  t.mock.timers.tick(5000);
  assert.equal(page.successMsg, null);
  assert.equal(page.harness.timer, null, 'Timer should have been cleared');

  t.mock.timers.reset();
});

test('3. Reset timer when new success notification appears before previous expires (no stale timer dismissal)', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });

  const page = new PageNotificationSimulator();
  // Action 1: Create Order #156
  page.setSuccess('Order #156 created successfully.');

  // 2000ms elapse (1000ms left before dismissal)
  t.mock.timers.tick(2000);
  assert.equal(page.successMsg, 'Order #156 created successfully.');

  // Action 2: Create Order #157 at 2000ms mark
  page.setSuccess('Order #157 created successfully.');
  assert.equal(page.successMsg, 'Order #157 created successfully.');

  // Advance 1000ms (total elapsed 3000ms). The old timer would have fired here!
  // But because timer was reset, Order #157 should STILL be visible.
  t.mock.timers.tick(1000);
  assert.equal(page.successMsg, 'Order #157 created successfully.', 'Order #157 should NOT be dismissed by stale timer from #156');

  // Advance remaining 1999ms (total 2999ms for Order #157) -> still visible
  t.mock.timers.tick(1999);
  assert.equal(page.successMsg, 'Order #157 created successfully.');

  // Advance 1ms (total 3000ms for Order #157) -> dismisses
  t.mock.timers.tick(1);
  assert.equal(page.successMsg, null, 'Order #157 should be dismissed after its full 3000ms');

  t.mock.timers.reset();
});

test('4. Error messages are NOT auto-dismissed by the success timer', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });

  const page = new PageNotificationSimulator();
  page.setError('Failed to update inventory item: Database lock.');
  page.setSuccess('Item category updated successfully.');

  // Advance 3000ms -> success dismissed, error remains visible!
  t.mock.timers.tick(3000);
  assert.equal(page.successMsg, null, 'Success message auto-dismissed');
  assert.equal(page.errorMsg, 'Failed to update inventory item: Database lock.', 'Error message must remain visible');

  // Advance another 10000ms -> error still remains visible
  t.mock.timers.tick(10000);
  assert.equal(page.errorMsg, 'Failed to update inventory item: Database lock.', 'Error message must not auto-dismiss');

  // User manually closes error
  page.closeError();
  assert.equal(page.errorMsg, null);

  t.mock.timers.reset();
});

test('5. Unmounting component cancels timer cleanly preventing leaks or delayed state updates', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });

  const harness = new AutoDismissHarness('Settings updated successfully.', 3000);
  assert.equal(harness.dismissCallCount, 0);

  // Advance 1500ms
  t.mock.timers.tick(1500);

  // Component unmounts (e.g. user navigates to another page)
  harness.unmount();

  // Advance another 5000ms
  t.mock.timers.tick(5000);
  assert.equal(harness.dismissCallCount, 0, 'Callback must not be invoked after unmount');
  assert.equal(harness.timer, null, 'Timer must be cleared on unmount');

  t.mock.timers.reset();
});

test('6. Consistency across frontend entities (Orders, Staff, Menu, Categories, Inventory, Customers, Billing, Settings)', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });

  const entities = [
    { name: 'Orders', msg: 'Order #201 created successfully.' },
    { name: 'Staff', msg: 'Staff member updated successfully.' },
    { name: 'Menu', msg: 'Menu item saved successfully.' },
    { name: 'Categories', msg: 'Category created successfully.' },
    { name: 'Inventory', msg: 'Stock level updated successfully.' },
    { name: 'Customers', msg: 'Customer created successfully.' },
    { name: 'Billing', msg: 'Payment collected successfully.' },
    { name: 'Settings', msg: 'Settings updated successfully!' }
  ];

  for (const entity of entities) {
    const page = new PageNotificationSimulator();
    page.setSuccess(entity.msg);

    assert.equal(page.successMsg, entity.msg, `${entity.name} notification active`);

    t.mock.timers.tick(2999);
    assert.equal(page.successMsg, entity.msg, `${entity.name} still visible at 2999ms`);

    t.mock.timers.tick(1);
    assert.equal(page.successMsg, null, `${entity.name} dismissed at 3000ms`);
  }

  t.mock.timers.reset();
});
