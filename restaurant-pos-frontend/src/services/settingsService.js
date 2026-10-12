import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

/**
 * Settings Service
 * Interacts with Spring Boot backend RestaurantSettingsController endpoints (/api/settings)
 */
export const settingsService = {
  /**
   * Fetch current restaurant settings for authenticated tenant
   * @returns {Promise<Object>} RestaurantSettingsResponse
   */
  async getSettings() {
    const response = await api.get(API_ENDPOINTS.SETTINGS);
    return response.data;
  },

  /**
   * Update restaurant settings for authenticated tenant
   * @param {Object} settingsData - RestaurantSettingsRequest DTO
   * @returns {Promise<Object>} RestaurantSettingsResponse
   */
  async updateSettings(settingsData) {
    const response = await api.put(API_ENDPOINTS.SETTINGS, settingsData);
    return response.data;
  },
};

export default settingsService;
