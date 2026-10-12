import api from './api';
import { API_ENDPOINTS, STORAGE_KEYS } from '../utils/constants';

/**
 * Authentication Service
 */
export const authService = {
  /**
   * Send login request to backend
   * @param {string} email 
   * @param {string} password 
   * @returns {Promise<Object>} LoginResponse from Spring Boot backend
   */
  async login(email, password) {
    const response = await api.post(API_ENDPOINTS.LOGIN, { email, password });
    return response.data;
  },

  /**
   * Persist session in sessionStorage (isolated per window/tab context)
   * @param {Object} data - LoginResponse object from backend
   */
  saveSession(data) {
    if (!data || !data.token) return;

    const token = data.token;
    const user = {
      userId: data.userId,
      restaurantId: data.restaurantId,
      role: data.role,
      name: data.name,
      email: data.email,
      expiresIn: data.expiresIn,
    };

    sessionStorage.setItem(STORAGE_KEYS.TOKEN, token);
    sessionStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));

    // Clear legacy shared localStorage keys if present
    localStorage.removeItem(STORAGE_KEYS.TOKEN);
    localStorage.removeItem(STORAGE_KEYS.USER);
  },

  /**
   * Clear session from sessionStorage and legacy localStorage
   */
  clearSession() {
    sessionStorage.removeItem(STORAGE_KEYS.TOKEN);
    sessionStorage.removeItem(STORAGE_KEYS.USER);
    localStorage.removeItem(STORAGE_KEYS.TOKEN);
    localStorage.removeItem(STORAGE_KEYS.USER);
  },

  /**
   * Retrieve cached user object from sessionStorage
   * @returns {Object|null}
   */
  getCurrentUser() {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEYS.USER);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  },

  /**
   * Retrieve cached JWT token from sessionStorage
   * @returns {string|null}
   */
  getToken() {
    return sessionStorage.getItem(STORAGE_KEYS.TOKEN);
  },

  /**
   * Retrieve current user role
   * @returns {string|null}
   */
  getRole() {
    const user = this.getCurrentUser();
    return user ? user.role : null;
  },

  /**
   * Check if client token is valid and unexpired
   * @returns {boolean}
   */
  isAuthenticated() {
    const token = this.getToken();
    const user = this.getCurrentUser();

    if (!token || !user) {
      return false;
    }

    if (this.isTokenExpired(token)) {
      this.clearSession();
      return false;
    }

    return true;
  },

  /**
   * Decode JWT payload to check client-side exp timestamp
   * @param {string} token 
   * @returns {boolean} True if expired
   */
  isTokenExpired(token) {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return false;
      
      const payloadBase64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(atob(payloadBase64));
      
      if (payload.exp) {
        const currentTime = Math.floor(Date.now() / 1000);
        return payload.exp < currentTime;
      }
      return false;
    } catch {
      return false;
    }
  },
};

export default authService;
