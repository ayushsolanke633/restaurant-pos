import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/core/network/api_client.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/menu_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/order_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/dine_in_order_page.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/order_review_page.dart';
import 'package:restaurant_pos_waiter/shared/models/cart_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/category_model.dart';
import 'package:restaurant_pos_waiter/shared/models/menu_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/order_model.dart';
import 'package:restaurant_pos_waiter/shared/models/table_model.dart';

class MockMenuService extends MenuService {
  final List<CategoryModel> categories;
  final List<MenuItemModel> menuItems;

  MockMenuService({required this.categories, required this.menuItems})
      : super(apiClient: MockApiClient());

  @override
  Future<List<CategoryModel>> getCategories() async => categories;

  @override
  Future<List<MenuItemModel>> getMenuItems({int? categoryId}) async => menuItems;
}

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
  Future<Map<String, dynamic>> put(
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

void main() {
  final testTable = TableModel(
    id: 1,
    restaurantId: 1,
    tableNumber: '1',
    capacity: 4,
    status: 'OCCUPIED',
  );

  final testCategories = [
    CategoryModel(id: 1, name: 'Main', isActive: true),
  ];

  final testMenuItems = [
    MenuItemModel(
      id: 101,
      name: 'Paneer Butter Masala',
      price: 150.0,
      categoryId: 1,
      categoryIsActive: true,
      isAvailable: true,
    ),
    MenuItemModel(
      id: 102,
      name: 'Dal Makhani',
      price: 120.0,
      categoryId: 1,
      categoryIsActive: true,
      isAvailable: true,
    ),
  ];

  final existingSubmittedOrder = OrderModel(
    id: 50,
    restaurantId: 1,
    tableId: 1,
    tableNumber: 1,
    waiterId: 10,
    waiterName: 'Waiter John',
    orderType: 'DINE_IN',
    status: 'NEW',
    subtotal: 300.0,
    taxAmount: 0.0,
    discountAmount: 0.0,
    totalAmount: 300.0,
    items: [
      OrderItemModel(
        id: 1,
        menuItemId: 101,
        menuItemName: 'Paneer Butter Masala',
        quantity: 2,
        unitPrice: 150.0,
        subtotal: 300.0,
      ),
    ],
  );

  testWidgets('1. Modifying submitted order: Delete button is hidden for existing submitted item',
      (WidgetTester tester) async {
    final mockMenu = MockMenuService(
      categories: testCategories,
      menuItems: testMenuItems,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: DineInOrderPage(
          table: testTable,
          existingOrder: existingSubmittedOrder,
          isAddOnMode: false,
          menuService: mockMenu,
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Open Cart bottom sheet
    final viewCartFinder = find.textContaining('qty');
    expect(viewCartFinder, findsOneWidget);
    await tester.tap(viewCartFinder);
    await tester.pumpAndSettle();

    // Verify item name is displayed in bottom sheet
    final bottomSheetFinder = find.byType(BottomSheet);
    expect(
      find.descendant(of: bottomSheetFinder, matching: find.text('Paneer Butter Masala')),
      findsOneWidget,
    );
    // Delete button must NOT be present in bottom sheet for existing submitted item
    expect(
      find.descendant(of: bottomSheetFinder, matching: find.text('Delete')),
      findsNothing,
    );
  });

  testWidgets('2. Modifying submitted order: Cannot decrement existing item below original quantity',
      (WidgetTester tester) async {
    final mockMenu = MockMenuService(
      categories: testCategories,
      menuItems: testMenuItems,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: DineInOrderPage(
          table: testTable,
          existingOrder: existingSubmittedOrder,
          isAddOnMode: false,
          menuService: mockMenu,
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Open Cart bottom sheet
    await tester.tap(find.textContaining('qty'));
    await tester.pumpAndSettle();

    final bottomSheetFinder = find.byType(BottomSheet);

    // Quantity should be 2 in bottom sheet
    expect(
      find.descendant(of: bottomSheetFinder, matching: find.text('2')),
      findsOneWidget,
    );

    // The '-' button should be disabled (onPressed == null) because quantity == original quantity (2)
    final minusButtonFinder = find.descendant(
      of: bottomSheetFinder,
      matching: find.widgetWithText(OutlinedButton, '-'),
    );
    expect(minusButtonFinder, findsOneWidget);
    final OutlinedButton minusButton = tester.widget(minusButtonFinder);
    expect(minusButton.onPressed, isNull);

    // Tap '+' to increase quantity from 2 to 3
    final plusButtonFinder = find.descendant(
      of: bottomSheetFinder,
      matching: find.widgetWithText(ElevatedButton, '+'),
    );
    await tester.tap(plusButtonFinder);
    await tester.pumpAndSettle();

    // Quantity should now be 3 in bottom sheet
    expect(
      find.descendant(of: bottomSheetFinder, matching: find.text('3')),
      findsOneWidget,
    );

    // Now '-' button should be enabled
    final OutlinedButton minusButtonAfterIncrease = tester.widget(minusButtonFinder);
    expect(minusButtonAfterIncrease.onPressed, isNotNull);

    // Tap '-' to decrement back to original 2
    await tester.tap(minusButtonFinder);
    await tester.pumpAndSettle();

    expect(
      find.descendant(of: bottomSheetFinder, matching: find.text('2')),
      findsOneWidget,
    );
    final OutlinedButton minusButtonBackToOriginal = tester.widget(minusButtonFinder);
    expect(minusButtonBackToOriginal.onPressed, isNull);
  });

  testWidgets('3. Creating new order: Full cart controls (add, decrement, delete) remain functional',
      (WidgetTester tester) async {
    final mockMenu = MockMenuService(
      categories: testCategories,
      menuItems: testMenuItems,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: DineInOrderPage(
          table: TableModel(id: 2, restaurantId: 1, tableNumber: '2', capacity: 4, status: 'AVAILABLE'),
          existingOrder: null, // New order creation
          isAddOnMode: false,
          menuService: mockMenu,
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Add Paneer Butter Masala from menu
    final addBtn = find.widgetWithText(ElevatedButton, 'ADD').first;
    await tester.tap(addBtn);
    await tester.pumpAndSettle();

    // Open cart
    await tester.tap(find.textContaining('qty'));
    await tester.pumpAndSettle();

    // Delete button should be present for newly created unsubmitted item
    expect(find.text('Delete'), findsOneWidget);

    // Delete item from cart
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();

    expect(find.text('Cart is empty'), findsOneWidget);
  });

  testWidgets('4. OrderReviewPage displays clear 403 authorization message when server rejects',
      (WidgetTester tester) async {
    final mockApi = MockApiClient();
    mockApi.shouldThrow = true;
    mockApi.exceptionToThrow = const AppException('Access denied: Only ADMIN can delete order items', code: '403');

    final orderService = OrderService(apiClient: mockApi);

    await tester.pumpWidget(
      MaterialApp(
        home: OrderReviewPage(
          table: testTable,
          existingOrder: existingSubmittedOrder,
          cartItems: [
            CartItemModel(
              menuItem: testMenuItems[0],
              quantity: 1,
            ),
          ],
          orderService: orderService,
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Tap UPDATE ORDER
    await tester.tap(find.text('UPDATE ORDER'));
    await tester.pumpAndSettle();

    // Verify dialog with authorization error message is shown
    expect(find.text('Access denied: Only ADMIN can delete order items'), findsOneWidget);
  });
}
