import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/utils/jwt_decoder.dart';

void main() {
  group('JwtDecoder Utility Tests', () {
    test('isExpired returns false for unexpired token', () {
      // Header: {"alg":"HS256","typ":"JWT"}
      // Payload: {"sub":"waiter@example.com","exp":4102444800} (Year 2100)
      const token =
          'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ3YWl0ZXJAZXhhbXBsZS5jb20iLCJleHAiOjQxMDI0NDQ4MDB9.signature';

      expect(JwtDecoder.isExpired(token), isFalse);
    });

    test('isExpired returns true for expired token', () {
      // Header: {"alg":"HS256","typ":"JWT"}
      // Payload: {"sub":"waiter@example.com","exp":1577836800} (Year 2020)
      const token =
          'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ3YWl0ZXJAZXhhbXBsZS5jb20iLCJleHAiOjE1Nzc4MzY4MDB9.signature';

      expect(JwtDecoder.isExpired(token), isTrue);
    });

    test('isExpired returns true for invalid JWT token format', () {
      expect(JwtDecoder.isExpired('invalid_token_string'), isTrue);
      expect(JwtDecoder.isExpired(''), isTrue);
    });

    test('decodePayload returns correct claims', () {
      const token =
          'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ3YWl0ZXJAZXhhbXBsZS5jb20iLCJleHAiOjQxMDI0NDQ4MDB9.signature';

      final payload = JwtDecoder.decodePayload(token);
      expect(payload, isNotNull);
      expect(payload!['sub'], 'waiter@example.com');
      expect(payload['exp'], 4102444800);
    });
  });
}
