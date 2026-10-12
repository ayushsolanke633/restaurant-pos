// Application Constants

const RAW_API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const RAW_WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL;
const IS_PROD = import.meta.env.PROD;

function resolveApiBaseUrl() {
  const url = (RAW_API_BASE_URL || '').trim() || (IS_PROD ? '' : 'http://localhost:8080');

  if (IS_PROD) {
    if (!url) {
      throw new Error(
        '[Production Configuration Error] VITE_API_BASE_URL is required in production builds but was not configured.'
      );
    }
    const disallowedPatterns = [
      '10.10.13.114',
      '192.168.74.55',
      '192.168.',
      'localhost',
      '127.0.0.1',
    ];
    for (const pattern of disallowedPatterns) {
      if (url.includes(pattern)) {
        throw new Error(
          `[Production Configuration Error] Production VITE_API_BASE_URL cannot use staging/development address: "${pattern}". Current value: "${url}".`
        );
      }
    }
    if (!url.startsWith('https://')) {
      throw new Error(
        `[Production Configuration Error] Production VITE_API_BASE_URL must use https://. Current value: "${url}".`
      );
    }
  }

  return url.endsWith('/') ? url.slice(0, -1) : url;
}

export const API_BASE_URL = resolveApiBaseUrl();

function resolveWsBaseUrl() {
  let wsUrl = (RAW_WS_BASE_URL || '').trim();
  if (!wsUrl) {
    wsUrl = `${API_BASE_URL}/ws`;
  }
  if (IS_PROD) {
    const disallowedPatterns = [
      '10.10.13.114',
      '192.168.74.55',
      '192.168.',
      'localhost',
      '127.0.0.1',
    ];
    for (const pattern of disallowedPatterns) {
      if (wsUrl.includes(pattern)) {
        throw new Error(
          `[Production Configuration Error] Production WebSocket URL cannot use staging/development address: "${pattern}". Current value: "${wsUrl}".`
        );
      }
    }
    if (!wsUrl.startsWith('https://') && !wsUrl.startsWith('wss://')) {
      throw new Error(
        `[Production Configuration Error] Production WebSocket URL must use https:// or wss://. Current value: "${wsUrl}".`
      );
    }
  }
  return wsUrl.endsWith('/') ? wsUrl.slice(0, -1) : wsUrl;
}

export const WS_BASE_URL = resolveWsBaseUrl();

export const ROLES = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  ADMIN: 'ADMIN',
  KITCHEN: 'KITCHEN',
  WAITER: 'WAITER',
};

export const SUPPORTED_ROLES = [ROLES.SUPER_ADMIN, ROLES.ADMIN, ROLES.KITCHEN];

export const STORAGE_KEYS = {
  TOKEN: 'restaurant_pos_token',
  USER: 'restaurant_pos_user',
};

export const API_ENDPOINTS = {
  LOGIN: '/api/auth/login',
  CATEGORIES: '/api/categories',
  MENU_ITEMS: '/api/menu-items',
  TABLES: '/api/tables',
  ORDERS: '/api/orders',
  BILLS: '/api/bills',
  USERS: '/api/users',
  INVENTORY_ITEMS: '/api/inventory-items',
  INVENTORY_TRANSACTIONS: '/api/inventory-transactions',
  CUSTOMERS: '/api/customers',
  REPORTS: '/api/reports',
  SETTINGS: '/api/settings',
  PLATFORM_RESTAURANTS: '/api/platform/restaurants',
};

export const CURRENCY_SYMBOL = '₹';
