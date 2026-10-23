import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
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
  bool cancelOrderCalled = false;
  int? cancelledOrderId;
  AppException? exceptionToThrowOnCancel;

  MockOrderServiceForCancellation({this.activeOrderToReturn, this.exceptionToThrowOnCancel});

  @override
  Future<OrderModel?> getActiveOrderByTable(int tableId) async {
    return activeOrderToReturn;
  }

  @override
  Future<OrderModel> cancelOrder(int orderId) async {
    cancelOrderCalled = true;
    cancelledOrderId = orderId;
    if (exceptionToThrowOnCancel != null) {
      throw exceptionToThrowOnCancel!;
    }
    return activeOrderToReturn!;
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

  group('Waiter Order Cancellation UI Tests', () {
    testWidgets('1. Waiter sees CANCEL ORDER button for NEW order', (tester) async {
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

      expect(find.text('CANCEL ORDER'), findsOneWidget);
    });

    testWidgets('2. Waiter sees CANCEL ORDER button for ACCEPTED active unbilled order (KDS disabled mode)', (tester) async {
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

      expect(find.text('CANCEL ORDER'), findsOneWidget);
    });

    testWidgets('3. Waiter sees CANCEL ORDER button for PREPARING / READY order', (tester) async {
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

      expect(find.text('CANCEL ORDER'), findsOneWidget);
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
    });

    testWidgets('6. Successful cancellation shows dialog, calls API, and displays success message', (tester) async {
      final mockAuth = MockAuthServiceForCancellation(userToReturn: currentUser);
      final mockOrder = createOrder(id: 106, status: 'NEW');
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

      // Tap Cancel Order
      await tester.tap(find.text('CANCEL ORDER'));
      await tester.pumpAndSettle();

      // Verify confirmation dialog
      expect(find.text('Cancel Order?'), findsOneWidget);
      expect(find.text('Are you sure you want to cancel Order #106? Table 5 will become AVAILABLE.'), findsOneWidget);

      // Tap Yes, Cancel Order
      await tester.tap(find.text('Yes, Cancel Order'));
      await tester.pumpAndSettle();

      expect(mockOrderService.cancelOrderCalled, isTrue);
      expect(mockOrderService.cancelledOrderId, 106);
      expect(find.text('Order #106 cancelled successfully. Table released.'), findsOneWidget);
    });

    testWidgets('7. Backend conflict rejection displays exact server message to waiter', (tester) async {
      final mockAuth = MockAuthServiceForCancellation(userToReturn: currentUser);
      final mockOrder = createOrder(id: 107, status: 'ACCEPTED');
      final mockOrderService = MockOrderServiceForCancellation(
        activeOrderToReturn: mockOrder,
        exceptionToThrowOnCancel: const AppException(
          'Cannot cancel order: bill has already been generated',
          code: '409',
        ),
      );

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

      await tester.tap(find.text('CANCEL ORDER'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Yes, Cancel Order'));
      await tester.pumpAndSettle();

      expect(mockOrderService.cancelOrderCalled, isTrue);
      expect(find.text('Cannot cancel order: bill has already been generated'), findsOneWidget);
    });

    testWidgets('8. KDS rejection displays exact server message after kitchen acceptance', (tester) async {
      final mockAuth = MockAuthServiceForCancellation(userToReturn: currentUser);
      final mockOrder = createOrder(id: 108, status: 'ACCEPTED');
      final mockOrderService = MockOrderServiceForCancellation(
        activeOrderToReturn: mockOrder,
        exceptionToThrowOnCancel: const AppException(
          'Invalid cancellation status: Order cannot be cancelled after kitchen acceptance',
          code: '409',
        ),
      );

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

      await tester.tap(find.text('CANCEL ORDER'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Yes, Cancel Order'));
      await tester.pumpAndSettle();

      expect(mockOrderService.cancelOrderCalled, isTrue);
      expect(find.text('Invalid cancellation status: Order cannot be cancelled after kitchen acceptance'), findsOneWidget);
    });
  });
}
