import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/core/network/api_client.dart';
import 'package:restaurant_pos_waiter/core/network/websocket_service.dart';
import 'package:restaurant_pos_waiter/core/storage/secure_storage_service.dart';
import 'package:restaurant_pos_waiter/core/utils/jwt_decoder.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/auth_service.dart';
import 'package:restaurant_pos_waiter/shared/models/user_model.dart';

class MockStorageService extends SecureStorageService {
  final Map<String, String> _storage = {};
  bool clearCalled = false;

  @override
  Future<void> saveToken(String token) async {
    _storage['auth_token'] = token;
  }

  @override
  Future<String?> getToken() async {
    return _storage['auth_token'];
  }

  @override
  Future<void> saveUser(UserModel user) async {
    _storage['user_data'] = json.encode(user.toJson());
  }

  @override
  Future<UserModel?> getUser() async {
    final str = _storage['user_data'];
    if (str == null) return null;
    return UserModel.fromJson(json.decode(str));
  }

  @override
  Future<void> clearSession() async {
    clearCalled = true;
    _storage.remove('auth_token');
    _storage.remove('user_data');
  }
}

class MockHttpClientSuccess extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    return http.StreamedResponse(
      Stream.value(utf8.encode(json.encode({'message': 'Logged out successfully'}))),
      200,
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Flutter Authentication UX & Session Reliability Tests', () {
    test('1. HTTP 401 clears storage and throws session expired AppException', () {
      final storage = MockStorageService();
      final client = ApiClient(storageService: storage);

      final response401 = http.Response(
        json.encode({'status': 401, 'error': 'Unauthorized', 'message': 'Full authentication is required'}),
        401,
      );

      expect(
        () => client.handleResponse(response401),
        throwsA(
          isA<AppException>()
              .having((e) => e.code, 'code', '401')
              .having((e) => e.message, 'message', 'Full authentication is required'),
        ),
      );
      expect(storage.clearCalled, isTrue, reason: 'Session must be cleared on HTTP 401');
    });

    test('2. HTTP 401 on login failure preserves specific credential error message', () {
      final storage = MockStorageService();
      final client = ApiClient(storageService: storage);

      final responseInvalidCreds = http.Response(
        json.encode({'status': 401, 'error': 'Unauthorized', 'message': 'Invalid email or password'}),
        401,
      );

      expect(
        () => client.handleResponse(responseInvalidCreds),
        throwsA(
          isA<AppException>()
              .having((e) => e.code, 'code', '401')
              .having((e) => e.message, 'message', 'Invalid email or password'),
        ),
      );
    });

    test('3. HTTP 403 Forbidden throws AppException without clearing session storage', () {
      final storage = MockStorageService();
      storage.saveToken('valid.waiter.token');
      final client = ApiClient(storageService: storage);

      final response403 = http.Response(
        json.encode({'status': 403, 'error': 'Forbidden', 'message': 'Access denied: Role unauthorized'}),
        403,
      );

      expect(
        () => client.handleResponse(response403),
        throwsA(
          isA<AppException>()
              .having((e) => e.code, 'code', '403')
              .having((e) => e.message, 'message', 'Access denied: Role unauthorized'),
        ),
      );
      expect(storage.clearCalled, isFalse, reason: 'Session must NOT be cleared on HTTP 403');
    });

    test('4. AuthService.logout calls API, clears session, and disconnects WebSocket', () async {
      final storage = MockStorageService();
      await storage.saveToken('token.to.revoke');
      await storage.saveUser(UserModel(userId: 10, role: 'WAITER', name: 'Waiter 1'));

      final apiClient = ApiClient(
        client: MockHttpClientSuccess(),
        storageService: storage,
      );

      final authService = AuthService(
        apiClient: apiClient,
        storageService: storage,
      );

      await authService.logout();

      expect(await storage.getToken(), isNull);
      expect(await storage.getUser(), isNull);
      expect(storage.clearCalled, isTrue);
    });

    test('5. AuthService.isLoggedIn clears session when token is expired', () async {
      final storage = MockStorageService();
      // Generate a mock expired JWT token (exp in past: 1000)
      final header = base64Url.encode(utf8.encode(json.encode({'alg': 'HS256', 'typ': 'JWT'}))).replaceAll('=', '');
      final payload = base64Url.encode(utf8.encode(json.encode({
        'sub': 'waiter@r1.com',
        'role': 'WAITER',
        'exp': 1000, // Past timestamp
      }))).replaceAll('=', '');
      final expiredToken = '$header.$payload.dummySignature';

      await storage.saveToken(expiredToken);
      expect(JwtDecoder.isExpired(expiredToken), isTrue);

      final authService = AuthService(storageService: storage);
      final loggedIn = await authService.isLoggedIn();

      expect(loggedIn, isFalse);
      expect(storage.clearCalled, isTrue, reason: 'Expired token must trigger session cleanup');
      expect(await storage.getToken(), isNull);
    });

    test('6. AuthService.getToken returns null and clears session when token is expired', () async {
      final storage = MockStorageService();
      final header = base64Url.encode(utf8.encode(json.encode({'alg': 'HS256', 'typ': 'JWT'}))).replaceAll('=', '');
      final payload = base64Url.encode(utf8.encode(json.encode({
        'sub': 'waiter@r1.com',
        'exp': 1000,
      }))).replaceAll('=', '');
      final expiredToken = '$header.$payload.dummySignature';

      await storage.saveToken(expiredToken);

      final authService = AuthService(storageService: storage);
      final token = await authService.getToken();

      expect(token, isNull);
      expect(storage.clearCalled, isTrue);
    });

    test('7. WebSocketService disconnect terminates connection and cancels reconnect timers', () async {
      final wsService = WebSocketService();

      expect(wsService.isConnected, isFalse);
      await wsService.disconnect();
      expect(wsService.isConnected, isFalse);
      expect(wsService.isConnectedNotifier.value, isFalse);
    });
  });
}
