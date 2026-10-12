import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

const platformService = {
  getAllRestaurants: async () => {
    const response = await api.get(API_ENDPOINTS.PLATFORM_RESTAURANTS);
    return response.data;
  },

  getRestaurantById: async (id) => {
    const response = await api.get(`${API_ENDPOINTS.PLATFORM_RESTAURANTS}/${id}`);
    return response.data;
  },

  onboardRestaurant: async (data) => {
    const response = await api.post(API_ENDPOINTS.PLATFORM_RESTAURANTS, data);
    return response.data;
  },

  updateRestaurantStatus: async (id, isActive) => {
    const response = await api.patch(`${API_ENDPOINTS.PLATFORM_RESTAURANTS}/${id}/status`, {
      isActive,
    });
    return response.data;
  },
};

export default platformService;
