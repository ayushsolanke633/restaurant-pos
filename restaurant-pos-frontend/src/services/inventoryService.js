import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

/**
 * Inventory Management Service
 * Interacts with Spring Boot backend InventoryController endpoints (/api/inventory-items)
 */
export const inventoryService = {
  /**
   * Fetch all inventory items for current restaurant
   * @returns {Promise<Array>} List of InventoryItemResponse
   */
  async getAllInventoryItems() {
    const response = await api.get(API_ENDPOINTS.INVENTORY_ITEMS);
    return response.data;
  },

  /**
   * Fetch single inventory item by ID
   * @param {number|string} id 
   * @returns {Promise<Object>} InventoryItemResponse
   */
  async getInventoryItemById(id) {
    const response = await api.get(`${API_ENDPOINTS.INVENTORY_ITEMS}/${id}`);
    return response.data;
  },

  /**
   * Create a new inventory item
   * @param {Object} itemData - { name, unit, minimumQuantity, costPerUnit, isActive }
   * @returns {Promise<Object>} InventoryItemResponse
   */
  async createInventoryItem(itemData) {
    const response = await api.post(API_ENDPOINTS.INVENTORY_ITEMS, itemData);
    return response.data;
  },

  /**
   * Update an existing inventory item
   * @param {number|string} id 
   * @param {Object} itemData - { name, unit, minimumQuantity, costPerUnit, isActive }
   * @returns {Promise<Object>} InventoryItemResponse
   */
  async updateInventoryItem(id, itemData) {
    const response = await api.put(`${API_ENDPOINTS.INVENTORY_ITEMS}/${id}`, itemData);
    return response.data;
  },

  /**
   * Activate an inventory item
   * @param {number|string} id 
   * @returns {Promise<Object>} InventoryItemResponse
   */
  async activateInventoryItem(id) {
    const response = await api.patch(`${API_ENDPOINTS.INVENTORY_ITEMS}/${id}/activate`);
    return response.data;
  },

  /**
   * Deactivate an inventory item
   * @param {number|string} id 
   * @returns {Promise<Object>} InventoryItemResponse
   */
  async deactivateInventoryItem(id) {
    const response = await api.patch(`${API_ENDPOINTS.INVENTORY_ITEMS}/${id}/deactivate`);
    return response.data;
  },

  /**
   * Record stock purchase (+ Stock)
   * @param {number|string} id 
   * @param {Object} transactionData - { quantity, referenceType, referenceId }
   * @returns {Promise<Object>} InventoryTransactionResponse
   */
  async recordPurchase(id, transactionData) {
    const response = await api.post(`${API_ENDPOINTS.INVENTORY_ITEMS}/${id}/purchase`, transactionData);
    return response.data;
  },

  /**
   * Record manual stock adjustment (+/- Stock)
   * @param {number|string} id 
   * @param {Object} transactionData - { quantity, referenceType, referenceId }
   * @returns {Promise<Object>} InventoryTransactionResponse
   */
  async recordAdjustment(id, transactionData) {
    const response = await api.post(`${API_ENDPOINTS.INVENTORY_ITEMS}/${id}/adjust`, transactionData);
    return response.data;
  },

  /**
   * Record stock wastage (- Stock)
   * @param {number|string} id 
   * @param {Object} transactionData - { quantity, referenceType, referenceId }
   * @returns {Promise<Object>} InventoryTransactionResponse
   */
  async recordWastage(id, transactionData) {
    const response = await api.post(`${API_ENDPOINTS.INVENTORY_ITEMS}/${id}/wastage`, transactionData);
    return response.data;
  },

  /**
   * Get transaction history for a specific inventory item
   * @param {number|string} id 
   * @returns {Promise<Array>} List of InventoryTransactionResponse
   */
  async getItemTransactions(id) {
    const response = await api.get(`${API_ENDPOINTS.INVENTORY_ITEMS}/${id}/transactions`);
    return response.data;
  },

  /**
   * Get all inventory transaction history for current restaurant
   * @returns {Promise<Array>} List of InventoryTransactionResponse
   */
  async getAllTransactions() {
    const response = await api.get(API_ENDPOINTS.INVENTORY_TRANSACTIONS);
    return response.data;
  },
};

export default inventoryService;
