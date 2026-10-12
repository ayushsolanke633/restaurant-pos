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
   * Accept a new order (NEW -> ACCEPTED)
   * @param {number|string} id 
   * @returns {Promise<Object>} KitchenOrderResponse
   */
  async acceptOrder(id) {
    const response = await api.patch(`${KITCHEN_ENDPOINT}/${id}/accept`);
    return response.data;
  },

  /**
   * Start preparing an order (ACCEPTED -> PREPARING)
   * @param {number|string} id 
   * @returns {Promise<Object>} KitchenOrderResponse
   */
  async prepareOrder(id) {
    const response = await api.patch(`${KITCHEN_ENDPOINT}/${id}/preparing`);
    return response.data;
  },

  /**
   * Mark order ready (PREPARING -> READY)
   * @param {number|string} id 
   * @returns {Promise<Object>} KitchenOrderResponse
   */
  async readyOrder(id) {
    const response = await api.patch(`${KITCHEN_ENDPOINT}/${id}/ready`);
    return response.data;
  },
};

export default kitchenService;
