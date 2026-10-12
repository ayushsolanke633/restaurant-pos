import 'dart:io';
import 'package:flutter/foundation.dart';

class ApiConstants {
  ApiConstants._();

  static const String _customBaseUrl = String.fromEnvironment('BACKEND_BASE_URL');
  static const String _lanIp = String.fromEnvironment('LAN_IP', defaultValue: '192.168.74.55');
  static const bool _useEmulator = bool.fromEnvironment('USE_EMULATOR', defaultValue: false);

  static const String _localHostUrl = 'http://localhost:8080';
  static const String _emulatorUrl = 'http://10.0.2.2:8080';

  /// Documented placeholder HTTPS endpoint for production builds until client supplies production domain.
  static const String productionPlaceholderUrl = 'https://pos-api.example-restaurant-pos.com';

  /// Baseline backend URL resolution:
  /// - In release mode: Requires a configured HTTPS URL (via --dart-define=BACKEND_BASE_URL)
  ///   or falls back to the documented production placeholder. Never silently uses LAN or localhost.
  /// - In debug/profile mode: Preserves local development defaults (emulator, LAN IP, localhost).
  static String get baseUrl {
    if (kReleaseMode) {
      if (_customBaseUrl.isNotEmpty) {
        if (!_customBaseUrl.startsWith('https://')) {
          // Reject cleartext HTTP in release mode
          return 'https://${_customBaseUrl.replaceFirst(RegExp(r'^https?://'), '')}';
        }
        return _customBaseUrl;
      }
      return productionPlaceholderUrl;
    }

    if (_customBaseUrl.isNotEmpty) {
      return _customBaseUrl;
    }
    if (kIsWeb) {
      return _localHostUrl;
    }
    if (Platform.isAndroid) {
      if (_useEmulator) {
        return _emulatorUrl;
      }
      return 'http://$_lanIp:8080';
    }
    return _localHostUrl;
  }

  static const String apiVersion = '/api/v1';
  static const String loginEndpoint = '/api/auth/login';
  static const String logoutEndpoint = '/api/auth/logout';
  static const String tablesEndpoint = '/api/tables';
  static const String healthEndpoint = '/api/health';
}
