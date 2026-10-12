import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:restaurant_pos_waiter/core/network/backend_url_resolver.dart';
import 'package:restaurant_pos_waiter/core/storage/secure_storage_service.dart';

class MemoryStorageService extends SecureStorageService {
  final Map<String, String> _storage = {};

  @override
  Future<void> saveManualServerUrl(String url) async {
    _storage['manual_server_url'] = url;
  }

  @override
  Future<String?> getManualServerUrl() async {
    return _storage['manual_server_url'];
  }

  @override
  Future<void> clearManualServerUrl() async {
    _storage.remove('manual_server_url');
  }

  @override
  Future<void> saveCachedServerUrl(String url) async {
    _storage['cached_server_url'] = url;
  }

  @override
  Future<String?> getCachedServerUrl() async {
    return _storage['cached_server_url'];
  }

  @override
  Future<void> clearCachedServerUrl() async {
    _storage.remove('cached_server_url');
  }
}

void main() {
  group('BackendUrlResolver Tests', () {
    late MemoryStorageService storage;

    setUp(() {
      storage = MemoryStorageService();
    });

    test('1. Manual URL priority takes precedence when reachable', () async {
      final client = MockClient((request) async {
        if (request.url.toString() == 'http://192.168.1.100:8080/api/health') {
          return http.Response(
            json.encode({'service': 'restaurant-pos-backend', 'status': 'UP'}),
            200,
          );
        }
        return http.Response('Not Found', 404);
      });

      await storage.saveManualServerUrl('http://192.168.1.100:8080');

      final resolver = BackendUrlResolver(
        storageService: storage,
        client: client,
      );

      final url = await resolver.resolveUrl();
      expect(url, 'http://192.168.1.100:8080');
    });

    test('2. Cached URL reuse when manual URL is absent', () async {
      final client = MockClient((request) async {
        if (request.url.toString() == 'http://192.168.1.200:8080/api/health') {
          return http.Response(
            json.encode({'service': 'restaurant-pos-backend', 'status': 'UP'}),
            200,
          );
        }
        return http.Response('Not Found', 404);
      });

      await storage.saveCachedServerUrl('http://192.168.1.200:8080');

      final resolver = BackendUrlResolver(
        storageService: storage,
        client: client,
      );

      final url = await resolver.resolveUrl();
      expect(url, 'http://192.168.1.200:8080');
    });

    test('3. Dead cached URL invalidation and fallback', () async {
      final client = MockClient((request) async {
        if (request.url.toString() == 'http://restaurant-pos.local:8080/api/health') {
          return http.Response(
            json.encode({'service': 'restaurant-pos-backend', 'status': 'UP'}),
            200,
          );
        }
        return http.Response('Connection refused', 500);
      });

      // Dead cached URL
      await storage.saveCachedServerUrl('http://192.168.1.99:8080');

      final resolver = BackendUrlResolver(
        storageService: storage,
        client: client,
      );

      final url = await resolver.resolveUrl();

      // Dead cached URL should be invalidated
      final cachedAfter = await storage.getCachedServerUrl();
      expect(cachedAfter, 'http://restaurant-pos.local:8080');
      expect(url, 'http://restaurant-pos.local:8080');
    });

    test('4. Hostname fallback (restaurant-pos.local:8080)', () async {
      final client = MockClient((request) async {
        if (request.url.toString() == 'http://restaurant-pos.local:8080/api/health') {
          return http.Response(
            json.encode({'service': 'restaurant-pos-backend', 'status': 'UP'}),
            200,
          );
        }
        return http.Response('Not Found', 404);
      });

      final resolver = BackendUrlResolver(
        storageService: storage,
        client: client,
      );

      final url = await resolver.resolveUrl();
      expect(url, 'http://restaurant-pos.local:8080');
    });

    test('5 & 6. Discovery fallback and successful discovery persistence', () async {
      final client = MockClient((request) async {
        if (request.url.toString() == 'http://192.168.74.55:8080/api/health') {
          return http.Response(
            json.encode({'service': 'restaurant-pos-backend', 'status': 'UP'}),
            200,
          );
        }
        return http.Response('Not Found', 404);
      });

      final resolver = BackendUrlResolver(
        storageService: storage,
        client: client,
      );

      final url = await resolver.resolveUrl();
      expect(url, 'http://192.168.74.55:8080');

      final savedCache = await storage.getCachedServerUrl();
      expect(savedCache, 'http://192.168.74.55:8080');
    });

    test('7. Discovery failure handling returns null', () async {
      final client = MockClient((request) async {
        return http.Response('Server error', 500);
      });

      final resolver = BackendUrlResolver(
        storageService: storage,
        client: client,
      );

      final url = await resolver.resolveUrl(forceRediscovery: true);
      expect(url, null);
    });

    test('URL Normalization test', () {
      final resolver = BackendUrlResolver();
      expect(resolver.normalizeUrl('192.168.1.50'), 'http://192.168.1.50:8080');
      expect(resolver.normalizeUrl('192.168.1.50:8080/'), 'http://192.168.1.50:8080');
      expect(resolver.normalizeUrl('http://restaurant-pos.local:8080/'), 'http://restaurant-pos.local:8080');
    });
  });
}
