import '../../../../core/network/api_client.dart';
import '../../../../shared/models/category_model.dart';
import '../../../../shared/models/menu_item_model.dart';

class MenuService {
  final ApiClient _apiClient;

  MenuService({ApiClient? apiClient}) : _apiClient = apiClient ?? ApiClient();

  Future<List<CategoryModel>> getCategories() async {
    final response = await _apiClient.get('/api/categories');
    if (response is List) {
      return response.map((json) => CategoryModel.fromJson(json)).toList();
    }
    return [];
  }

  Future<List<MenuItemModel>> getMenuItems({int? categoryId}) async {
    String endpoint = '/api/menu-items';
    if (categoryId != null) {
      endpoint = '/api/menu-items?categoryId=$categoryId';
    }

    final response = await _apiClient.get(endpoint);
    if (response is List) {
      return response.map((json) => MenuItemModel.fromJson(json)).toList();
    }
    return [];
  }
}
