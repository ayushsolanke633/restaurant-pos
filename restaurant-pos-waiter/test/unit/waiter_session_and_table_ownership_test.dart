import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/constants/api_constants.dart';
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/core/network/api_client.dart';
import 'package:restaurant_pos_waiter/core/storage/secure_storage_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/auth_service.dart';

class MockTrackingApiClient extends ApiClient {
  final List<String> calledEndpoints = [];
  Map<String, dynamic>? nextResponse;
  AppException? nextException;

  @override
  Future<Map<String, dynamic>> post(
    String endpoint, {
    required Map<String, dynamic> body,
    String? token,
  }) async {
    calledEndpoints.add(endpoint);
    if (nextException != null) {
      throw nextException!;
    }
    return nextResponse ?? {};
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
  group('Waiter Single-Device Session & Logout Tests', () {
    test('logout() calls /api/auth/logout and clears local session storage', () async {
      final mockClient = MockTrackingApiClient();
      final storage = InMemoryStorageService();
      await storage.saveToken('test-jwt-token');

      final authService = AuthService(
        apiClient: mockClient,
        storageService: storage,
      );

      expect(await storage.getToken(), 'test-jwt-token');

      await authService.logout();

      expect(mockClient.calledEndpoints, contains(ApiConstants.logoutEndpoint));
      expect(await storage.getToken(), isNull);
    });

    test('logout() clears storage even if network request fails', () async {
      final mockClient = MockTrackingApiClient();
      mockClient.nextException = const AppException('Network failed', code: 'UNABLE_TO_CONNECT');
      final storage = InMemoryStorageService();
      await storage.saveToken('test-jwt-token');

      final authService = AuthService(
        apiClient: mockClient,
        storageService: storage,
      );

      await authService.logout();

      expect(mockClient.calledEndpoints, contains(ApiConstants.logoutEndpoint));
      expect(await storage.getToken(), isNull);
    });

    test('login rejects second device with DuplicateSession 409 message', () async {
      final mockClient = MockTrackingApiClient();
      mockClient.nextException = const AppException(
        'This waiter account is already logged in on another device. Please log out from the other device first.',
        code: '409',
      );
      final storage = InMemoryStorageService();

      final authService = AuthService(
        apiClient: mockClient,
        storageService: storage,
      );

      expect(
        () async => await authService.login('waiter1@restaurant.com', 'password'),
        throwsA(
          isA<AppException>()
              .having((e) => e.code, 'code', '409')
              .having(
                (e) => e.message,
                'message',
                'This waiter account is already logged in on another device. Please log out from the other device first.',
              ),
        ),
      );
      expect(await storage.getToken(), isNull);
    });
  });
}
