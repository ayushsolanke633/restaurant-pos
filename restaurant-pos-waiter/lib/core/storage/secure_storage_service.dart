import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../shared/models/user_model.dart';

class SecureStorageService {
  final FlutterSecureStorage _storage;

  static const String _keyToken = 'auth_token';
  static const String _keyUser = 'user_data';
  static const String _keyManualUrl = 'manual_server_url';
  static const String _keyCachedUrl = 'cached_server_url';

  SecureStorageService({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  Future<void> saveToken(String token) async {
    await _storage.write(key: _keyToken, value: token);
  }

  Future<String?> getToken() async {
    return await _storage.read(key: _keyToken);
  }

  Future<void> saveUser(UserModel user) async {
    final userJson = json.encode(user.toJson());
    await _storage.write(key: _keyUser, value: userJson);
  }

  Future<UserModel?> getUser() async {
    final userJson = await _storage.read(key: _keyUser);
    if (userJson == null || userJson.isEmpty) {
      return null;
    }
    try {
      final map = json.decode(userJson) as Map<String, dynamic>;
      return UserModel.fromJson(map);
    } catch (_) {
      return null;
    }
  }

  Future<void> clearSession() async {
    await _storage.delete(key: _keyToken);
    await _storage.delete(key: _keyUser);
  }

  Future<void> saveManualServerUrl(String url) async {
    await _storage.write(key: _keyManualUrl, value: url);
  }

  Future<String?> getManualServerUrl() async {
    return await _storage.read(key: _keyManualUrl);
  }

  Future<void> clearManualServerUrl() async {
    await _storage.delete(key: _keyManualUrl);
  }

  Future<void> saveCachedServerUrl(String url) async {
    await _storage.write(key: _keyCachedUrl, value: url);
  }

  Future<String?> getCachedServerUrl() async {
    return await _storage.read(key: _keyCachedUrl);
  }

  Future<void> clearCachedServerUrl() async {
    await _storage.delete(key: _keyCachedUrl);
  }
}
