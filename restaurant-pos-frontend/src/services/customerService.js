import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

/**
 * Customer Management Service
 * Interacts with Spring Boot backend CustomerController endpoints (/api/customers)
 */
export const customerService = {
  /**
   * Fetch all customers for current restaurant
   * @returns {Promise<Array>} List of CustomerResponse
   */
  async getAllCustomers() {
    const response = await api.get(API_ENDPOINTS.CUSTOMERS);
    return response.data;
  },

  /**
   * Fetch single customer by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} CustomerResponse
   */
  async getCustomerById(id) {
    const response = await api.get(`${API_ENDPOINTS.CUSTOMERS}/${id}`);
    return response.data;
  },

  /**
   * Create a new customer
   * @param {Object} customerData - { name, phone, email, address }
   * @returns {Promise<Object>} CustomerResponse
   */
  async createCustomer(customerData) {
    const response = await api.post(API_ENDPOINTS.CUSTOMERS, customerData);
    return response.data;
  },

  /**
   * Update an existing customer
   * @param {number|string} id 
   * @param {Object} customerData - { name, phone, email, address }
   * @returns {Promise<Object>} CustomerResponse
   */
  async updateCustomer(id, customerData) {
    const response = await api.put(`${API_ENDPOINTS.CUSTOMERS}/${id}`, customerData);
    return response.data;
  },

  /**
   * Search customers by name, phone, or email
   * @param {string} query 
   * @returns {Promise<Array>} List of CustomerResponse
   */
  async searchCustomers(query) {
    const response = await api.get(`${API_ENDPOINTS.CUSTOMERS}/search`, {
      params: { query },
    });
    return response.data;
  },
};

export default customerService;
