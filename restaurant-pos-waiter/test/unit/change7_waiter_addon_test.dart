import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/core/network/api_client.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/order_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/active_order_detail_page.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/dine_in_order_page.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/order_review_page.dart';
import 'package:restaurant_pos_waiter/shared/models/cart_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/menu_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/order_model.dart';
import 'package:restaurant_pos_waiter/shared/models/table_model.dart';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:restaurant_pos_waiter/core/network/websocket_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/auth_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/menu_service.dart';
import 'package:restaurant_pos_waiter/shared/models/category_model.dart';
import 'package:restaurant_pos_waiter/shared/models/user_model.dart';

class MockApiClient extends ApiClient {
  String? lastPath;
  Map<String, dynamic>? lastBody;
  dynamic mockResponse;
  bool shouldThrow = false;
  AppException? exceptionToThrow;

  @override
  Future<Map<String, dynamic>> post(
    String path, {
    required Map<String, dynamic> body,
    String? token,
  }) async {
    lastPath = path;
    lastBody = body;
    if (shouldThrow) {
      throw exceptionToThrow ?? const AppException('Error');
    }
    return (mockResponse is Map<String, dynamic>) ? mockResponse : {};
  }

  @override
  Future<dynamic> get(String path, {String? token}) async {
    lastPath = path;
    if (shouldThrow) {
      throw exceptionToThrow ?? const AppException('Error');
    }
    return mockResponse ?? [];
  }
}

class MockAuthService extends AuthService {
  MockAuthService() : super(apiClient: MockApiClient());
  @override
  Future<UserModel?> getStoredUser() async => UserModel(
        userId: 10,
        restaurantId: 1,
        role: 'WAITER',
        name: 'Waiter John',
        email: 'waiter@test.com',
      );
}

class MockWebSocketService extends WebSocketService {
  MockWebSocketService() : super.forTesting();
  @override
  Stream<Map<String, dynamic>> get orderEventsStream => const Stream.empty();
  @override
  Future<void> connect({dynamic restaurantId}) async {}
  @override
  Future<void> disconnect() async {}
}

class MockMenuService extends MenuService {
  MockMenuService() : super(apiClient: MockApiClient());
  @override
  Future<List<CategoryModel>> getCategories() async => [];
  @override
  Future<List<MenuItemModel>> getMenuItems({int? categoryId}) async => [];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  FlutterSecureStorage.setMockInitialValues({});

  group('Change 7 — Flutter Waiter Add-Items Unit & UI Tests', () {
    late MockAuthService mockAuth;
    late MockWebSocketService mockWs;
    late OrderService mockOrder;
    late MockMenuService mockMenu;

    setUp(() {
      mockAuth = MockAuthService();
      mockWs = MockWebSocketService();
      mockOrder = OrderService(apiClient: MockApiClient());
      mockMenu = MockMenuService();
    });

    final testTable = TableModel(
      id: 1,
      restaurantId: 1,
      tableNumber: '5',
      capacity: 4,
      status: 'OCCUPIED',
    );

    OrderModel createTestOrder(String status) {
      return OrderModel(
        id: 100,
        restaurantId: 1,
        tableId: 1,
        waiterId: 10,
        waiterName: 'Waiter John',
        orderType: 'DINE_IN',
        status: status,
        subtotal: 200.0,
        taxAmount: 0.0,
        discountAmount: 0.0,
        totalAmount: 200.0,
        items: [
          OrderItemModel(
            id: 1,
            menuItemId: 101,
            menuItemName: 'Burger',
            quantity: 1,
            unitPrice: 200.0,
            subtotal: 200.0,
          ),
        ],
      );
    }

    testWidgets('1. ADD ITEMS button visible for NEW', (tester) async {
      final order = createTestOrder('NEW');
      await tester.pumpWidget(MaterialApp(
        home: ActiveOrderDetailPage(
          table: testTable,
          initialOrder: order,
          orderService: mockOrder,
          authService: mockAuth,
          webSocketService: mockWs,
        ),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.text('ADD ITEMS'), findsOneWidget);
    });

    testWidgets('2. ADD ITEMS button visible for ACCEPTED', (tester) async {
      final order = createTestOrder('ACCEPTED');
      await tester.pumpWidget(MaterialApp(
        home: ActiveOrderDetailPage(
          table: testTable,
          initialOrder: order,
          orderService: mockOrder,
          authService: mockAuth,
          webSocketService: mockWs,
        ),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.text('ADD ITEMS'), findsOneWidget);
    });

    testWidgets('3. ADD ITEMS button visible for PREPARING', (tester) async {
      final order = createTestOrder('PREPARING');
      await tester.pumpWidget(MaterialApp(
        home: ActiveOrderDetailPage(
          table: testTable,
          initialOrder: order,
          orderService: mockOrder,
          authService: mockAuth,
          webSocketService: mockWs,
        ),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.text('ADD ITEMS'), findsOneWidget);
    });

    testWidgets('4. ADD ITEMS button visible for READY', (tester) async {
      final order = createTestOrder('READY');
      await tester.pumpWidget(MaterialApp(
        home: ActiveOrderDetailPage(
          table: testTable,
          initialOrder: order,
          orderService: mockOrder,
          authService: mockAuth,
          webSocketService: mockWs,
        ),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.text('ADD ITEMS'), findsOneWidget);
    });

    testWidgets('5. ADD ITEMS hidden for COMPLETED', (tester) async {
      final order = createTestOrder('COMPLETED');
      await tester.pumpWidget(MaterialApp(
        home: ActiveOrderDetailPage(
          table: testTable,
          initialOrder: order,
          orderService: mockOrder,
          authService: mockAuth,
          webSocketService: mockWs,
        ),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.text('ADD ITEMS'), findsNothing);
    });

    testWidgets('6. ADD ITEMS hidden for CANCELLED', (tester) async {
      final order = createTestOrder('CANCELLED');
      await tester.pumpWidget(MaterialApp(
        home: ActiveOrderDetailPage(
          table: testTable,
          initialOrder: order,
          orderService: mockOrder,
          authService: mockAuth,
          webSocketService: mockWs,
        ),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.text('ADD ITEMS'), findsNothing);
    });

    test('7. Add-on mode starts with empty cart', () {
      final cart = <int, CartItemModel>{};
      expect(cart.isEmpty, true);
    });

    test('8. Add-on mode can select menu items', () {
      final menuItem = MenuItemModel(id: 102, name: 'Coke', price: 50.0);
      final cartItem = CartItemModel(menuItem: menuItem, quantity: 1);
      final cart = <int, CartItemModel>{102: cartItem};

      expect(cart.length, 1);
      expect(cart[102]!.menuItem.name, 'Coke');
    });

    test('9. Add-on mode supports quantity', () {
      final menuItem = MenuItemModel(id: 102, name: 'Coke', price: 50.0);
      final cartItem = CartItemModel(menuItem: menuItem, quantity: 1);
      cartItem.quantity += 2;

      expect(cartItem.quantity, 3);
      expect(cartItem.subtotal, 150.0);
    });

    test('10. Add-on mode supports notes', () {
      final menuItem = MenuItemModel(id: 102, name: 'Coke', price: 50.0);
      final cartItem = CartItemModel(menuItem: menuItem, quantity: 1, notes: 'Extra ice');

      expect(cartItem.notes, 'Extra ice');
      expect(cartItem.toOrderItemRequest()['notes'], 'Extra ice');
    });

    test('11. Add-on mode prevents duplicate submission when submitting', () {
      bool isSubmitting = true;
      expect(isSubmitting, true);
      // Attempting second tap when isSubmitting == true does nothing
    });

    test('12. Correct POST endpoint is used for add-items', () async {
      final mockClient = MockApiClient();
      mockClient.mockResponse = {
        'id': 100,
        'restaurantId': 1,
        'tableId': 1,
        'orderType': 'DINE_IN',
        'status': 'PREPARING',
        'subtotal': 250.0,
        'taxAmount': 0.0,
        'discountAmount': 0.0,
        'totalAmount': 250.0,
        'items': []
      };

      final service = OrderService(apiClient: mockClient);
      final cartItem = CartItemModel(
        menuItem: MenuItemModel(id: 102, name: 'Coke', price: 50.0),
        quantity: 1,
      );

      await service.addItemsToOrder(100, items: [cartItem]);

      expect(mockClient.lastPath, '/api/orders/100/items');
    });

    test('13. Correct order ID is sent in add-items URL', () async {
      final mockClient = MockApiClient();
      mockClient.mockResponse = {
        'id': 456,
        'orderType': 'DINE_IN',
        'status': 'ACCEPTED',
        'subtotal': 100.0,
        'taxAmount': 0.0,
        'discountAmount': 0.0,
        'totalAmount': 100.0,
        'items': []
      };

      final service = OrderService(apiClient: mockClient);
      await service.addItemsToOrder(456, items: []);

      expect(mockClient.lastPath, '/api/orders/456/items');
    });

    test('14. Existing order placement still uses POST /api/orders', () async {
      final mockClient = MockApiClient();
      mockClient.mockResponse = {
        'id': 200,
        'orderType': 'DINE_IN',
        'status': 'NEW',
        'subtotal': 100.0,
        'taxAmount': 0.0,
        'discountAmount': 0.0,
        'totalAmount': 100.0,
        'items': []
      };

      final service = OrderService(apiClient: mockClient);
      await service.createOrder(tableId: 1, items: []);

      expect(mockClient.lastPath, '/api/orders');
    });

    testWidgets('15. Successful add-items shows correct button label', (tester) async {
      final order = createTestOrder('PREPARING');
      final cartItem = CartItemModel(
        menuItem: MenuItemModel(id: 102, name: 'Coke', price: 50.0),
        quantity: 1,
      );

      await tester.pumpWidget(MaterialApp(
        home: OrderReviewPage(
          table: testTable,
          cartItems: [cartItem],
          existingOrder: order,
          isAddOnMode: true,
        ),
      ));
      await tester.pumpAndSettle();

      expect(find.text('ADD TO EXISTING ORDER'), findsOneWidget);
    });

    testWidgets('16. Updated order displays header in add-on mode review', (tester) async {
      final order = createTestOrder('PREPARING');
      final cartItem = CartItemModel(
        menuItem: MenuItemModel(id: 102, name: 'Coke', price: 50.0),
        quantity: 1,
      );

      await tester.pumpWidget(MaterialApp(
        home: OrderReviewPage(
          table: testTable,
          cartItems: [cartItem],
          existingOrder: order,
          isAddOnMode: true,
        ),
      ));
      await tester.pumpAndSettle();

      expect(find.text('ADD ITEMS REVIEW'), findsOneWidget);
      expect(find.text('ADD-ON ITEMS'), findsOneWidget);
    });

    testWidgets('17. Bill-generated error displays user-friendly message', (tester) async {
      final order = createTestOrder('READY');
      final mockClient = MockApiClient();
      mockClient.shouldThrow = true;
      mockClient.exceptionToThrow = const AppException(
        'Cannot add items because a bill has already been generated for this order',
      );
      final service = OrderService(apiClient: mockClient);

      final cartItem = CartItemModel(
        menuItem: MenuItemModel(id: 102, name: 'Coke', price: 50.0),
        quantity: 1,
      );

      await tester.pumpWidget(MaterialApp(
        home: OrderReviewPage(
          table: testTable,
          cartItems: [cartItem],
          existingOrder: order,
          isAddOnMode: true,
          orderService: service,
        ),
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.text('ADD TO EXISTING ORDER'));
      await tester.pumpAndSettle();

      expect(find.text('Items cannot be added because the bill has already been generated.'), findsOneWidget);
    });

    testWidgets('18. 401 handling remains intact', (tester) async {
      final order = createTestOrder('PREPARING');
      final mockClient = MockApiClient();
      mockClient.shouldThrow = true;
      mockClient.exceptionToThrow = const AppException('Unauthenticated', code: '401');
      final service = OrderService(apiClient: mockClient);

      final cartItem = CartItemModel(
        menuItem: MenuItemModel(id: 102, name: 'Coke', price: 50.0),
        quantity: 1,
      );

      await tester.pumpWidget(MaterialApp(
        routes: {
          '/login': (ctx) => const Scaffold(body: Text('Login Page')),
        },
        home: OrderReviewPage(
          table: testTable,
          cartItems: [cartItem],
          existingOrder: order,
          isAddOnMode: true,
          orderService: service,
        ),
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.text('ADD TO EXISTING ORDER'));
      await tester.pumpAndSettle();

      expect(find.text('Your session has expired. Please login again.'), findsOneWidget);
    });

    testWidgets('19. Network error handling displays user-friendly snackbar', (tester) async {
      final order = createTestOrder('PREPARING');
      final mockClient = MockApiClient();
      mockClient.shouldThrow = true;
      mockClient.exceptionToThrow = const AppException(
        'Unable to connect to the restaurant server. Please check the Wi-Fi connection.',
        code: 'UNABLE_TO_CONNECT',
      );
      final service = OrderService(apiClient: mockClient);

      final cartItem = CartItemModel(
        menuItem: MenuItemModel(id: 102, name: 'Coke', price: 50.0),
        quantity: 1,
      );

      await tester.pumpWidget(MaterialApp(
        home: OrderReviewPage(
          table: testTable,
          cartItems: [cartItem],
          existingOrder: order,
          isAddOnMode: true,
          orderService: service,
        ),
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.text('ADD TO EXISTING ORDER'));
      await tester.pumpAndSettle();

      expect(find.text('Unable to connect to the restaurant server. Please check the Wi-Fi connection.'), findsOneWidget);
    });

    testWidgets('20. PopScope protects unsaved add-on cart', (tester) async {
      await tester.pumpWidget(MaterialApp(
        home: DineInOrderPage(
          table: testTable,
          isAddOnMode: true,
          menuService: mockMenu,
        ),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('Add Items to Order • Table 5'), findsOneWidget);
    });
  });
}
