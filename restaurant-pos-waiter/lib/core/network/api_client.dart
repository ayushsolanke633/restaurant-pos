import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../constants/api_constants.dart';
import '../errors/app_exception.dart';
import '../storage/secure_storage_service.dart';
import 'backend_url_resolver.dart';

class ApiClient {
  final http.Client _client;
  final SecureStorageService _storageService;
  final BackendUrlResolver _urlResolver;
  String? _currentBaseUrl;

  ApiClient({
    http.Client? client,
    String? baseUrl,
    SecureStorageService? storageService,
    BackendUrlResolver? urlResolver,
  })  : _client = client ?? http.Client(),
        _storageService = storageService ?? SecureStorageService(),
        _urlResolver = urlResolver ??
            BackendUrlResolver(
              storageService: storageService ?? SecureStorageService(),
              client: client,
            ),
        _currentBaseUrl = baseUrl;

  /// Gets current active base URL or resolves it using the URL resolver.
  Future<String> getBaseUrl({bool forceRediscovery = false}) async {
    if (_currentBaseUrl != null && !forceRediscovery) {
      return _currentBaseUrl!;
    }
    final resolved = await _urlResolver.resolveUrl(forceRediscovery: forceRediscovery);
    if (resolved != null && resolved.isNotEmpty) {
      _currentBaseUrl = resolved;
      return resolved;
    }
    // Fallback baseline URL
    _currentBaseUrl = ApiConstants.baseUrl;
    return _currentBaseUrl!;
  }

  /// Sets or overrides the current base URL manually.
  void setBaseUrl(String url) {
    _currentBaseUrl = _urlResolver.normalizeUrl(url);
  }



  Future<dynamic> get(
    String endpoint, {
    String? token,
  }) async {
    final authToken = token ?? await _storageService.getToken();
    final headers = <String, String>{
      'Content-Type': 'application/json',
      if (authToken != null && authToken.isNotEmpty) 'Authorization': 'Bearer $authToken',
    };

    return _executeWithRecovery(endpoint, 'GET', headers, (baseUrl) async {
      final uri = Uri.parse('$baseUrl$endpoint');
      return await _client.get(uri, headers: headers);
    });
  }

  Future<Map<String, dynamic>> post(
    String endpoint, {
    required Map<String, dynamic> body,
    String? token,
  }) async {
    final authToken = token ?? await _storageService.getToken();
    final headers = <String, String>{
      'Content-Type': 'application/json',
      if (authToken != null && authToken.isNotEmpty) 'Authorization': 'Bearer $authToken',
    };

    final result = await _executeWithRecovery(endpoint, 'POST', headers, (baseUrl) async {
      final uri = Uri.parse('$baseUrl$endpoint');
      return await _client.post(
        uri,
        headers: headers,
        body: json.encode(body),
      );
    });

    if (result is Map<String, dynamic>) {
      return result;
    }
    return {};
  }

  Future<Map<String, dynamic>> put(
    String endpoint, {
    required Map<String, dynamic> body,
    String? token,
  }) async {
    final authToken = token ?? await _storageService.getToken();
    final headers = <String, String>{
      'Content-Type': 'application/json',
      if (authToken != null && authToken.isNotEmpty) 'Authorization': 'Bearer $authToken',
    };

    final result = await _executeWithRecovery(endpoint, 'PUT', headers, (baseUrl) async {
      final uri = Uri.parse('$baseUrl$endpoint');
      return await _client.put(
        uri,
        headers: headers,
        body: json.encode(body),
      );
    });

    if (result is Map<String, dynamic>) {
      return result;
    }
    return {};
  }

  Future<Map<String, dynamic>> patch(
    String endpoint, {
    Map<String, dynamic>? body,
    String? token,
  }) async {
    final authToken = token ?? await _storageService.getToken();
    final headers = <String, String>{
      'Content-Type': 'application/json',
      if (authToken != null && authToken.isNotEmpty) 'Authorization': 'Bearer $authToken',
    };

    final result = await _executeWithRecovery(endpoint, 'PATCH', headers, (baseUrl) async {
      final uri = Uri.parse('$baseUrl$endpoint');
      return await _client.patch(
        uri,
        headers: headers,
        body: body != null ? json.encode(body) : null,
      );
    });

    if (result is Map<String, dynamic>) {
      return result;
    }
    return {};
  }

  Future<dynamic> _executeWithRecovery(
    String endpoint,
    String method,
    Map<String, String> headers,
    Future<http.Response> Function(String baseUrl) requestFn,
  ) async {
    String activeUrl = await getBaseUrl();
    // First attempt with current active URL
    try {
      final response = await requestFn(activeUrl);
      if (kDebugMode) {
        debugPrint('[API] $method $endpoint (status: ${response.statusCode})');
      }
      return _handleResponse(response);
    } on SocketException catch (e) {
      if (kDebugMode) {
        debugPrint('[API] SocketException on $activeUrl for $method $endpoint: $e');
      }
    } on http.ClientException catch (e) {
      if (kDebugMode) {
        debugPrint('[API] ClientException on $activeUrl for $method $endpoint: $e');
      }
    }

    // Connection failed -> Invalidate current URL and attempt recovery across priorities
    final recoveredUrl = await _urlResolver.resolveUrl(forceRediscovery: true);
    if (recoveredUrl != null && recoveredUrl.isNotEmpty) {
      _currentBaseUrl = recoveredUrl;
      try {
        final response = await requestFn(recoveredUrl);
        return _handleResponse(response);
      } on SocketException catch (_) {
        throw const AppException(
          'Unable to connect to the restaurant server. Please check the Wi-Fi connection.',
          code: 'UNABLE_TO_CONNECT',
        );
      } on http.ClientException catch (_) {
        throw const AppException(
          'Unable to connect to the restaurant server. Please check the Wi-Fi connection.',
          code: 'UNABLE_TO_CONNECT',
        );
      } catch (e) {
        if (e is AppException) rethrow;
        throw AppException('An unexpected error occurred: $e');
      }
    }

    throw const AppException(
      'Unable to connect to the restaurant server. Please check the Wi-Fi connection.',
      code: 'NO_BACKEND_FOUND',
    );
  }

  @visibleForTesting
  dynamic handleResponse(http.Response response) => _handleResponse(response);

  dynamic _handleResponse(http.Response response) {
    dynamic body;
    if (response.body.isNotEmpty) {
      try {
        body = json.decode(response.body);
      } catch (_) {}
    }

    switch (response.statusCode) {
      case 200:
      case 201:
        return body ?? {};
      case 400:
        final msg = (body is Map)
            ? (body['message'] ?? body['error'] ?? 'Invalid request or validation error.')
            : 'Invalid request or validation error.';
        throw AppException(msg.toString(), code: '400', details: (body is Map) ? body : null);
      case 401:
        _storageService.clearSession();
        throw const AppException('Your session has expired. Please login again.', code: '401');
      case 403:
        final msg = (body is Map)
            ? (body['message'] ?? body['error'] ?? 'You do not have permission to perform this action.')
            : 'You do not have permission to perform this action.';
        throw AppException(msg.toString(), code: '403', details: (body is Map) ? body : null);
      case 404:
        throw const AppException('The requested order or table could not be found.', code: '404');
      case 409:
        final msg = (body is Map)
            ? (body['message'] ?? body['error'] ?? 'The order was changed by another user. Please refresh and try again.')
            : 'The order was changed by another user. Please refresh and try again.';
        throw AppException(msg.toString(), code: '409', details: (body is Map) ? body : null);
      case 500:
      default:
        final msg = (body is Map)
            ? (body['message'] ?? 'Something went wrong on the server. Please try again.')
            : 'Something went wrong on the server. Please try again.';
        throw AppException(msg.toString(), code: response.statusCode.toString());
    }
  }
}
