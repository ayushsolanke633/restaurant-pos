import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Regression Test Suite:
 * False or Persistent "Unable to Connect to the Server" Error in Tables Page
 */

// Simulation of fetchTablesAndOrders error classification and execution logic
function createTablesPageManager({ mockTableService, mockOrderService }) {
  let state = {
    tables: [],
    activeOrdersByTable: {},
    loading: false,
    error: '',
    wsStatus: 'DISCONNECTED',
  };

  const listeners = [];
  function setState(updater) {
    state = typeof updater === 'function' ? updater(state) : { ...state, ...updater };
    listeners.forEach((l) => l(state));
  }

  const setError = (err) => setState((s) => ({ ...s, error: err }));
  const setLoading = (loading) => setState((s) => ({ ...s, loading }));
  const setTables = (tables) => setState((s) => ({ ...s, tables }));
  const setActiveOrdersByTable = (orders) => setState((s) => ({ ...s, activeOrdersByTable: orders }));

  async function fetchTablesAndOrders(showLoading = true) {
    if (showLoading) setLoading(true);
    setError('');

    try {
      const [tablesData, dineInOrders] = await Promise.all([
        mockTableService.getTables(),
        mockOrderService.getOrders(null, 'DINE_IN').catch(() => []),
      ]);

      const tableList = Array.isArray(tablesData) ? tablesData : [];
      const sortedTables = [...tableList].sort((a, b) => Number(a.tableNumber) - Number(b.tableNumber));
      setTables(sortedTables);

      // Group active orders (NEW, ACCEPTED, PREPARING, READY) by tableId
      const ordersByTable = {};
      const activeStatuses = ['NEW', 'ACCEPTED', 'PREPARING', 'READY'];

      if (Array.isArray(dineInOrders)) {
        dineInOrders.forEach((ord) => {
          if (ord.tableId && activeStatuses.includes((ord.status || '').toUpperCase())) {
            if (!ordersByTable[ord.tableId]) {
              ordersByTable[ord.tableId] = [];
            }
            ordersByTable[ord.tableId].push(ord);
          }
        });

        // Sort descending by createdAt
        Object.keys(ordersByTable).forEach((tId) => {
          ordersByTable[tId].sort(
            (a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
          );
        });
      }

      setActiveOrdersByTable(ordersByTable);
    } catch (err) {
      if (err?.response) {
        if (err.response.status === 403) {
          setError('Access Denied: You do not have permission to view restaurant tables.');
        } else if (err.response.status === 401) {
          setError('Session expired. Please log in again.');
        } else if (err.response.status >= 500) {
          setError('Server error while loading tables. Please try again later.');
        } else {
          setError(err.response.data?.message || 'Failed to load restaurant tables.');
        }
      } else if (err?.code === 'ERR_NETWORK' || err?.request) {
        setError('Unable to connect to the server. Please check your network connection.');
      } else {
        setError(err?.message || 'An unexpected error occurred while loading tables.');
      }
    } finally {
      if (showLoading) setLoading(false);
    }
  }

  function handleWsStatusChange(newStatus) {
    // WebSocket reconnection status updates must not set or overwrite page errors
    setState((s) => ({ ...s, wsStatus: newStatus }));
  }

  return {
    getState: () => state,
    fetchTablesAndOrders,
    handleWsStatusChange,
    setError,
  };
}

test('1. Tables load successfully without a false error banner', async () => {
  const manager = createTablesPageManager({
    mockTableService: {
      getTables: async () => [
        { id: 1, tableNumber: '1', capacity: 4, status: 'AVAILABLE' },
        { id: 2, tableNumber: '2', capacity: 2, status: 'OCCUPIED' },
      ],
    },
    mockOrderService: {
      getOrders: async () => [
        { id: 101, tableId: 2, status: 'NEW', createdAt: '2026-10-15T12:00:00Z', items: [] },
      ],
    },
  });

  await manager.fetchTablesAndOrders(true);
  const state = manager.getState();

  assert.equal(state.loading, false);
  assert.equal(state.error, '', 'Error banner should be empty on successful table load');
  assert.equal(state.tables.length, 2);
  assert.ok(state.activeOrdersByTable[2]);
  assert.equal(state.activeOrdersByTable[2][0].id, 101);
});

test('2. A genuine network failure produces accurate "Unable to connect to the server" message', async () => {
  const networkError = new Error('Network Error');
  networkError.code = 'ERR_NETWORK';
  networkError.request = {}; // request was sent but no response

  const manager = createTablesPageManager({
    mockTableService: {
      getTables: async () => {
        throw networkError;
      },
    },
    mockOrderService: {
      getOrders: async () => [],
    },
  });

  await manager.fetchTablesAndOrders(true);
  const state = manager.getState();

  assert.equal(state.loading, false);
  assert.equal(
    state.error,
    'Unable to connect to the server. Please check your network connection.',
    'Should correctly identify real network failures'
  );
});

test('3. Server 500 error produces server error message, not a network failure', async () => {
  const serverError = new Error('Internal Server Error');
  serverError.response = {
    status: 500,
    data: { message: 'Database query timeout' },
  };

  const manager = createTablesPageManager({
    mockTableService: {
      getTables: async () => {
        throw serverError;
      },
    },
    mockOrderService: {
      getOrders: async () => [],
    },
  });

  await manager.fetchTablesAndOrders(true);
  const state = manager.getState();

  assert.equal(state.loading, false);
  assert.equal(
    state.error,
    'Server error while loading tables. Please try again later.',
    'Server errors must not be labeled as network disconnection'
  );
});

test('4. 403 Forbidden displays Access Denied permission message', async () => {
  const authError = new Error('Forbidden');
  authError.response = {
    status: 403,
    data: { message: 'Forbidden' },
  };

  const manager = createTablesPageManager({
    mockTableService: {
      getTables: async () => {
        throw authError;
      },
    },
    mockOrderService: {
      getOrders: async () => [],
    },
  });

  await manager.fetchTablesAndOrders(true);
  const state = manager.getState();

  assert.equal(state.loading, false);
  assert.equal(
    state.error,
    'Access Denied: You do not have permission to view restaurant tables.'
  );
});

test('5. 401 Unauthorized displays Session Expired message', async () => {
  const unauthError = new Error('Unauthorized');
  unauthError.response = {
    status: 401,
    data: { message: 'Token expired' },
  };

  const manager = createTablesPageManager({
    mockTableService: {
      getTables: async () => {
        throw unauthError;
      },
    },
    mockOrderService: {
      getOrders: async () => [],
    },
  });

  await manager.fetchTablesAndOrders(true);
  const state = manager.getState();

  assert.equal(state.loading, false);
  assert.equal(state.error, 'Session expired. Please log in again.');
});

test('6. Successful retries clear stale errors', async () => {
  let shouldFail = true;
  const manager = createTablesPageManager({
    mockTableService: {
      getTables: async () => {
        if (shouldFail) {
          const err = new Error('Network failure');
          err.code = 'ERR_NETWORK';
          throw err;
        }
        return [{ id: 1, tableNumber: '1', capacity: 4, status: 'AVAILABLE' }];
      },
    },
    mockOrderService: {
      getOrders: async () => [],
    },
  });

  // Attempt 1 fails
  await manager.fetchTablesAndOrders(true);
  assert.equal(manager.getState().error, 'Unable to connect to the server. Please check your network connection.');

  // Attempt 2 succeeds (e.g. user retries or reconnects)
  shouldFail = false;
  await manager.fetchTablesAndOrders(false);

  assert.equal(manager.getState().error, '', 'Stale error banner must be cleared after successful retry');
  assert.equal(manager.getState().tables.length, 1);
});

test('7. Optional background order retrieval failure does not falsely claim the server is offline', async () => {
  const manager = createTablesPageManager({
    mockTableService: {
      getTables: async () => [
        { id: 1, tableNumber: '1', capacity: 4, status: 'AVAILABLE' },
      ],
    },
    mockOrderService: {
      getOrders: async () => {
        // Simulating orders API returning an error while tables succeed
        throw new Error('Orders query temporarily unavailable');
      },
    },
  });

  await manager.fetchTablesAndOrders(true);
  const state = manager.getState();

  assert.equal(state.error, '', 'Background order fetch failure must not trigger a false server disconnect error');
  assert.equal(state.tables.length, 1, 'Tables should still load and render');
});

test('8. WebSocket reconnection events do not flood or set page error banners', () => {
  const manager = createTablesPageManager({
    mockTableService: { getTables: async () => [] },
    mockOrderService: { getOrders: async () => [] },
  });

  manager.handleWsStatusChange('CONNECTING');
  assert.equal(manager.getState().wsStatus, 'CONNECTING');
  assert.equal(manager.getState().error, '');

  manager.handleWsStatusChange('DISCONNECTED');
  assert.equal(manager.getState().wsStatus, 'DISCONNECTED');
  assert.equal(manager.getState().error, '', 'WebSocket disconnect status must not set error banner');

  manager.handleWsStatusChange('CONNECTED');
  assert.equal(manager.getState().wsStatus, 'CONNECTED');
  assert.equal(manager.getState().error, '');
});
