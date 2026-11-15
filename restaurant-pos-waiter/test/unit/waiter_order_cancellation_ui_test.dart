import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/network/websocket_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/auth_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/order_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/active_order_detail_page.dart';
import 'package:restaurant_pos_waiter/shared/models/order_model.dart';
import 'package:restaurant_pos_waiter/shared/models/table_model.dart';
import 'package:restaurant_pos_waiter/shared/models/user_model.dart';

class MockAuthServiceForCancellation extends Fake implements AuthService {
  final UserModel? userToReturn;
  MockAuthServiceForCancellation({this.userToReturn});

  UserModel? get currentUser => userToReturn;

  @override
  Future<UserModel?> getStoredUser() async => userToReturn;
}

class MockOrderServiceForCancellation extends Fake implements OrderService {
  OrderModel? activeOrderToReturn;

  MockOrderServiceForCancellation({this.activeOrderToReturn});

  @override
  Future<OrderModel?> getActiveOrderByTable(int tableId) async {
    return activeOrderToReturn;
  }
}

class MockWebSocketServiceForCancellation extends Fake implements WebSocketService {
  @override
  Stream<Map<String, dynamic>> get orderEventsStream => const Stream.empty();
}

void main() {
  const currentUser = UserModel(userId: 1, role: 'WAITER', name: 'Waiter 1', email: 'waiter1@pos.com');

  OrderModel createOrder({
    required int id,
    required String status,
    int waiterId = 1,
  }) {
    return OrderModel(
      id: id,
      tableId: 5,
      waiterId: waiterId,
      orderType: 'DINE_IN',
      status: status,
      subtotal: 100.0,
      taxAmount: 0.0,
      discountAmount: 0.0,
      totalAmount: 100.0,
      items: [
        OrderItemModel(
          id: 1,
          menuItemId: 10,
          menuItemName: 'Pizza',
          quantity: 1,
          unitPrice: 100.0,
          subtotal: 100.0,
        ),
      ],
    );
  }

  group('Waiter App: Admin-Only Order Cancellation (No Cancellation for Waiters)', () {
    testWidgets('1. Waiter does NOT see CANCEL ORDER button for NEW order (sees MODIFY ORDER and ADD ITEMS)', (tester) async {
      final mockAuth = MockAuthServiceForCancellation(userToReturn: currentUser);
      final mockOrder = createOrder(id: 101, status: 'NEW');
      final mockOrderService = MockOrderServiceForCancellation(activeOrderToReturn: mockOrder);

      await tester.pumpWidget(
        MaterialApp(
          home: ActiveOrderDetailPage(
            table: const TableModel(id: 5, restaurantId: 1, capacity: 4, tableNumber: '5', status: 'OCCUPIED'),
            orderService: mockOrderService,
            authService: mockAuth,
            webSocketService: MockWebSocketServiceForCancellation(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // CANCEL ORDER button MUST NOT be present
      expect(find.text('CANCEL ORDER'), findsNothing);

      // Permitted waiter actions MUST still be present
      expect(find.text('MODIFY ORDER'), findsOneWidget);
      expect(find.text('ADD ITEMS'), findsOneWidget);
      expect(find.text('BACK TO TABLES'), findsOneWidget);
    });

    testWidgets('2. Waiter does NOT see CANCEL ORDER button for ACCEPTED order (sees ADD ITEMS and BACK TO TABLES)', (tester) async {
      final mockAuth = MockAuthServiceForCancellation(userToReturn: currentUser);
      final mockOrder = createOrder(id: 102, status: 'ACCEPTED');
      final mockOrderService = MockOrderServiceForCancellation(activeOrderToReturn: mockOrder);

      await tester.pumpWidget(
        MaterialApp(
          home: ActiveOrderDetailPage(
            table: const TableModel(id: 5, restaurantId: 1, capacity: 4, tableNumber: '5', status: 'OCCUPIED'),
            orderService: mockOrderService,
            authService: mockAuth,
            webSocketService: MockWebSocketServiceForCancellation(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('CANCEL ORDER'), findsNothing);
      expect(find.text('ADD ITEMS'), findsOneWidget);
      expect(find.text('BACK TO TABLES'), findsOneWidget);
    });

    testWidgets('3. Waiter does NOT see CANCEL ORDER button for PREPARING or READY order', (tester) async {
      final mockAuth = MockAuthServiceForCancellation(userToReturn: currentUser);
      final mockOrder = createOrder(id: 103, status: 'READY');
      final mockOrderService = MockOrderServiceForCancellation(activeOrderToReturn: mockOrder);

      await tester.pumpWidget(
        MaterialApp(
          home: ActiveOrderDetailPage(
            table: const TableModel(id: 5, restaurantId: 1, capacity: 4, tableNumber: '5', status: 'OCCUPIED'),
            orderService: mockOrderService,
            authService: mockAuth,
            webSocketService: MockWebSocketServiceForCancellation(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('CANCEL ORDER'), findsNothing);
      expect(find.text('ADD ITEMS'), findsOneWidget);
      expect(find.text('BACK TO TABLES'), findsOneWidget);
    });

    testWidgets('4. CANCEL ORDER button is NOT visible for other waiter orders', (tester) async {
      final mockAuth = MockAuthServiceForCancellation(userToReturn: currentUser);
      final mockOrder = createOrder(id: 104, status: 'NEW', waiterId: 2); // Different waiter!
      final mockOrderService = MockOrderServiceForCancellation(activeOrderToReturn: mockOrder);

      await tester.pumpWidget(
        MaterialApp(
          home: ActiveOrderDetailPage(
            table: const TableModel(id: 5, restaurantId: 1, capacity: 4, tableNumber: '5', status: 'OCCUPIED'),
            orderService: mockOrderService,
            authService: mockAuth,
            webSocketService: MockWebSocketServiceForCancellation(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('CANCEL ORDER'), findsNothing);
      expect(find.text('MODIFY ORDER'), findsNothing);
      expect(find.text('BACK TO TABLES'), findsOneWidget);
    });

    testWidgets('5. CANCEL ORDER button is NOT visible for COMPLETED or CANCELLED order', (tester) async {
      final mockAuth = MockAuthServiceForCancellation(userToReturn: currentUser);
      final mockOrder = createOrder(id: 105, status: 'COMPLETED');
      final mockOrderService = MockOrderServiceForCancellation(activeOrderToReturn: mockOrder);

      await tester.pumpWidget(
        MaterialApp(
          home: ActiveOrderDetailPage(
            table: const TableModel(id: 5, restaurantId: 1, capacity: 4, tableNumber: '5', status: 'OCCUPIED'),
            orderService: mockOrderService,
            authService: mockAuth,
            webSocketService: MockWebSocketServiceForCancellation(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('CANCEL ORDER'), findsNothing);
      expect(find.text('BACK TO TABLES'), findsOneWidget);
    });
  });
}
