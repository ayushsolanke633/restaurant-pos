import axios from 'axios';
import { API_BASE_URL, API_ENDPOINTS, STORAGE_KEYS } from '../utils/constants';

// Centralized Axios Instance
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

// Request Interceptor: Attach JWT Token if present
api.interceptors.request.use(
  (config) => {
    const token = sessionStorage.getItem(STORAGE_KEYS.TOKEN);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: Global Error Handler
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      const requestUrl = error.config?.url || '';
      const isLoginRequest = requestUrl.includes(API_ENDPOINTS.LOGIN);
      const isLogoutRequest = requestUrl.includes(API_ENDPOINTS.LOGOUT);

      // Always clear session tokens on 401 (unless it's an initial login attempt)
      if (!isLoginRequest) {
        sessionStorage.removeItem(STORAGE_KEYS.TOKEN);
        sessionStorage.removeItem(STORAGE_KEYS.USER);
        localStorage.removeItem(STORAGE_KEYS.TOKEN);
        localStorage.removeItem(STORAGE_KEYS.USER);
      }

      // Dispatch auth:unauthorized only for authenticated session failures (not login or logout attempts)
      if (!isLoginRequest && !isLogoutRequest) {
        window.dispatchEvent(new Event('auth:unauthorized'));
      }
    }
    // Note: 403 Forbidden is NOT treated as expired session; pass error through
    return Promise.reject(error);
  }
);

export default api;
