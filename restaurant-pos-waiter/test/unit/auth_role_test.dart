import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/core/network/api_client.dart';
import 'package:restaurant_pos_waiter/core/storage/secure_storage_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/auth_service.dart';

class MockApiClient extends ApiClient {
  final Map<String, dynamic> mockResponse;

  MockApiClient(this.mockResponse);

  @override
  Future<Map<String, dynamic>> post(
    String endpoint, {
    required Map<String, dynamic> body,
    String? token,
  }) async {
    return mockResponse;
  }
}

class InMemoryStorageService extends SecureStorageService {
  final Map<String, String> _data = {};

  @override
  Future<void> saveToken(String token) async {
    _data['auth_token'] = token;
  }

  @override
  Future<String?> getToken() async {
    return _data['auth_token'];
  }

  @override
  Future<void> saveUser(user) async {
    _data['user_data'] = 'user_json';
  }

  @override
  Future<void> clearSession() async {
    _data.clear();
  }
}

void main() {
  group('AuthService Role Enforcement Tests', () {
    test('login succeeds for WAITER role', () async {
      final mockResponse = {
        'token': 'mock.waiter.jwt',
        'tokenType': 'Bearer',
        'role': 'WAITER',
        'userId': 1,
        'restaurantId': 1,
        'name': 'Waiter Bob',
        'email': 'waiter@example.com',
      };

      final storage = InMemoryStorageService();
      final authService = AuthService(
        apiClient: MockApiClient(mockResponse),
        storageService: storage,
      );

      final response = await authService.login('waiter@example.com', 'password');

      expect(response.role, 'WAITER');
      expect(await storage.getToken(), 'mock.waiter.jwt');
    });

    test('login throws ROLE_DENIED exception for ADMIN role and clears session', () async {
      final mockResponse = {
        'token': 'mock.admin.jwt',
        'tokenType': 'Bearer',
        'role': 'ADMIN',
        'userId': 2,
        'restaurantId': 1,
        'name': 'Admin Alice',
        'email': 'admin@example.com',
      };

      final storage = InMemoryStorageService();
      final authService = AuthService(
        apiClient: MockApiClient(mockResponse),
        storageService: storage,
      );

      expect(
        () async => await authService.login('admin@example.com', 'password'),
        throwsA(
          isA<AppException>()
              .having((e) => e.code, 'code', 'ROLE_DENIED')
              .having((e) => e.message, 'message', 'This app is only for Waiter accounts.'),
        ),
      );

      expect(await storage.getToken(), isNull);
    });

    test('login throws ROLE_DENIED exception for KITCHEN role', () async {
      final mockResponse = {
        'token': 'mock.kitchen.jwt',
        'tokenType': 'Bearer',
        'role': 'KITCHEN',
        'userId': 3,
        'restaurantId': 1,
        'name': 'Chef Charlie',
        'email': 'kitchen@example.com',
      };

      final storage = InMemoryStorageService();
      final authService = AuthService(
        apiClient: MockApiClient(mockResponse),
        storageService: storage,
      );

      expect(
        () async => await authService.login('kitchen@example.com', 'password'),
        throwsA(
          isA<AppException>()
              .having((e) => e.code, 'code', 'ROLE_DENIED'),
        ),
      );
    });
  });
}
