import 'dart:convert';

class JwtDecoder {
  JwtDecoder._();

  /// Decodes the payload portion of a JWT token into a Map.
  static Map<String, dynamic>? decodePayload(String token) {
    try {
      final parts = token.split('.');
      if (parts.length != 3) {
        return null;
      }
      final payload = parts[1];
      final normalized = base64Url.normalize(payload);
      final resp = utf8.decode(base64Url.decode(normalized));
      return json.decode(resp) as Map<String, dynamic>;
    } catch (_) {
      return null;
    }
  }

  /// Checks whether a JWT token is expired.
  /// Returns `true` if token is expired or invalid, `false` otherwise.
  static bool isExpired(String token) {
    final payload = decodePayload(token);
    if (payload == null) {
      return true;
    }

    if (payload.containsKey('exp')) {
      final exp = payload['exp'];
      if (exp is int) {
        final expirationDate = DateTime.fromMillisecondsSinceEpoch(exp * 1000);
        return DateTime.now().isAfter(expirationDate);
      }
    }
    return false;
  }
}
