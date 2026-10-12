import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../constants/api_constants.dart';
import '../storage/secure_storage_service.dart';

class BackendUrlResolver {
  final SecureStorageService _storageService;
  final http.Client _client;

  static const String defaultHostname = 'http://restaurant-pos.local:8080';
  static const String defaultEmulatorUrl = 'http://10.0.2.2:8080';
  static const String defaultLocalhostUrl = 'http://localhost:8080';
  static const String defaultLanIpUrl = 'http://192.168.74.55:8080';

  BackendUrlResolver({
    SecureStorageService? storageService,
    http.Client? client,
  })  : _storageService = storageService ?? SecureStorageService(),
        _client = client ?? http.Client();

  /// Normalizes any raw IP / hostname into a standard URL without trailing slash.
  String normalizeUrl(String rawUrl) {
    var trimmed = rawUrl.trim();
    if (trimmed.isEmpty) return '';

    if (!trimmed.startsWith('http://') && !trimmed.startsWith('https://')) {
      if (kReleaseMode) {
        trimmed = 'https://$trimmed';
      } else {
        trimmed = 'http://$trimmed';
      }
    }

    if (trimmed.endsWith('/')) {
      trimmed = trimmed.substring(0, trimmed.length - 1);
    }

    // Ensure port 8080 if no port specified on plain http
    final uri = Uri.parse(trimmed);
    if (!uri.hasPort && uri.scheme == 'http') {
      trimmed = '${uri.scheme}://${uri.host}:8080';
    }

    return trimmed;
  }

  /// Verifies whether a given backend URL responds to GET /api/health.
  Future<bool> checkHealth(
    String baseUrl, {
    Duration timeout = const Duration(seconds: 2),
  }) async {
    final cleanUrl = normalizeUrl(baseUrl);
    if (cleanUrl.isEmpty) return false;

    try {
      final uri = Uri.parse('$cleanUrl/api/health');
      final response = await _client.get(uri).timeout(timeout);
      if (response.statusCode == 200) {
        final body = json.decode(response.body);
        if (body is Map && body['service'] == 'restaurant-pos-backend') {
          return true;
        }
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  /// Resolves backend URL according to strict priority order:
  /// In Production Release Mode:
  /// 1. Manually configured HTTPS URL in secure storage, if healthy.
  /// 2. Cached URL, if healthy.
  /// 3. Configured production HTTPS backend (ApiConstants.baseUrl).
  ///
  /// In Development/Debug Mode:
  /// 1. Manually configured server URL, if valid & reachable.
  /// 2. Previously discovered/cached server URL, if valid & reachable.
  /// 3. Local hostname: http://restaurant-pos.local:8080
  /// 4. Automatic LAN discovery (subnet probing + emulator/localhost/defaults).
  /// 5. null if all fail.
  Future<String?> resolveUrl({bool forceRediscovery = false}) async {
    // 1. Manually configured server URL
    final manualUrl = await _storageService.getManualServerUrl();
    if (manualUrl != null && manualUrl.isNotEmpty) {
      final normalized = normalizeUrl(manualUrl);
      if (await checkHealth(normalized)) {
        return normalized;
      }
    }

    // 2. Previously discovered / cached URL
    if (!forceRediscovery) {
      final cachedUrl = await _storageService.getCachedServerUrl();
      if (cachedUrl != null && cachedUrl.isNotEmpty) {
        final normalized = normalizeUrl(cachedUrl);
        if (await checkHealth(normalized)) {
          return normalized;
        } else {
          // Dead cached URL -> Invalidate
          await _storageService.clearCachedServerUrl();
        }
      }
    } else {
      // Force rediscovery invalidates cached URL
      await _storageService.clearCachedServerUrl();
    }

    // In production release builds, do not perform silent LAN probing.
    if (kReleaseMode) {
      final prodUrl = ApiConstants.baseUrl;
      if (await checkHealth(prodUrl)) {
        await _storageService.saveCachedServerUrl(prodUrl);
        return prodUrl;
      }
      return prodUrl;
    }

    // 3. Local Hostname: http://restaurant-pos.local:8080
    if (await checkHealth(defaultHostname)) {
      await _storageService.saveCachedServerUrl(defaultHostname);
      return defaultHostname;
    }

    // 4. Automatic LAN Discovery (active in development/staging mode)
    final discoveredUrl = await _discoverLanBackend();
    if (discoveredUrl != null) {
      await _storageService.saveCachedServerUrl(discoveredUrl);
      return discoveredUrl;
    }

    // 5. All automatic methods failed
    return null;
  }

  /// Performs controlled parallel probing of candidate local network IPs.
  Future<String?> _discoverLanBackend() async {
    final candidateUrls = <String>{};

    // Add high priority developer / emulator defaults
    if (!kIsWeb && Platform.isAndroid) {
      candidateUrls.add(defaultEmulatorUrl);
    }
    candidateUrls.add(defaultLocalhostUrl);
    candidateUrls.add(defaultLanIpUrl);

    // Discover active subnets from device network interfaces
    List<String> subnetPrefixes = await _getActiveSubnetPrefixes();

    // Fallback subnets if none detected via interfaces
    if (subnetPrefixes.isEmpty) {
      subnetPrefixes = ['192.168.74', '192.168.1', '192.168.0', '192.168.43', '10.0.0'];
    }

    for (final prefix in subnetPrefixes) {
      for (int i = 1; i <= 254; i++) {
        candidateUrls.add('http://$prefix.$i:8080');
      }
    }

    // Probe candidates with bounded concurrency (30 concurrent checks)
    final candidatesList = candidateUrls.toList();
    const batchSize = 30;

    for (int i = 0; i < candidatesList.length; i += batchSize) {
      final end = (i + batchSize < candidatesList.length) ? i + batchSize : candidatesList.length;
      final batch = candidatesList.sublist(i, end);

      final results = await Future.wait(
        batch.map((url) async {
          final isHealthy = await checkHealth(url, timeout: const Duration(milliseconds: 1500));
          return isHealthy ? url : null;
        }),
      );

      final found = results.firstWhere((url) => url != null, orElse: () => null);
      if (found != null) {
        return normalizeUrl(found);
      }
    }

    return null;
  }

  /// Retrieves IPv4 subnet prefixes (e.g. "192.168.1") from active network interfaces.
  Future<List<String>> _getActiveSubnetPrefixes() async {
    final prefixes = <String>{};
    try {
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
        includeLinkLocal: false,
        includeLoopback: false,
      );

      for (final interface in interfaces) {
        for (final addr in interface.addresses) {
          final ip = addr.address;
          if (ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.')) {
            final parts = ip.split('.');
            if (parts.length == 4) {
              prefixes.add('${parts[0]}.${parts[1]}.${parts[2]}');
            }
          }
        }
      }
    } catch (_) {}

    return prefixes.toList();
  }
}
