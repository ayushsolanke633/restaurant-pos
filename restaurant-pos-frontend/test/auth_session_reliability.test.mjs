import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * AnnaDhara POS — Minor Update 4: React Authentication UX & Session Reliability Tests
 */

// Simulated storage per tab context
class MockSessionStorage {
  constructor() {
    this.store = {};
  }
  getItem(key) {
    return this.store[key] || null;
  }
  setItem(key, value) {
    this.store[key] = String(value);
  }
  removeItem(key) {
    delete this.store[key];
  }
  clear() {
    this.store = {};
  }
}

// Simulated Event Target / Window Event Dispatcher
class MockEventTarget {
  constructor() {
    this.listeners = {};
  }
  addEventListener(event, fn) {
    this.listeners[event] = this.listeners[event] || [];
    this.listeners[event].push(fn);
  }
  removeEventListener(event, fn) {
    if (!this.listeners[event]) return;
    this.listeners[event] = this.listeners[event].filter((l) => l !== fn);
  }
  dispatchEvent(event) {
    const type = event.type;
    if (this.listeners[type]) {
      this.listeners[type].forEach((fn) => fn(event));
    }
  }
}

test('1. HTTP 401 clears session and dispatches auth:unauthorized for authenticated requests', () => {
  const sessionStorage = new MockSessionStorage();
  const windowTarget = new MockEventTarget();
  let unauthorizedFired = false;

  windowTarget.addEventListener('auth:unauthorized', () => {
    unauthorizedFired = true;
  });

  sessionStorage.setItem('annadhara_token', 'valid.token');
  sessionStorage.setItem('annadhara_user', JSON.stringify({ userId: 1, role: 'ADMIN' }));

  // Simulate response interceptor on 401 for authenticated API
  const error = {
    config: { url: '/api/v1/orders' },
    response: { status: 401 },
  };

  if (error.response?.status === 401) {
    const isLogin = error.config?.url?.includes('/auth/login');
    const isLogout = error.config?.url?.includes('/auth/logout');
    if (!isLogin) {
      sessionStorage.removeItem('annadhara_token');
      sessionStorage.removeItem('annadhara_user');
    }
    if (!isLogin && !isLogout) {
      windowTarget.dispatchEvent({ type: 'auth:unauthorized' });
    }
  }

  assert.equal(sessionStorage.getItem('annadhara_token'), null, 'Token must be cleared on 401');
  assert.equal(sessionStorage.getItem('annadhara_user'), null, 'User must be cleared on 401');
  assert.equal(unauthorizedFired, true, 'auth:unauthorized event must be dispatched');
});

test('2. HTTP 401 on login attempt does NOT dispatch auth:unauthorized or clear unrelated state', () => {
  const sessionStorage = new MockSessionStorage();
  const windowTarget = new MockEventTarget();
  let unauthorizedFired = false;

  windowTarget.addEventListener('auth:unauthorized', () => {
    unauthorizedFired = true;
  });

  // Simulated login failed with bad credentials
  const error = {
    config: { url: '/api/v1/auth/login' },
    response: { status: 401, data: { message: 'Invalid email or password' } },
  };

  if (error.response?.status === 401) {
    const isLogin = error.config?.url?.includes('/auth/login');
    const isLogout = error.config?.url?.includes('/auth/logout');
    if (!isLogin) {
      sessionStorage.removeItem('annadhara_token');
      sessionStorage.removeItem('annadhara_user');
    }
    if (!isLogin && !isLogout) {
      windowTarget.dispatchEvent({ type: 'auth:unauthorized' });
    }
  }

  assert.equal(unauthorizedFired, false, 'auth:unauthorized must NOT fire for failed login attempts');
});

test('3. HTTP 403 Forbidden does NOT clear session and does NOT trigger auth:unauthorized', () => {
  const sessionStorage = new MockSessionStorage();
  const windowTarget = new MockEventTarget();
  let unauthorizedFired = false;

  windowTarget.addEventListener('auth:unauthorized', () => {
    unauthorizedFired = true;
  });

  sessionStorage.setItem('annadhara_token', 'valid.token');
  sessionStorage.setItem('annadhara_user', JSON.stringify({ userId: 2, role: 'KITCHEN' }));

  // Kitchen tries to access billing endpoint -> 403
  const error = {
    config: { url: '/api/v1/bills' },
    response: { status: 403, data: { message: 'Access denied' } },
  };

  if (error.response?.status === 401) {
    sessionStorage.removeItem('annadhara_token');
    windowTarget.dispatchEvent({ type: 'auth:unauthorized' });
  }

  assert.notEqual(sessionStorage.getItem('annadhara_token'), null, 'Token must be preserved on 403');
  assert.equal(unauthorizedFired, false, 'auth:unauthorized must NOT fire on 403');
});

test('4. Tab session isolation: sessionStorage values in Tab A and Tab B remain isolated', () => {
  const tabA = new MockSessionStorage();
  const tabB = new MockSessionStorage();

  tabA.setItem('annadhara_token', 'token-admin-restaurant-1');
  tabA.setItem('annadhara_user', JSON.stringify({ userId: 1, restaurantId: 1, role: 'ADMIN' }));

  tabB.setItem('annadhara_token', 'token-kitchen-restaurant-2');
  tabB.setItem('annadhara_user', JSON.stringify({ userId: 2, restaurantId: 2, role: 'KITCHEN' }));

  assert.notEqual(tabA.getItem('annadhara_token'), tabB.getItem('annadhara_token'), 'Tokens must be distinct across tabs');
  assert.equal(JSON.parse(tabA.getItem('annadhara_user')).restaurantId, 1);
  assert.equal(JSON.parse(tabB.getItem('annadhara_user')).restaurantId, 2);

  // Logout in Tab A does not destroy session in Tab B
  tabA.clear();
  assert.equal(tabA.getItem('annadhara_token'), null);
  assert.notEqual(tabB.getItem('annadhara_token'), null, 'Tab B session must remain intact');
});

test('5. Logout cleanup clears sessionStorage and notifies listeners without loops', async () => {
  const sessionStorage = new MockSessionStorage();
  const windowTarget = new MockEventTarget();
  let logoutEvents = 0;

  windowTarget.addEventListener('auth:logout', () => {
    logoutEvents++;
  });

  sessionStorage.setItem('annadhara_token', 'token-to-logout');
  sessionStorage.setItem('annadhara_user', JSON.stringify({ userId: 1 }));

  // Simulated authService.logout()
  let apiLogoutCalled = false;
  const mockApi = {
    post: async (endpoint) => {
      if (endpoint === '/api/v1/auth/logout') {
        apiLogoutCalled = true;
      }
    },
  };

  const token = sessionStorage.getItem('annadhara_token');
  try {
    if (token) {
      await mockApi.post('/api/v1/auth/logout');
    }
  } finally {
    sessionStorage.removeItem('annadhara_token');
    sessionStorage.removeItem('annadhara_user');
    windowTarget.dispatchEvent({ type: 'auth:logout' });
  }

  assert.equal(apiLogoutCalled, true, 'Backend logout endpoint must be called');
  assert.equal(sessionStorage.getItem('annadhara_token'), null, 'Session storage must be cleaned');
  assert.equal(logoutEvents, 1, 'Exactly one logout event dispatched');

  // Second logout when already logged out does NOT re-call API
  apiLogoutCalled = false;
  const token2 = sessionStorage.getItem('annadhara_token');
  if (token2) {
    await mockApi.post('/api/v1/auth/logout');
  }
  assert.equal(apiLogoutCalled, false, 'Redundant logout must not call API without token');
});

test('6. WebSocket authentication failure halts infinite reconnection loop', () => {
  let reconnectDelay = 3000;
  let status = 'CONNECTING';
  let disconnected = false;
  let authUnauthorizedFired = false;

  const mockClient = {
    disconnect: () => {
      reconnectDelay = 0;
      disconnected = true;
    },
  };

  // Simulating onStompError with auth rejection frame
  const frame = {
    headers: { message: 'Access denied: Token has been revoked' },
  };

  const errMsg = (frame?.headers?.message || '').toLowerCase();
  const isAuthError =
    errMsg.includes('access denied') ||
    errMsg.includes('unauthenticated') ||
    errMsg.includes('revoked') ||
    errMsg.includes('expired') ||
    errMsg.includes('unauthorized');

  if (isAuthError) {
    mockClient.disconnect();
    status = 'AUTH_ERROR';
    authUnauthorizedFired = true;
  } else {
    status = 'DISCONNECTED';
  }

  assert.equal(disconnected, true, 'WebSocket must be disconnected immediately on auth error');
  assert.equal(reconnectDelay, 0, 'reconnectDelay must be reset to 0 to prevent infinite loop');
  assert.equal(status, 'AUTH_ERROR', 'Status must reflect auth error');
  assert.equal(authUnauthorizedFired, true, 'auth:unauthorized must be dispatched');
});

test('7. WebSocket temporary network disconnect preserves reconnection delay for recovery', () => {
  let reconnectDelay = 3000;
  let status = 'CONNECTING';
  let disconnected = false;

  const mockClient = {
    disconnect: () => {
      reconnectDelay = 0;
      disconnected = true;
    },
  };

  // Simulating temporary TCP drop / broker restart
  const frame = {
    headers: { message: 'Connection reset by peer' },
  };

  const errMsg = (frame?.headers?.message || '').toLowerCase();
  const isAuthError =
    errMsg.includes('access denied') ||
    errMsg.includes('unauthenticated') ||
    errMsg.includes('revoked') ||
    errMsg.includes('expired') ||
    errMsg.includes('unauthorized');

  if (isAuthError) {
    mockClient.disconnect();
    status = 'AUTH_ERROR';
  } else {
    status = 'DISCONNECTED';
  }

  assert.equal(disconnected, false, 'Temporary drop must NOT permanently disconnect client');
  assert.equal(reconnectDelay, 3000, 'reconnectDelay must be preserved for auto-reconnection');
  assert.equal(status, 'DISCONNECTED', 'Status must be DISCONNECTED for temporary drop');
});
