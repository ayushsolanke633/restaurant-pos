import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

export const ROLE_MAP = {
  ADMIN: { id: 1, name: 'ADMIN', label: 'Admin' },
  WAITER: { id: 2, name: 'WAITER', label: 'Waiter' },
  KITCHEN: { id: 3, name: 'KITCHEN', label: 'Kitchen' },
};

/**
 * Staff / User Management Service
 * Interacts with backend UserController endpoints (/api/users)
 */
export const staffService = {
  /**
   * Fetch all staff members for a restaurant
   * @param {number|string} restaurantId 
   * @returns {Promise<Array>} List of UserResponse
   */
  async getStaffMembers(restaurantId) {
    const response = await api.get(`${API_ENDPOINTS.USERS}/restaurant/${restaurantId}`);
    return response.data;
  },

  /**
   * Fetch single staff member by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} UserResponse
   */
  async getStaffMemberById(id) {
    const response = await api.get(`${API_ENDPOINTS.USERS}/${id}`);
    return response.data;
  },

  /**
   * Create a new staff member
   * @param {Object} staffData - { restaurantId, roleId, name, email, phone, password, isActive }
   * @returns {Promise<Object>} UserResponse
   */
  async createStaffMember(staffData) {
    const response = await api.post(API_ENDPOINTS.USERS, staffData);
    return response.data;
  },

  /**
   * Update an existing staff member
   * @param {number|string} id 
   * @param {Object} staffData - { restaurantId, roleId, name, email, phone, password, isActive }
   * @returns {Promise<Object>} UserResponse
   */
  async updateStaffMember(id, staffData) {
    // ADMIN accounts (roleId === 1) must always remain active
    if (staffData.roleId === 1 || staffData.roleId === ROLE_MAP.ADMIN.id) {
      staffData.isActive = true;
    }
    const response = await api.put(`${API_ENDPOINTS.USERS}/${id}`, staffData);
    return response.data;
  },

  /**
   * Deactivate staff member (sets isActive = false)
   * @param {number|string} id 
   * @param {string} [roleName] 
   * @returns {Promise<Object>} UserResponse
   */
  async deactivateStaffMember(id, roleName) {
    if (roleName === 'ADMIN') {
      throw new Error('ADMIN accounts cannot be deactivated.');
    }
    const response = await api.patch(`${API_ENDPOINTS.USERS}/${id}/deactivate`);
    return response.data;
  },

  /**
   * Reactivate staff member (updates isActive = true)
   * @param {number|string} id 
   * @param {Object} currentData 
   * @returns {Promise<Object>} UserResponse
   */
  async activateStaffMember(id, currentData) {
    const payload = {
      restaurantId: currentData.restaurantId,
      roleId: currentData.roleId,
      name: currentData.name,
      email: currentData.email,
      phone: currentData.phone,
      isActive: true,
    };
    const response = await api.put(`${API_ENDPOINTS.USERS}/${id}`, payload);
    return response.data;
  },
};

export default staffService;
