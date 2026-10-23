import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

/**
 * Order Service
 */
export const orderService = {
  /**
   * Fetch all orders for current restaurant tenant
   * @param {string} [status] - Optional status filter (NEW, ACCEPTED, PREPARING, READY, COMPLETED, CANCELLED)
   * @param {string} [orderType] - Optional orderType filter (DINE_IN, TAKEAWAY)
   * @returns {Promise<Array>} List of OrderResponse
   */
  async getOrders(status = null, orderType = null) {
    const params = {};
    if (status && status !== 'ALL') {
      params.status = status;
    }
    if (orderType && orderType !== 'ALL') {
      params.orderType = orderType;
    }
    const response = await api.get(API_ENDPOINTS.ORDERS, { params });
    return response.data;
  },

  /**
   * Fetch a single order by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} OrderResponse
   */
  async getOrderById(id) {
    const response = await api.get(`${API_ENDPOINTS.ORDERS}/${id}`);
    return response.data;
  },

  /**
   * Create a new order
   * @param {Object} orderData - { orderType, tableId, notes, items: [{ menuItemId, quantity, notes }] }
   * @returns {Promise<Object>} OrderResponse
   */
  async createOrder(orderData) {
    const response = await api.post(API_ENDPOINTS.ORDERS, orderData);
    return response.data;
  },

  /**
   * Update an existing order (Allowed only when status === 'NEW')
   * @param {number|string} id 
   * @param {Object} orderData - { orderType, tableId, notes, items: [{ menuItemId, quantity, notes }] }
   * @returns {Promise<Object>} OrderResponse
   */
  async updateOrder(id, orderData) {
    const response = await api.put(`${API_ENDPOINTS.ORDERS}/${id}`, orderData);
    return response.data;
  },

  /**
   * Cancel an order (Admin allowed when status === 'NEW' or 'ACCEPTED')
   * @param {number|string} id 
   * @returns {Promise<Object>} OrderResponse
   */
  async cancelOrder(id) {
    const response = await api.patch(`${API_ENDPOINTS.ORDERS}/${id}/cancel`);
    return response.data;
  },

  /**
   * Add items to an existing order
   * @param {number|string} id - Order ID
   * @param {Object} itemsData - { orderType, items: [{ menuItemId, quantity, notes }], notes?: string }
   * @returns {Promise<Object>} OrderResponse
   */
  async addItemsToOrder(id, itemsData) {
    const response = await api.post(`${API_ENDPOINTS.ORDERS}/${id}/items`, itemsData);
    return response.data;
  },

  /**
   * Fetch active order for a table
   * @param {number|string} tableId 
   * @returns {Promise<Object>} OrderResponse
   */
  async getActiveOrderByTable(tableId) {
    const response = await api.get(`${API_ENDPOINTS.ORDERS}/table/${tableId}/active`);
    return response.data;
  },
};

export default orderService;
