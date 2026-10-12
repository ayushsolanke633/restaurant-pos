import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

/**
 * Menu Item Service
 */
export const menuItemService = {
  /**
   * Fetch menu items, optionally filtered by categoryId
   * @param {number|string} [categoryId] 
   * @returns {Promise<Array>} List of MenuItemResponse
   */
  async getMenuItems(categoryId = null) {
    const params = {};
    if (categoryId) {
      params.categoryId = categoryId;
    }
    const response = await api.get(API_ENDPOINTS.MENU_ITEMS, { params });
    return response.data;
  },

  /**
   * Fetch a single menu item by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} MenuItemResponse
   */
  async getMenuItemById(id) {
    const response = await api.get(`${API_ENDPOINTS.MENU_ITEMS}/${id}`);
    return response.data;
  },

  /**
   * Create a new menu item
   * @param {Object} menuItemData - { categoryId, name, description, price, isAvailable }
   * @returns {Promise<Object>} MenuItemResponse
   */
  async createMenuItem(menuItemData) {
    const response = await api.post(API_ENDPOINTS.MENU_ITEMS, menuItemData);
    return response.data;
  },

  /**
   * Update an existing menu item
   * @param {number|string} id 
   * @param {Object} menuItemData - { categoryId, name, description, price, isAvailable }
   * @returns {Promise<Object>} MenuItemResponse
   */
  async updateMenuItem(id, menuItemData) {
    const response = await api.put(`${API_ENDPOINTS.MENU_ITEMS}/${id}`, menuItemData);
    return response.data;
  },

  /**
   * Mark menu item as available
   * @param {number|string} id 
   * @returns {Promise<Object>} MenuItemResponse
   */
  async setAvailable(id) {
    const response = await api.patch(`${API_ENDPOINTS.MENU_ITEMS}/${id}/available`);
    return response.data;
  },

  /**
   * Mark menu item as unavailable
   * @param {number|string} id 
   * @returns {Promise<Object>} MenuItemResponse
   */
  async setUnavailable(id) {
    const response = await api.patch(`${API_ENDPOINTS.MENU_ITEMS}/${id}/unavailable`);
    return response.data;
  },
};

export default menuItemService;
