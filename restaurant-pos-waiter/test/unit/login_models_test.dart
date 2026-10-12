import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/shared/models/login_request.dart';
import 'package:restaurant_pos_waiter/shared/models/login_response.dart';
import 'package:restaurant_pos_waiter/shared/models/user_model.dart';

void main() {
  group('LoginRequest Model Tests', () {
    test('toJson produces expected Map', () {
      const request = LoginRequest(
        email: 'waiter@example.com',
        password: 'password123',
      );

      final json = request.toJson();

      expect(json['email'], 'waiter@example.com');
      expect(json['password'], 'password123');
    });
  });

  group('LoginResponse Model Tests', () {
    test('fromJson parses valid backend response correctly', () {
      final jsonMap = {
        'token': 'mock.jwt.token',
        'tokenType': 'Bearer',
        'expiresIn': 86400000,
        'userId': 10,
        'restaurantId': 1,
        'role': 'WAITER',
        'name': 'John Waiter',
        'email': 'waiter@example.com',
      };

      final response = LoginResponse.fromJson(jsonMap);

      expect(response.token, 'mock.jwt.token');
      expect(response.tokenType, 'Bearer');
      expect(response.expiresIn, 86400000);
      expect(response.userId, 10);
      expect(response.restaurantId, 1);
      expect(response.role, 'WAITER');
      expect(response.name, 'John Waiter');
      expect(response.email, 'waiter@example.com');
    });

    test('fromJson handles lowercase role conversion', () {
      final jsonMap = {
        'token': 'mock.jwt.token',
        'role': 'waiter',
      };

      final response = LoginResponse.fromJson(jsonMap);
      expect(response.role, 'WAITER');
    });
  });

  group('UserModel Tests', () {
    test('fromJson & toJson roundtrip works', () {
      final jsonMap = {
        'userId': 42,
        'restaurantId': 5,
        'role': 'WAITER',
        'name': 'Alice',
        'email': 'alice@example.com',
      };

      final user = UserModel.fromJson(jsonMap);
      expect(user.userId, 42);
      expect(user.role, 'WAITER');
      expect(user.name, 'Alice');

      final serialized = user.toJson();
      expect(serialized['userId'], 42);
      expect(serialized['role'], 'WAITER');
    });
  });
}
