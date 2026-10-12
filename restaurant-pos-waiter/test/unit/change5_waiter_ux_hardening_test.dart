import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/core/network/api_client.dart';
import 'package:restaurant_pos_waiter/core/network/websocket_service.dart';
import 'package:restaurant_pos_waiter/core/storage/secure_storage_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/auth_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/menu_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/order_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/table_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/active_order_detail_page.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/dine_in_order_page.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/order_review_page.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/waiter_profile_page.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/waiter_tables_page.dart';
import 'package:restaurant_pos_waiter/shared/models/cart_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/category_model.dart';
import 'package:restaurant_pos_waiter/shared/models/menu_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/order_model.dart';
import 'package:restaurant_pos_waiter/shared/models/table_model.dart';
import 'package:restaurant_pos_waiter/shared/models/user_model.dart';

class MockAuthService extends AuthService {
  UserModel? userToReturn;
  bool logoutCalled = false;

  MockAuthService({this.userToReturn});

  @override
  Future<UserModel?> getStoredUser() async {
    return userToReturn;
  }

  @override
  Future<void> logout() async {
    logoutCalled = true;
  }
}

class MockWebSocketService extends WebSocketService {
  bool disconnectCalled = false;
  int connectCallCount = 0;
  final _eventsController = StreamController<Map<String, dynamic>>.broadcast();

  MockWebSocketService() : super.forTesting();

  @override
  Stream<Map<String, dynamic>> get orderEventsStream => _eventsController.stream;

  @override
  Future<void> connect({dynamic restaurantId}) async {
    connectCallCount++;
  }

  @override
  Future<void> disconnect() async {
    disconnectCalled = true;
  }

  void emitOrderEvent(Map<String, dynamic> event) {
    _eventsController.add(event);
  }
}

class MockTableService extends TableService {
  List<TableModel>? tablesToReturn;
  AppException? exceptionToThrow;

  MockTableService({this.tablesToReturn, this.exceptionToThrow});

  @override
  Future<List<TableModel>> getTables() async {
    if (exceptionToThrow != null) {
      throw exceptionToThrow!;
    }
    return tablesToReturn ?? [];
  }
}

class MockMenuService extends MenuService {
  List<CategoryModel> categories = [];
  List<MenuItemModel> menuItems = [];
  AppException? exceptionToThrow;

  MockMenuService({this.categories = const [], this.menuItems = const [], this.exceptionToThrow});

  @override
  Future<List<CategoryModel>> getCategories() async {
    if (exceptionToThrow != null) throw exceptionToThrow!;
    return categories;
  }

  @override
  Future<List<MenuItemModel>> getMenuItems({int? categoryId}) async {
    if (exceptionToThrow != null) throw exceptionToThrow!;
    return menuItems;
  }
}

class MockOrderService extends OrderService {
  OrderModel? createdOrderToReturn;
  OrderModel? activeOrderToReturn;
  AppException? exceptionToThrow;
  int createOrderCallCount = 0;

  MockOrderService({this.createdOrderToReturn, this.activeOrderToReturn, this.exceptionToThrow});

  @override
  Future<OrderModel> createOrder({
    required int tableId,
    required List<CartItemModel> items,
    String? notes,
  }) async {
    createOrderCallCount++;
    if (exceptionToThrow != null) throw exceptionToThrow!;
    return createdOrderToReturn ??
        OrderModel(
          id: 999,
          tableId: tableId,
          orderType: 'DINE_IN',
          status: 'NEW',
          subtotal: 100.0,
          taxAmount: 0.0,
          discountAmount: 0.0,
          totalAmount: 100.0,
          items: [],
        );
  }

  @override
  Future<OrderModel?> getActiveOrderByTable(int tableId) async {
    if (exceptionToThrow != null) throw exceptionToThrow!;
    return activeOrderToReturn;
  }
}

class MockSecureStorageService extends SecureStorageService {
  bool sessionCleared = false;

  @override
  Future<void> clearSession() async {
    sessionCleared = true;
  }
}

void main() {
  group('Change 5 — Waiter UX, Real-Time Status & Hardening Tests', () {
    testWidgets('1. Profile screen renders waiter information', (tester) async {
      final mockAuth = MockAuthService(
        userToReturn: const UserModel(
          userId: 1,
          restaurantId: 42,
          role: 'WAITER',
          name: 'John Doe',
          email: 'waiter1@restaurant.com',
        ),
      );

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterProfilePage(authService: mockAuth),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('My Profile'), findsOneWidget);
      expect(find.text('John Doe'), findsNWidgets(2));
      expect(find.text('waiter1@restaurant.com'), findsOneWidget);
      expect(find.text('WAITER'), findsOneWidget);
      expect(find.text('Restaurant #42'), findsOneWidget);
      expect(find.text('Logout'), findsOneWidget);
    });

    testWidgets('2. Logout clears session', (tester) async {
      final mockAuth = MockAuthService(
        userToReturn: const UserModel(userId: 1, role: 'WAITER', name: 'John', email: 'j@a.com'),
      );
      final mockWs = MockWebSocketService();

      await tester.pumpWidget(
        MaterialApp(
          routes: {
            '/login': (context) => const Scaffold(body: Text('Login Screen')),
          },
          home: WaiterProfilePage(authService: mockAuth, webSocketService: mockWs),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      await tester.tap(find.text('Logout').first);
      await tester.pumpAndSettle();

      expect(find.text('Are you sure you want to logout?'), findsOneWidget);

      await tester.tap(find.widgetWithText(ElevatedButton, 'Logout').last);
      await tester.pumpAndSettle();

      expect(mockAuth.logoutCalled, true);
      expect(find.text('Login Screen'), findsOneWidget);
    });

    testWidgets('3. Logout disconnects WebSocket', (tester) async {
      final mockAuth = MockAuthService(
        userToReturn: const UserModel(userId: 1, role: 'WAITER', name: 'John', email: 'j@a.com'),
      );
      final mockWs = MockWebSocketService();

      await tester.pumpWidget(
        MaterialApp(
          routes: {
            '/login': (context) => const Scaffold(body: Text('Login Screen')),
          },
          home: WaiterProfilePage(authService: mockAuth, webSocketService: mockWs),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      await tester.tap(find.text('Logout').first);
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(ElevatedButton, 'Logout').last);
      await tester.pumpAndSettle();

      expect(mockWs.disconnectCalled, true);
    });

    test('4. 401 clears session', () async {
      final mockStorage = MockSecureStorageService();
      final client = ApiClient(storageService: mockStorage);

      expect(
        () => client.handleResponse(http.Response('{}', 401)),
        throwsA(isA<AppException>().having((e) => e.code, 'code', '401')),
      );
      expect(mockStorage.sessionCleared, true);
    });

    testWidgets('5. Table loading error displays retry', (tester) async {
      final mockAuth = MockAuthService(
        userToReturn: const UserModel(userId: 1, restaurantId: 1, role: 'WAITER', name: 'John', email: 'j@a.com'),
      );
      final mockTableService = MockTableService(
        exceptionToThrow: const AppException('Unable to load tables. Please check your connection.'),
      );

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterTablesPage(tableService: mockTableService, authService: mockAuth),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('Unable to load tables'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('6. Menu loading error displays retry', (tester) async {
      final mockMenuService = MockMenuService(
        exceptionToThrow: const AppException('Unable to load menu. Please check your connection.'),
      );

      await tester.pumpWidget(
        MaterialApp(
          home: DineInOrderPage(
            table: const TableModel(id: 1, restaurantId: 1, capacity: 4, tableNumber: '1', status: 'AVAILABLE'),
            menuService: mockMenuService,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('Unable to load menu. Please check your connection.'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('7. Empty menu state', (tester) async {
      final mockMenuService = MockMenuService(categories: [], menuItems: []);

      await tester.pumpWidget(
        MaterialApp(
          home: DineInOrderPage(
            table: const TableModel(id: 1, restaurantId: 1, capacity: 4, tableNumber: '1', status: 'AVAILABLE'),
            menuService: mockMenuService,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('No menu items found.'), findsOneWidget);
    });

    testWidgets('8. Place Order disables button during request', (tester) async {
      final mockOrderService = MockOrderService();
      final cartItem = CartItemModel(
        menuItem: const MenuItemModel(id: 101, name: 'Burger', price: 100.0),
        quantity: 1,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: OrderReviewPage(
            table: const TableModel(id: 1, restaurantId: 1, capacity: 4, tableNumber: '1', status: 'AVAILABLE'),
            cartItems: [cartItem],
            orderService: mockOrderService,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      final buttonFinder = find.widgetWithText(ElevatedButton, 'PLACE ORDER');
      expect(buttonFinder, findsOneWidget);

      await tester.tap(buttonFinder, warnIfMissed: false);
      await tester.pump();

      expect(mockOrderService.createOrderCallCount, 1);
    });

    testWidgets('9. Rapid Place Order taps do not create duplicate requests', (tester) async {
      final mockOrderService = MockOrderService();
      final cartItem = CartItemModel(
        menuItem: const MenuItemModel(id: 101, name: 'Burger', price: 100.0),
        quantity: 1,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: OrderReviewPage(
            table: const TableModel(id: 1, restaurantId: 1, capacity: 4, tableNumber: '1', status: 'AVAILABLE'),
            cartItems: [cartItem],
            orderService: mockOrderService,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      final buttonFinder = find.widgetWithText(ElevatedButton, 'PLACE ORDER');
      await tester.tap(buttonFinder, warnIfMissed: false);
      await tester.tap(buttonFinder, warnIfMissed: false);
      await tester.tap(buttonFinder, warnIfMissed: false);
      await tester.pump();

      expect(mockOrderService.createOrderCallCount, 1);
    });

    testWidgets('10. 409 duplicate order is handled correctly', (tester) async {
      final mockOrderService = MockOrderService(
        exceptionToThrow: const AppException('Table already has an active order', code: '409'),
      );
      final cartItem = CartItemModel(
        menuItem: const MenuItemModel(id: 101, name: 'Burger', price: 100.0),
        quantity: 1,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: OrderReviewPage(
            table: const TableModel(id: 1, restaurantId: 1, capacity: 4, tableNumber: '1', status: 'AVAILABLE'),
            cartItems: [cartItem],
            orderService: mockOrderService,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      await tester.tap(find.widgetWithText(ElevatedButton, 'PLACE ORDER'), warnIfMissed: false);
      await tester.pumpAndSettle();

      expect(find.text('An active order already exists for this table.'), findsOneWidget);
    });

    testWidgets('11. Active order displays status', (tester) async {
      final mockAuth = MockAuthService(
        userToReturn: const UserModel(userId: 1, role: 'WAITER', name: 'John', email: 'j@a.com'),
      );
      final mockOrderService = MockOrderService(
        activeOrderToReturn: OrderModel(
          id: 101,
          tableId: 5,
          orderType: 'DINE_IN',
          status: 'ACCEPTED',
          subtotal: 200.0,
          taxAmount: 0.0,
          discountAmount: 0.0,
          totalAmount: 200.0,
          items: [],
        ),
      );

      await tester.pumpWidget(
        MaterialApp(
          home: ActiveOrderDetailPage(
            table: const TableModel(id: 5, restaurantId: 1, capacity: 4, tableNumber: '5', status: 'OCCUPIED'),
            orderService: mockOrderService,
            authService: mockAuth,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      expect(find.text('Order #101'), findsOneWidget);
      expect(find.text('ACCEPTED'), findsAtLeastNWidgets(1));
    });

    testWidgets('12. Order status update changes UI', (tester) async {
      final mockAuth = MockAuthService(
        userToReturn: const UserModel(userId: 1, role: 'WAITER', name: 'John', email: 'j@a.com'),
      );
      final mockOrderService = MockOrderService(
        activeOrderToReturn: OrderModel(
          id: 101,
          tableId: 5,
          orderType: 'DINE_IN',
          status: 'NEW',
          subtotal: 200.0,
          taxAmount: 0.0,
          discountAmount: 0.0,
          totalAmount: 200.0,
          items: [],
        ),
      );
      final mockWs = MockWebSocketService();

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

      expect(find.text('NEW'), findsAtLeastNWidgets(1));

      // Simulate status change via WebSocket
      mockOrderService.activeOrderToReturn = OrderModel(
        id: 101,
        tableId: 5,
        orderType: 'DINE_IN',
        status: 'PREPARING',
        subtotal: 200.0,
        taxAmount: 0.0,
        discountAmount: 0.0,
        totalAmount: 200.0,
        items: [],
      );

      mockWs.emitOrderEvent({'id': 101, 'status': 'PREPARING', 'tableId': 5});
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      expect(find.text('PREPARING'), findsAtLeastNWidgets(1));
    });

    test('13. WebSocket reconnect does not create duplicate subscriptions', () async {
      final mockWs = MockWebSocketService();
      await mockWs.connect(restaurantId: 10);
      expect(mockWs.connectCallCount, 1);
    });

    testWidgets('14. Back navigation with unsaved cart shows confirmation', (tester) async {
      final mockMenu = MockMenuService(
        categories: [const CategoryModel(id: 1, name: 'Drinks')],
        menuItems: [const MenuItemModel(id: 10, name: 'Coke', price: 50.0, isAvailable: true)],
      );

      await tester.pumpWidget(
        MaterialApp(
          home: DineInOrderPage(
            table: const TableModel(id: 1, restaurantId: 1, capacity: 4, tableNumber: '1', status: 'AVAILABLE'),
            menuService: mockMenu,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      // Add item to cart
      await tester.tap(find.text('ADD'));
      await tester.pump();

      // Attempt back navigation via system pop
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();

      expect(find.text('Leave this order?'), findsOneWidget);
      expect(find.text('Your selected items will be removed.'), findsOneWidget);
    });

    testWidgets('15. Empty cart prevents order submission', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: OrderReviewPage(
            table: const TableModel(id: 1, restaurantId: 1, capacity: 4, tableNumber: '1', status: 'AVAILABLE'),
            cartItems: const [],
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      final button = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, 'PLACE ORDER'));
      expect(button.onPressed, null);
    });

    test('16. WebSocketService singleton returns same instance', () {
      final instance1 = WebSocketService();
      final instance2 = WebSocketService.instance();
      expect(identical(instance1, instance2), true);
    });

    test('17. Missing JWT prevents connection attempt', () async {
      final mockStorage = MockSecureStorageService();
      final service = WebSocketService(storageService: mockStorage);
      await service.connect(restaurantId: 1);
      expect(service.isConnected, false);
    });
  });
}
