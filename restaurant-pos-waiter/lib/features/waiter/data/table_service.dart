import '../../../core/constants/api_constants.dart';
import '../../../core/errors/app_exception.dart';
import '../../../core/network/api_client.dart';
import '../../../shared/models/table_model.dart';

class TableService {
  final ApiClient _apiClient;

  TableService({ApiClient? apiClient})
      : _apiClient = apiClient ?? ApiClient();

  /// Fetches all tables for the authenticated user's restaurant.
  Future<List<TableModel>> getTables() async {
    final response = await _apiClient.get(ApiConstants.tablesEndpoint);
    if (response is List) {
      final tables = response
          .map((item) => TableModel.fromJson(item as Map<String, dynamic>))
          .toList();
      tables.sort((a, b) {
        final numA = int.tryParse(a.tableNumber) ?? 0;
        final numB = int.tryParse(b.tableNumber) ?? 0;
        if (numA != numB) {
          return numA.compareTo(numB);
        }
        return a.tableNumber.compareTo(b.tableNumber);
      });
      return tables;
    }
    return [];
  }

  /// Fetches details for a single table by ID.
  Future<TableModel> getTableById(int id) async {
    final response = await _apiClient.get('${ApiConstants.tablesEndpoint}/$id');
    if (response is Map<String, dynamic>) {
      return TableModel.fromJson(response);
    }
    throw const AppException('Invalid table response received from server.');
  }
}
