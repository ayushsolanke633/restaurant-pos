import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

const KITCHEN_ENDPOINT = '/api/kitchen/orders';

/**
 * Kitchen Service
 * Interacts with backend KitchenController endpoints (/api/kitchen/orders)
 */
export const kitchenService = {
  /**
   * Fetch active kitchen orders
   * @param {string} [status] - Optional status filter (NEW, ACCEPTED, PREPARING, READY)
   * @returns {Promise<Array>} List of KitchenOrderResponse
   */
  async getKitchenOrders(status = null) {
    const params = {};
    if (status && status !== 'ALL') {
      params.status = status;
    }
    const response = await api.get(KITCHEN_ENDPOINT, { params });
    return response.data;
  },

  /**
   * Fetch a single kitchen order by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} KitchenOrderResponse
   */
  async getKitchenOrderById(id) {
    const response = await api.get(`${KITCHEN_ENDPOINT}/${id}`);
    return response.data;
  },

  /**
   * Accept a new order or specific batch (NEW -> ACCEPTED)
   * @param {number|string} id 
   * @param {number} [batchNumber]
   * @returns {Promise<Object>} KitchenOrderResponse
   */
  async acceptOrder(id, batchNumber = null) {
    const url = batchNumber != null ? `${KITCHEN_ENDPOINT}/${id}/batches/${batchNumber}/accept` : `${KITCHEN_ENDPOINT}/${id}/accept`;
    const response = await api.patch(url);
    return response.data;
  },

  /**
   * Start preparing an order or specific batch (ACCEPTED -> PREPARING)
   * @param {number|string} id 
   * @param {number} [batchNumber]
   * @returns {Promise<Object>} KitchenOrderResponse
   */
  async prepareOrder(id, batchNumber = null) {
    const url = batchNumber != null ? `${KITCHEN_ENDPOINT}/${id}/batches/${batchNumber}/preparing` : `${KITCHEN_ENDPOINT}/${id}/preparing`;
    const response = await api.patch(url);
    return response.data;
  },

  /**
   * Mark order or specific batch ready (PREPARING -> READY)
   * @param {number|string} id 
   * @param {number} [batchNumber]
   * @returns {Promise<Object>} KitchenOrderResponse
   */
  async readyOrder(id, batchNumber = null) {
    const url = batchNumber != null ? `${KITCHEN_ENDPOINT}/${id}/batches/${batchNumber}/ready` : `${KITCHEN_ENDPOINT}/${id}/ready`;
    const response = await api.patch(url);
    return response.data;
  },
};

export default kitchenService;
