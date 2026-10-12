import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/shared/models/table_model.dart';

void main() {
  group('TableModel Unit Tests', () {
    test('1. TableModel JSON parsing with integer and string values', () {
      final json = {
        'id': 1,
        'restaurantId': 2,
        'tableNumber': 'T-01',
        'capacity': 4,
        'status': 'AVAILABLE',
        'createdAt': '2026-09-24T10:00:00',
        'updatedAt': '2026-09-24T10:30:00',
      };

      final table = TableModel.fromJson(json);

      expect(table.id, 1);
      expect(table.restaurantId, 2);
      expect(table.tableNumber, 'T-01');
      expect(table.capacity, 4);
      expect(table.status, 'AVAILABLE');
      expect(table.createdAt, '2026-09-24T10:00:00');
      expect(table.updatedAt, '2026-09-24T10:30:00');
    });

    test('2. AVAILABLE table parsing and helper getters', () {
      final json = {
        'id': '10',
        'restaurantId': '2',
        'tableNumber': '10',
        'capacity': '6',
        'status': 'AVAILABLE',
      };

      final table = TableModel.fromJson(json);

      expect(table.id, 10);
      expect(table.parsedStatus, TableStatus.available);
      expect(table.isAvailable, isTrue);
      expect(table.isOccupied, isFalse);
      expect(table.isBilling, isFalse);
    });

    test('3. OCCUPIED table parsing and helper getters', () {
      final json = {
        'id': 5,
        'restaurantId': 2,
        'tableNumber': '05',
        'capacity': 2,
        'status': 'OCCUPIED',
      };

      final table = TableModel.fromJson(json);

      expect(table.parsedStatus, TableStatus.occupied);
      expect(table.isAvailable, isFalse);
      expect(table.isOccupied, isTrue);
      expect(table.isBilling, isFalse);
    });

    test('4. BILLING table parsing and helper getters', () {
      final json = {
        'id': 8,
        'restaurantId': 2,
        'tableNumber': '08',
        'capacity': 8,
        'status': 'BILLING',
      };

      final table = TableModel.fromJson(json);

      expect(table.parsedStatus, TableStatus.billing);
      expect(table.isAvailable, isFalse);
      expect(table.isOccupied, isFalse);
      expect(table.isBilling, isTrue);
    });

    test('TableModel toJson converts model correctly back to Map', () {
      const table = TableModel(
        id: 3,
        restaurantId: 2,
        tableNumber: '03',
        capacity: 4,
        status: 'AVAILABLE',
      );

      final json = table.toJson();

      expect(json['id'], 3);
      expect(json['restaurantId'], 2);
      expect(json['tableNumber'], '03');
      expect(json['capacity'], 4);
      expect(json['status'], 'AVAILABLE');
    });
  });
}
