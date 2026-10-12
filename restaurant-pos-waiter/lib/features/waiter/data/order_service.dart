import '../../../../core/network/api_client.dart';
import '../../../../shared/models/cart_item_model.dart';
import '../../../../shared/models/order_model.dart';

class OrderService {
  final ApiClient _apiClient;

  OrderService({ApiClient? apiClient}) : _apiClient = apiClient ?? ApiClient();

  Future<OrderModel> createOrder({
    required int tableId,
    required List<CartItemModel> items,
    String? notes,
  }) async {
    final body = <String, dynamic>{
      'orderType': 'DINE_IN',
      'tableId': tableId,
      if (notes != null && notes.trim().isNotEmpty) 'notes': notes.trim(),
      'items': items.map((item) => item.toOrderItemRequest()).toList(),
    };

    final response = await _apiClient.post('/api/orders', body: body);
    return OrderModel.fromJson(response);
  }

  Future<OrderModel?> getActiveOrderByTable(int tableId) async {
    try {
      final response = await _apiClient.get('/api/orders/table/$tableId/active');
      if (response is Map<String, dynamic> && response.isNotEmpty) {
        return OrderModel.fromJson(response);
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  Future<OrderModel> getOrderById(int orderId) async {
    final response = await _apiClient.get('/api/orders/$orderId');
    return OrderModel.fromJson(response as Map<String, dynamic>);
  }

  Future<OrderModel> updateOrder(
    int orderId, {
    required List<CartItemModel> items,
    String? notes,
  }) async {
    final body = <String, dynamic>{
      'orderType': 'DINE_IN',
      if (notes != null && notes.trim().isNotEmpty) 'notes': notes.trim(),
      'items': items.map((item) => item.toOrderItemRequest()).toList(),
    };

    final response = await _apiClient.put('/api/orders/$orderId', body: body);
    return OrderModel.fromJson(response);
  }

  Future<OrderModel> cancelOrder(int orderId) async {
    final response = await _apiClient.patch('/api/orders/$orderId/cancel');
    return OrderModel.fromJson(response);
  }

  Future<OrderModel> addItemsToOrder(
    int orderId, {
    required List<CartItemModel> items,
    String? notes,
  }) async {
    final body = <String, dynamic>{
      'orderType': 'DINE_IN',
      if (notes != null && notes.trim().isNotEmpty) 'notes': notes.trim(),
      'items': items.map((item) => item.toOrderItemRequest()).toList(),
    };

    final response = await _apiClient.post('/api/orders/$orderId/items', body: body);
    return OrderModel.fromJson(response);
  }

  Future<List<OrderModel>> getWaiterOrderHistory({String? status}) async {
    String path = '/api/orders/waiter/history';
    if (status != null && status.trim().isNotEmpty && status.trim().toUpperCase() != 'ALL') {
      path += '?status=${status.trim().toUpperCase()}';
    }
    final response = await _apiClient.get(path);
    if (response is List) {
      return response
          .map((json) => OrderModel.fromJson(json as Map<String, dynamic>))
          .toList();
    }
    return [];
  }
}
