import '../../../core/constants/api_constants.dart';
import '../../../core/errors/app_exception.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/secure_storage_service.dart';
import '../../../core/utils/jwt_decoder.dart';
import '../../../shared/models/login_request.dart';
import '../../../shared/models/login_response.dart';
import '../../../shared/models/user_model.dart';

class AuthService {
  final ApiClient _apiClient;
  final SecureStorageService _storageService;

  AuthService({
    ApiClient? apiClient,
    SecureStorageService? storageService,
  })  : _apiClient = apiClient ?? ApiClient(),
        _storageService = storageService ?? SecureStorageService();

  /// Executes login request against backend, checks WAITER role, and securely persists token/user.
  Future<LoginResponse> login(String email, String password) async {
    final cleanEmail = email.trim();
    if (cleanEmail.isEmpty || password.isEmpty) {
      throw const AppException(
        'Email and password are required.',
        code: 'VALIDATION_ERROR',
      );
    }

    final request = LoginRequest(
      email: cleanEmail,
      password: password,
    );

    final responseMap = await _apiClient.post(
      ApiConstants.loginEndpoint,
      body: request.toJson(),
    );

    final loginResponse = LoginResponse.fromJson(responseMap);

    // Role Enforcement — Only Waiter accounts can use the waiter mobile app
    if (loginResponse.role != 'WAITER') {
      await clearSession();
      throw const AppException(
        'This app is only for Waiter accounts.',
        code: 'ROLE_DENIED',
      );
    }

    final user = UserModel(
      userId: loginResponse.userId,
      restaurantId: loginResponse.restaurantId,
      role: loginResponse.role,
      name: loginResponse.name,
      email: loginResponse.email ?? cleanEmail,
    );

    await _storageService.saveToken(loginResponse.token);
    await _storageService.saveUser(user);

    return loginResponse;
  }

  /// Logs out the user by revoking backend session and clearing local secure session storage.
  /// Guaranteed to clear local session even if backend call fails (e.g. HTTP 503) or network is down.
  /// NOTE: Clearing local credentials ensures this device is signed out, but is not proof that
  /// a stolen or copied token was successfully revoked on the backend if the server failed
  /// to persist the revocation record.
  Future<void> logout() async {
    try {
      await _apiClient.post(ApiConstants.logoutEndpoint, body: {});
    } catch (_) {
      // In case of network errors or server unreachability / 503, still clear local session safely
    } finally {
      await clearSession();
    }
  }

  /// Checks if a valid, non-expired JWT token exists.
  Future<bool> isLoggedIn() async {
    final token = await _storageService.getToken();
    if (token == null || token.isEmpty) {
      return false;
    }

    if (JwtDecoder.isExpired(token)) {
      await clearSession();
      return false;
    }

    return true;
  }

  /// Retrieves the current JWT token if valid and unexpired.
  Future<String?> getToken() async {
    final token = await _storageService.getToken();
    if (token == null || token.isEmpty) {
      return null;
    }

    if (JwtDecoder.isExpired(token)) {
      await clearSession();
      return null;
    }

    return token;
  }

  /// Retrieves stored user information.
  Future<UserModel?> getStoredUser() async {
    return await _storageService.getUser();
  }

  /// Clears stored JWT token and user info from secure storage.
  Future<void> clearSession() async {
    await _storageService.clearSession();
  }
}
