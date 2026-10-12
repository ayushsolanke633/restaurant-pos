import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/core/network/api_client.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/table_service.dart';

class MockTableApiClient extends ApiClient {
  dynamic mockGetResponse;
  String? lastRequestedEndpoint;

  MockTableApiClient({this.mockGetResponse});

  @override
  Future<dynamic> get(
    String endpoint, {
    String? token,
  }) async {
    lastRequestedEndpoint = endpoint;
    if (mockGetResponse is Exception) {
      throw mockGetResponse as Exception;
    }
    return mockGetResponse;
  }
}

void main() {
  group('TableService Unit Tests', () {
    test('5. getTables returns list of TableModel instances', () async {
      final mockResponse = [
        {
          'id': 1,
          'restaurantId': 2,
          'tableNumber': '01',
          'capacity': 4,
          'status': 'AVAILABLE',
        },
        {
          'id': 2,
          'restaurantId': 2,
          'tableNumber': '02',
          'capacity': 2,
          'status': 'OCCUPIED',
        },
      ];

      final apiClient = MockTableApiClient(mockGetResponse: mockResponse);
      final tableService = TableService(apiClient: apiClient);

      final tables = await tableService.getTables();

      expect(tables.length, 2);
      expect(tables[0].tableNumber, '01');
      expect(tables[0].status, 'AVAILABLE');
      expect(tables[1].tableNumber, '02');
      expect(tables[1].status, 'OCCUPIED');
      expect(apiClient.lastRequestedEndpoint, '/api/tables');
    });

    test('getTableById returns single TableModel instance', () async {
      final mockResponse = {
        'id': 5,
        'restaurantId': 2,
        'tableNumber': '05',
        'capacity': 6,
        'status': 'BILLING',
      };

      final apiClient = MockTableApiClient(mockGetResponse: mockResponse);
      final tableService = TableService(apiClient: apiClient);

      final table = await tableService.getTableById(5);

      expect(table.id, 5);
      expect(table.tableNumber, '05');
      expect(table.status, 'BILLING');
      expect(apiClient.lastRequestedEndpoint, '/api/tables/5');
    });

    test('getTables propagates AppException on network or authorization error', () async {
      final apiClient = MockTableApiClient(
        mockGetResponse: const AppException('Access denied.', code: '403'),
      );
      final tableService = TableService(apiClient: apiClient);

      expect(
        () async => await tableService.getTables(),
        throwsA(
          isA<AppException>()
              .having((e) => e.code, 'code', '403')
              .having((e) => e.message, 'message', 'Access denied.'),
        ),
      );
    });

    test('getTables returns tables sorted numerically by tableNumber', () async {
      final mockResponse = [
        {'id': 10, 'restaurantId': 1, 'tableNumber': '16', 'capacity': 4, 'status': 'AVAILABLE'},
        {'id': 11, 'restaurantId': 1, 'tableNumber': '13', 'capacity': 4, 'status': 'OCCUPIED'},
        {'id': 12, 'restaurantId': 1, 'tableNumber': '1', 'capacity': 2, 'status': 'AVAILABLE'},
        {'id': 13, 'restaurantId': 1, 'tableNumber': '15', 'capacity': 4, 'status': 'BILLING'},
        {'id': 14, 'restaurantId': 1, 'tableNumber': '11', 'capacity': 4, 'status': 'AVAILABLE'},
        {'id': 15, 'restaurantId': 1, 'tableNumber': '2', 'capacity': 4, 'status': 'AVAILABLE'},
        {'id': 16, 'restaurantId': 1, 'tableNumber': '8', 'capacity': 4, 'status': 'AVAILABLE'},
        {'id': 17, 'restaurantId': 1, 'tableNumber': '7', 'capacity': 4, 'status': 'AVAILABLE'},
        {'id': 18, 'restaurantId': 1, 'tableNumber': '3', 'capacity': 4, 'status': 'AVAILABLE'},
        {'id': 19, 'restaurantId': 1, 'tableNumber': '4', 'capacity': 4, 'status': 'AVAILABLE'},
        {'id': 20, 'restaurantId': 1, 'tableNumber': '5', 'capacity': 4, 'status': 'AVAILABLE'},
        {'id': 21, 'restaurantId': 1, 'tableNumber': '14', 'capacity': 4, 'status': 'AVAILABLE'},
      ];

      final apiClient = MockTableApiClient(mockGetResponse: mockResponse);
      final tableService = TableService(apiClient: apiClient);

      final tables = await tableService.getTables();
      final numbers = tables.map((t) => t.tableNumber).toList();

      expect(
        numbers,
        ['1', '2', '3', '4', '5', '7', '8', '11', '13', '14', '15', '16'],
      );
    });
  });
}
