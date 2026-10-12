import api from './api';
import { API_ENDPOINTS } from '../utils/constants';

/**
 * Reports & Analytics Service
 * Interacts with Spring Boot backend ReportController endpoints (/api/reports)
 */
export const reportService = {
  /**
   * Fetch today's real-time dashboard summary metrics
   * @returns {Promise<Object>} DashboardResponse
   */
  async getDashboardSummary() {
    const response = await api.get(`${API_ENDPOINTS.REPORTS}/dashboard`);
    return response.data;
  },

  /**
   * Fetch sales report for a specified date range
   * @param {string} from - ISO Date string YYYY-MM-DD
   * @param {string} to - ISO Date string YYYY-MM-DD
   * @returns {Promise<Object>} SalesReportResponse
   */
  async getSalesReport(from, to) {
    const response = await api.get(`${API_ENDPOINTS.REPORTS}/sales`, {
      params: { from, to },
    });
    return response.data;
  },

  /**
   * Fetch daily sales breakdown for a date range
   * @param {string} from - ISO Date string YYYY-MM-DD
   * @param {string} to - ISO Date string YYYY-MM-DD
   * @returns {Promise<Array>} List of DailySalesResponse
   */
  async getDailySalesBreakdown(from, to) {
    const response = await api.get(`${API_ENDPOINTS.REPORTS}/sales/daily`, {
      params: { from, to },
    });
    return response.data;
  },

  /**
   * Fetch payment method breakdown for a date range
   * @param {string} from - ISO Date string YYYY-MM-DD
   * @param {string} to - ISO Date string YYYY-MM-DD
   * @returns {Promise<Array>} List of PaymentMethodReportResponse
   */
  async getPaymentMethodBreakdown(from, to) {
    const response = await api.get(`${API_ENDPOINTS.REPORTS}/payments`, {
      params: { from, to },
    });
    return response.data;
  },

  /**
   * Fetch order status statistics for a date range
   * @param {string} from - ISO Date string YYYY-MM-DD
   * @param {string} to - ISO Date string YYYY-MM-DD
   * @returns {Promise<Object>} OrderStatusReportResponse
   */
  async getOrderStatusStatistics(from, to) {
    const response = await api.get(`${API_ENDPOINTS.REPORTS}/orders`, {
      params: { from, to },
    });
    return response.data;
  },

  /**
   * Fetch order type breakdown (Dine-In vs Takeaway) for a date range
   * @param {string} from - ISO Date string YYYY-MM-DD
   * @param {string} to - ISO Date string YYYY-MM-DD
   * @returns {Promise<Array>} List of OrderTypeReportResponse
   */
  async getOrderTypeReport(from, to) {
    const response = await api.get(`${API_ENDPOINTS.REPORTS}/orders/types`, {
      params: { from, to },
    });
    return response.data;
  },

  /**
   * Fetch top selling menu items for a date range
   * @param {string} from - ISO Date string YYYY-MM-DD
   * @param {string} to - ISO Date string YYYY-MM-DD
   * @param {number} [limit=10]
   * @returns {Promise<Array>} List of TopSellingItemResponse
   */
  async getTopSellingItems(from, to, limit = 10) {
    const response = await api.get(`${API_ENDPOINTS.REPORTS}/top-items`, {
      params: { from, to, limit },
    });
    return response.data;
  },

  /**
   * Fetch category sales performance report for a date range
   * @param {string} from - ISO Date string YYYY-MM-DD
   * @param {string} to - ISO Date string YYYY-MM-DD
   * @returns {Promise<Array>} List of CategorySalesResponse
   */
  async getCategorySalesReport(from, to) {
    const response = await api.get(`${API_ENDPOINTS.REPORTS}/categories`, {
      params: { from, to },
    });
    return response.data;
  },

  /**
   * Fetch bills list for report date range
   * @param {string} from - ISO Date string YYYY-MM-DD
   * @param {string} to - ISO Date string YYYY-MM-DD
   * @returns {Promise<Array>} List of ReportBillResponse
   */
  async getReportBills(from, to) {
    const response = await api.get(`${API_ENDPOINTS.REPORTS}/bills`, {
      params: { from, to },
    });
    return response.data;
  },
};

export default reportService;
