import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

/**
 * Billing & Payment Service
 * Interacts with backend BillController endpoints (/api/bills)
 */
export const billingService = {
  /**
   * Create a new bill for an order
   * @param {number|string} orderId 
   * @returns {Promise<Object>} BillResponse
   */
  async createBill(orderId) {
    const response = await api.post(API_ENDPOINTS.BILLS, { orderId: Number(orderId) });
    return response.data;
  },

  /**
   * Fetch bill by Order ID
   * @param {number|string} orderId 
   * @returns {Promise<Object>} BillResponse
   */
  async getBillByOrderId(orderId) {
    const response = await api.get(`${API_ENDPOINTS.BILLS}/order/${orderId}`);
    return response.data;
  },

  /**
   * Fetch all bills for current restaurant
   * @returns {Promise<Array>} List of BillResponse
   */
  async getAllBills() {
    const response = await api.get(API_ENDPOINTS.BILLS);
    return response.data;
  },

  /**
   * Fetch bill by Bill ID
   * @param {number|string} id 
   * @returns {Promise<Object>} BillResponse
   */
  async getBillById(id) {
    const response = await api.get(`${API_ENDPOINTS.BILLS}/${id}`);
    return response.data;
  },

  /**
   * Process payment for a bill
   * @param {number|string} billId 
   * @param {Object} paymentData - { amount, paymentMethod, transactionReference }
   * @returns {Promise<Object>} PaymentResponse
   */
  async processPayment(billId, paymentData) {
    const response = await api.post(`${API_ENDPOINTS.BILLS}/${billId}/payments`, paymentData);
    return response.data;
  },

  /**
   * Fetch payments for a bill
   * @param {number|string} billId 
   * @returns {Promise<Array>} List of PaymentResponse
   */
  async getPaymentsByBillId(billId) {
    const response = await api.get(`${API_ENDPOINTS.BILLS}/${billId}/payments`);
    return response.data;
  },
};

export default billingService;
