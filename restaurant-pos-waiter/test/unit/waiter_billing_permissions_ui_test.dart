import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/network/websocket_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/auth_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/order_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/active_order_detail_page.dart';
import 'package:restaurant_pos_waiter/shared/models/order_model.dart';
import 'package:restaurant_pos_waiter/shared/models/table_model.dart';
import 'package:restaurant_pos_waiter/shared/models/user_model.dart';

class MockAuthServiceForBilling extends Fake implements AuthService {
  final UserModel? userToReturn;
  MockAuthServiceForBilling({this.userToReturn});

  UserModel? get currentUser => userToReturn;

  @override
  Future<UserModel?> getStoredUser() async => userToReturn;
}

class MockOrderServiceForBilling extends Fake implements OrderService {
  final OrderModel? activeOrderToReturn;
  MockOrderServiceForBilling({this.activeOrderToReturn});

  @override
  Future<OrderModel?> getActiveOrderByTable(int tableId) async {
    return activeOrderToReturn;
  }
}

class MockWebSocketServiceForBilling extends Fake implements WebSocketService {
  @override
  Stream<Map<String, dynamic>> get orderEventsStream => const Stream.empty();
}

void main() {
  group('Waiter Billing & Payment Permissions UI Tests', () {
    testWidgets('1. ActiveOrderDetailPage displays "Please ask Admin to generate the bill."', (tester) async {
      final mockAuth = MockAuthServiceForBilling(
        userToReturn: const UserModel(userId: 1, role: 'WAITER', name: 'Waiter 1', email: 'waiter1@pos.com'),
      );

      final mockOrder = OrderModel(
        id: 101,
        tableId: 5,
        waiterId: 1,
        orderType: 'DINE_IN',
        status: 'NEW',
        subtotal: 250.0,
        taxAmount: 0.0,
        discountAmount: 0.0,
        totalAmount: 250.0,
        items: [
          OrderItemModel(id: 1, menuItemId: 10, menuItemName: 'Pasta', quantity: 1, unitPrice: 250.0, subtotal: 250.0),
        ],
      );

      final mockOrderService = MockOrderServiceForBilling(activeOrderToReturn: mockOrder);
      final mockWs = MockWebSocketServiceForBilling();

      await tester.pumpWidget(
        MaterialApp(
          home: ActiveOrderDetailPage(
            table: const TableModel(id: 5, restaurantId: 1, capacity: 4, tableNumber: '5', status: 'OCCUPIED'),
            orderService: mockOrderService,
            authService: mockAuth,
            webSocketService: mockWs,
          ),
        ),
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      // Verify the mandatory info message is displayed
      expect(find.text('Please ask Admin to generate the bill.'), findsOneWidget);

      // Verify NO billing or payment controls exist for Waiter
      expect(find.text('Generate Bill'), findsNothing);
      expect(find.text('GENERATE BILL'), findsNothing);
      expect(find.text('Request Bill'), findsNothing);
      expect(find.text('Pay'), findsNothing);
      expect(find.text('Payment'), findsNothing);
      expect(find.text('Complete Order'), findsNothing);
      expect(find.text('COMPLETE ORDER'), findsNothing);
      expect(find.text('Mark Paid'), findsNothing);

      // Verify legitimate waiter actions ARE available
      expect(find.text('ADD ITEMS'), findsOneWidget);
      expect(find.text('CANCEL ORDER'), findsOneWidget);
      expect(find.text('MODIFY ORDER'), findsOneWidget);
    });

    testWidgets('2. In READY status, Waiter still sees Admin billing notice and no bill controls', (tester) async {
      final mockAuth = MockAuthServiceForBilling(
        userToReturn: const UserModel(userId: 1, role: 'WAITER', name: 'Waiter 1', email: 'waiter1@pos.com'),
      );

      final mockOrder = OrderModel(
        id: 102,
        tableId: 5,
        waiterId: 1,
        orderType: 'DINE_IN',
        status: 'READY',
        subtotal: 500.0,
        taxAmount: 0.0,
        discountAmount: 0.0,
        totalAmount: 500.0,
        items: [
          OrderItemModel(id: 2, menuItemId: 11, menuItemName: 'Pizza', quantity: 2, unitPrice: 250.0, subtotal: 500.0),
        ],
      );

      final mockOrderService = MockOrderServiceForBilling(activeOrderToReturn: mockOrder);
      final mockWs = MockWebSocketServiceForBilling();

      await tester.pumpWidget(
        MaterialApp(
          home: ActiveOrderDetailPage(
            table: const TableModel(id: 5, restaurantId: 1, capacity: 4, tableNumber: '5', status: 'OCCUPIED'),
            orderService: mockOrderService,
            authService: mockAuth,
            webSocketService: mockWs,
          ),
        ),
      );

      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      // Verify notice is present even when READY
      expect(find.text('Please ask Admin to generate the bill.'), findsOneWidget);

      // Verify no billing/payment buttons
      expect(find.text('Generate Bill'), findsNothing);
      expect(find.text('Complete Order'), findsNothing);
      expect(find.text('Process Payment'), findsNothing);
    });
  });
}
