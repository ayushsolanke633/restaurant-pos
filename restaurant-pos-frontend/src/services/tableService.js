import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

/**
 * Restaurant Table Service
 */
export const tableService = {
  /**
   * Fetch all tables for current restaurant tenant
   * @returns {Promise<Array>} List of TableResponse
   */
  async getTables() {
    const response = await api.get(API_ENDPOINTS.TABLES);
    return response.data;
  },

  /**
   * Fetch a single table by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} TableResponse
   */
  async getTableById(id) {
    const response = await api.get(`${API_ENDPOINTS.TABLES}/${id}`);
    return response.data;
  },

  /**
   * Create a new table
   * @param {Object} tableData - { tableNumber: number, capacity: number }
   * @returns {Promise<Object>} TableResponse
   */
  async createTable(tableData) {
    const response = await api.post(API_ENDPOINTS.TABLES, tableData);
    return response.data;
  },

  /**
   * Update an existing table
   * @param {number|string} id 
   * @param {Object} tableData - { tableNumber: number, capacity: number }
   * @returns {Promise<Object>} TableResponse
   */
  async updateTable(id, tableData) {
    const response = await api.put(`${API_ENDPOINTS.TABLES}/${id}`, tableData);
    return response.data;
  },
};

export default tableService;
