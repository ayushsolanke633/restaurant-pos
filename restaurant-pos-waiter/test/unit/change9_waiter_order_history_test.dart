import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/core/network/api_client.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/auth_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/order_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/waiter_order_history_page.dart';
import 'package:restaurant_pos_waiter/shared/models/order_model.dart';
import 'package:restaurant_pos_waiter/shared/models/user_model.dart';

class MockApiClientForHistory extends ApiClient {
  String? lastPath;
  Map<String, dynamic>? lastBody;
  dynamic mockResponse;
  bool shouldThrow = false;
  AppException? exceptionToThrow;

  @override
  Future<dynamic> get(String path, {String? token}) async {
    lastPath = path;
    if (shouldThrow) {
      throw exceptionToThrow ?? const AppException('Network Error');
    }
    return mockResponse ?? [];
  }
}

class MockAuthServiceForHistory extends AuthService {
  MockAuthServiceForHistory({super.apiClient});
  @override
  Future<UserModel?> getStoredUser() async => UserModel(
        userId: 101,
        restaurantId: 1,
        role: 'WAITER',
        name: 'Waiter Senior',
        email: 'waiter.senior@example.com',
      );
}

void main() {
  group('Change 9 — Waiter Order History Unit & Model Tests', () {
    test('1. Order history model parses COMPLETED order correctly', () {
      final json = {
        'id': 1001,
        'restaurantId': 1,
        'tableId': 5,
        'tableNumber': 12,
        'waiterId': 101,
        'waiterName': 'Waiter Senior',
        'orderType': 'DINE_IN',
        'status': 'COMPLETED',
        'subtotal': 300.0,
        'taxAmount': 0.0,
        'discountAmount': 0.0,
        'totalAmount': 300.0,
        'createdAt': '2026-10-06T12:00:00Z',
        'updatedAt': '2026-10-06T12:30:00Z',
        'items': [
          {
            'id': 1,
            'menuItemId': 50,
            'menuItemName': 'Burger',
            'quantity': 2,
            'unitPrice': 150.0,
            'notes': 'No onion',
            'subtotal': 300.0,
          }
        ]
      };

      final order = OrderModel.fromJson(json);

      expect(order.id, equals(1001));
      expect(order.tableNumber, equals(12));
      expect(order.isCompleted, isTrue);
      expect(order.isCancelled, isFalse);
      expect(order.status, equals('COMPLETED'));
      expect(order.totalAmount, equals(300.0));
      expect(order.updatedAt, equals('2026-10-06T12:30:00Z'));
    });

    test('2. Order history model parses CANCELLED order correctly', () {
      final json = {
        'id': 1002,
        'restaurantId': 1,
        'tableId': 3,
        'tableNumber': 7,
        'waiterId': 101,
        'waiterName': 'Waiter Senior',
        'orderType': 'DINE_IN',
        'status': 'CANCELLED',
        'subtotal': 150.0,
        'taxAmount': 0.0,
        'discountAmount': 0.0,
        'totalAmount': 150.0,
        'createdAt': '2026-10-06T11:00:00Z',
        'items': []
      };

      final order = OrderModel.fromJson(json);

      expect(order.id, equals(1002));
      expect(order.tableNumber, equals(7));
      expect(order.isCancelled, isTrue);
      expect(order.isCompleted, isFalse);
      expect(order.status, equals('CANCELLED'));
    });

    test('3. tableNumber is parsed correctly', () {
      final json = {
        'id': 1003,
        'tableId': 10,
        'tableNumber': 99,
        'orderType': 'DINE_IN',
        'status': 'COMPLETED',
        'subtotal': 100.0,
        'taxAmount': 0.0,
        'discountAmount': 0.0,
        'totalAmount': 100.0,
        'items': []
      };

      final order = OrderModel.fromJson(json);
      expect(order.tableNumber, equals(99));
    });

    test('4. Order items are parsed correctly', () {
      final json = {
        'id': 1004,
        'orderType': 'DINE_IN',
        'status': 'COMPLETED',
        'subtotal': 250.0,
        'taxAmount': 0.0,
        'discountAmount': 0.0,
        'totalAmount': 250.0,
        'items': [
          {
            'id': 10,
            'menuItemId': 101,
            'menuItemName': 'Pizza',
            'quantity': 1,
            'unitPrice': 250.0,
            'notes': 'Extra cheese',
            'subtotal': 250.0,
          }
        ]
      };

      final order = OrderModel.fromJson(json);
      expect(order.items.length, equals(1));
      expect(order.items[0].menuItemName, equals('Pizza'));
      expect(order.items[0].quantity, equals(1));
      expect(order.items[0].notes, equals('Extra cheese'));
    });

    test('5. Historical item prices are preserved from OrderItemModel.unitPrice', () {
      final json = {
        'id': 1005,
        'orderType': 'DINE_IN',
        'status': 'COMPLETED',
        'subtotal': 120.0,
        'taxAmount': 0.0,
        'discountAmount': 0.0,
        'totalAmount': 120.0,
        'items': [
          {
            'id': 11,
            'menuItemId': 50,
            'menuItemName': 'Burger',
            'quantity': 1,
            'unitPrice': 120.0, // Historical price (current price in menu might be 200)
            'subtotal': 120.0,
          }
        ]
      };

      final order = OrderModel.fromJson(json);
      expect(order.items[0].unitPrice, equals(120.0));
    });

    test('6. History API calls correct endpoint GET /api/orders/waiter/history for ALL', () async {
      final mockClient = MockApiClientForHistory();
      mockClient.mockResponse = [];
      final service = OrderService(apiClient: mockClient);

      await service.getWaiterOrderHistory();

      expect(mockClient.lastPath, equals('/api/orders/waiter/history'));
    });

    test('7. COMPLETED filter sends correct query ?status=COMPLETED', () async {
      final mockClient = MockApiClientForHistory();
      mockClient.mockResponse = [];
      final service = OrderService(apiClient: mockClient);

      await service.getWaiterOrderHistory(status: 'COMPLETED');

      expect(mockClient.lastPath, equals('/api/orders/waiter/history?status=COMPLETED'));
    });

    test('8. CANCELLED filter sends correct query ?status=CANCELLED', () async {
      final mockClient = MockApiClientForHistory();
      mockClient.mockResponse = [];
      final service = OrderService(apiClient: mockClient);

      await service.getWaiterOrderHistory(status: 'CANCELLED');

      expect(mockClient.lastPath, equals('/api/orders/waiter/history?status=CANCELLED'));
    });

    test('9. No waiterId parameter is sent in request URL', () async {
      final mockClient = MockApiClientForHistory();
      mockClient.mockResponse = [];
      final service = OrderService(apiClient: mockClient);

      await service.getWaiterOrderHistory();

      expect(mockClient.lastPath?.contains('waiterId'), isFalse);
    });
  });

  group('Change 9 — Waiter Order History UI & Interaction Tests', () {
    testWidgets('10. Empty history displays correct empty state text', (tester) async {
      final mockClient = MockApiClientForHistory();
      mockClient.mockResponse = [];
      final orderService = OrderService(apiClient: mockClient);
      final authService = MockAuthServiceForHistory(apiClient: mockClient);

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterOrderHistoryPage(
            orderService: orderService,
            authService: authService,
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('No order history yet'), findsOneWidget);

      // Select COMPLETED filter
      await tester.tap(find.text('Completed'));
      await tester.pumpAndSettle();

      expect(find.text('No completed orders'), findsOneWidget);

      // Select CANCELLED filter
      await tester.tap(find.text('Cancelled'));
      await tester.pumpAndSettle();

      expect(find.text('No cancelled orders'), findsOneWidget);
    });

    testWidgets('11. Loading state works correctly', (tester) async {
      final mockClient = MockApiClientForHistory();
      mockClient.mockResponse = [];
      final orderService = OrderService(apiClient: mockClient);
      final authService = MockAuthServiceForHistory(apiClient: mockClient);

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterOrderHistoryPage(
            orderService: orderService,
            authService: authService,
          ),
        ),
      );

      // Initial state before pumpAndSettle should show loading indicator
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Loading order history...'), findsOneWidget);

      await tester.pumpAndSettle();

      expect(find.byType(CircularProgressIndicator), findsNothing);
    });

    testWidgets('12. API error state works and allows retry', (tester) async {
      final mockClient = MockApiClientForHistory();
      mockClient.shouldThrow = true;
      mockClient.exceptionToThrow = const AppException('Unable to connect to server');

      final orderService = OrderService(apiClient: mockClient);
      final authService = MockAuthServiceForHistory(apiClient: mockClient);

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterOrderHistoryPage(
            orderService: orderService,
            authService: authService,
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Unable to connect to server'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);

      // Fix error and tap retry
      mockClient.shouldThrow = false;
      mockClient.mockResponse = [];

      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();

      expect(find.text('No order history yet'), findsOneWidget);
    });

    testWidgets('13. Order history lists orders and tapping opens read-only details modal', (tester) async {
      final mockClient = MockApiClientForHistory();
      mockClient.mockResponse = [
        {
          'id': 5001,
          'restaurantId': 1,
          'tableId': 10,
          'tableNumber': 4,
          'waiterId': 101,
          'waiterName': 'Waiter Senior',
          'orderType': 'DINE_IN',
          'status': 'COMPLETED',
          'subtotal': 180.0,
          'taxAmount': 0.0,
          'discountAmount': 0.0,
          'totalAmount': 180.0,
          'createdAt': '2026-10-06T10:00:00Z',
          'updatedAt': '2026-10-06T10:30:00Z',
          'items': [
            {
              'id': 1,
              'menuItemId': 50,
              'menuItemName': 'Pasta',
              'quantity': 1,
              'unitPrice': 180.0,
              'notes': 'Extra sauce',
              'subtotal': 180.0,
            }
          ]
        }
      ];

      final orderService = OrderService(apiClient: mockClient);
      final authService = MockAuthServiceForHistory(apiClient: mockClient);

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterOrderHistoryPage(
            orderService: orderService,
            authService: authService,
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('#5001'), findsOneWidget);
      expect(find.text('Table #4'), findsOneWidget);
      expect(find.text('COMPLETED'), findsOneWidget);
      expect(find.text('₹180.00'), findsWidgets);

      // Tap order card
      await tester.tap(find.text('#5001'));
      await tester.pumpAndSettle();

      // Details Modal opens
      expect(find.text('Order #5001'), findsOneWidget);
      expect(find.text('Pasta'), findsOneWidget);
      expect(find.text('Note: Extra sauce'), findsOneWidget);
      expect(find.text('1 x ₹180.00 = ₹180.00'), findsOneWidget);
    });

    testWidgets('14. Historical order details modal is strictly read-only with no action buttons', (tester) async {
      final mockClient = MockApiClientForHistory();
      mockClient.mockResponse = [
        {
          'id': 6001,
          'tableNumber': 2,
          'orderType': 'DINE_IN',
          'status': 'COMPLETED',
          'subtotal': 200.0,
          'taxAmount': 0.0,
          'discountAmount': 0.0,
          'totalAmount': 200.0,
          'items': [
            {
              'id': 2,
              'menuItemId': 10,
              'menuItemName': 'Soup',
              'quantity': 2,
              'unitPrice': 100.0,
              'subtotal': 200.0,
            }
          ]
        }
      ];

      final orderService = OrderService(apiClient: mockClient);
      final authService = MockAuthServiceForHistory(apiClient: mockClient);

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterOrderHistoryPage(
            orderService: orderService,
            authService: authService,
          ),
        ),
      );

      await tester.pumpAndSettle();
      await tester.tap(find.text('#6001'));
      await tester.pumpAndSettle();

      // Verify NO action buttons are present
      expect(find.text('ADD ITEMS'), findsNothing);
      expect(find.text('Add Items'), findsNothing);
      expect(find.text('Cancel Order'), findsNothing);
      expect(find.text('Request Bill'), findsNothing);
      expect(find.text('Generate Bill'), findsNothing);
      expect(find.text('Payment'), findsNothing);
      expect(find.text('Complete Order'), findsNothing);
      expect(find.text('Change Status'), findsNothing);

      // Only Close button should be present
      expect(find.text('Close'), findsOneWidget);
    });

    testWidgets('15. Refresh reloads history from server', (tester) async {
      final mockClient = MockApiClientForHistory();
      mockClient.mockResponse = [];

      final orderService = OrderService(apiClient: mockClient);
      final authService = MockAuthServiceForHistory(apiClient: mockClient);

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterOrderHistoryPage(
            orderService: orderService,
            authService: authService,
          ),
        ),
      );

      await tester.pumpAndSettle();
      expect(mockClient.lastPath, equals('/api/orders/waiter/history'));

      // Tap Refresh in AppBar
      mockClient.lastPath = null;
      await tester.tap(find.byIcon(Icons.refresh));
      await tester.pumpAndSettle();

      expect(mockClient.lastPath, equals('/api/orders/waiter/history'));
    });
  });
}
