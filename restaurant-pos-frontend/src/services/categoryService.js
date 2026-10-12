import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

/**
 * Category Service
 */
export const categoryService = {
  /**
   * Fetch all categories for the current restaurant
   * @returns {Promise<Array>} List of CategoryResponse
   */
  async getCategories() {
    const response = await api.get(API_ENDPOINTS.CATEGORIES);
    return response.data;
  },

  /**
   * Fetch a single category by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} CategoryResponse
   */
  async getCategoryById(id) {
    const response = await api.get(`${API_ENDPOINTS.CATEGORIES}/${id}`);
    return response.data;
  },

  /**
   * Create a new category
   * @param {Object} categoryData - { name, description, isActive }
   * @returns {Promise<Object>} CategoryResponse
   */
  async createCategory(categoryData) {
    const response = await api.post(API_ENDPOINTS.CATEGORIES, categoryData);
    return response.data;
  },

  /**
   * Update an existing category
   * @param {number|string} id 
   * @param {Object} categoryData - { name, description, isActive }
   * @returns {Promise<Object>} CategoryResponse
   */
  async updateCategory(id, categoryData) {
    const response = await api.put(`${API_ENDPOINTS.CATEGORIES}/${id}`, categoryData);
    return response.data;
  },

  /**
   * Activate category by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} CategoryResponse
   */
  async activateCategory(id) {
    const response = await api.patch(`${API_ENDPOINTS.CATEGORIES}/${id}/activate`);
    return response.data;
  },

  /**
   * Deactivate category by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} CategoryResponse
   */
  async deactivateCategory(id) {
    const response = await api.patch(`${API_ENDPOINTS.CATEGORIES}/${id}/deactivate`);
    return response.data;
  },
};

export default categoryService;
