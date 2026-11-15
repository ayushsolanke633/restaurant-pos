import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/core/network/api_client.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/menu_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/dine_in_order_page.dart';
import 'package:restaurant_pos_waiter/shared/models/cart_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/category_model.dart';
import 'package:restaurant_pos_waiter/shared/models/menu_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/order_model.dart';
import 'package:restaurant_pos_waiter/shared/models/table_model.dart';

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

void main() {
  final testTable = TableModel(
    id: 1,
    restaurantId: 1,
    tableNumber: '5',
    capacity: 4,
    status: 'AVAILABLE',
  );

  final testCategories = [
    CategoryModel(id: 1, name: 'Biryani', isActive: true),
    CategoryModel(id: 2, name: 'Beverages', isActive: true),
  ];

  final biryaniHalf = MenuItemVariationModel(
    id: 11,
    name: 'Half',
    price: 120.0,
    isAvailable: true,
    sortOrder: 0,
  );

  final biryaniFull = MenuItemVariationModel(
    id: 12,
    name: 'Full',
    price: 220.0,
    isAvailable: true,
    sortOrder: 1,
  );

  final biryaniFamily = MenuItemVariationModel(
    id: 13,
    name: 'Family Pack',
    price: 450.0,
    isAvailable: false, // out of stock
    sortOrder: 2,
  );

  final biryaniMenuItem = MenuItemModel(
    id: 101,
    name: 'Chicken Biryani',
    price: 120.0,
    categoryId: 1,
    categoryName: 'Biryani',
    categoryIsActive: true,
    isAvailable: true,
    variations: [biryaniHalf, biryaniFull, biryaniFamily],
  );

  final chaiSingleVariation = MenuItemVariationModel(
    id: 21,
    name: 'Regular Cup',
    price: 25.0,
    isAvailable: true,
    sortOrder: 0,
  );

  final chaiMenuItem = MenuItemModel(
    id: 102,
    name: 'Masala Chai',
    price: 25.0,
    categoryId: 2,
    categoryName: 'Beverages',
    categoryIsActive: true,
    isAvailable: true,
    variations: [chaiSingleVariation],
  );

  final lassiNoVariations = MenuItemModel(
    id: 103,
    name: 'Sweet Lassi',
    price: 50.0,
    categoryId: 2,
    categoryName: 'Beverages',
    categoryIsActive: true,
    isAvailable: true,
    variations: const [],
  );

  group('Menu Item Variations - Model Parsing & Serialization', () {
    test('MenuItemModel.fromJson correctly parses variations list', () {
      final json = {
        'id': 101,
        'name': 'Chicken Biryani',
        'price': 120.0,
        'categoryId': 1,
        'categoryName': 'Biryani',
        'categoryIsActive': true,
        'isAvailable': true,
        'variations': [
          {
            'id': 11,
            'name': 'Half',
            'price': 120.0,
            'isAvailable': true,
            'sortOrder': 0,
          },
          {
            'id': 12,
            'name': 'Full',
            'price': 220.0,
            'isAvailable': true,
            'sortOrder': 1,
          },
        ],
      };

      final model = MenuItemModel.fromJson(json);
      expect(model.id, equals(101));
      expect(model.hasVariations, isTrue);
      expect(model.variations.length, equals(2));
      expect(model.variations[0].name, equals('Half'));
      expect(model.variations[0].price, equals(120.0));
      expect(model.variations[1].name, equals('Full'));
      expect(model.variations[1].price, equals(220.0));
      expect(model.minPrice, equals(120.0));
    });

    test('MenuItemModel.fromJson handles missing variations field cleanly', () {
      final json = {
        'id': 103,
        'name': 'Sweet Lassi',
        'price': 50.0,
        'isAvailable': true,
      };

      final model = MenuItemModel.fromJson(json);
      expect(model.id, equals(103));
      expect(model.hasVariations, isFalse);
      expect(model.variations, isEmpty);
      expect(model.minPrice, equals(50.0));
    });

    test('MenuItemVariationModel toJson serialization matches backend schema', () {
      final v = MenuItemVariationModel(
        id: 11,
        name: 'Half',
        price: 120.0,
        isAvailable: true,
        sortOrder: 0,
      );
      final json = v.toJson();
      expect(json['id'], equals(11));
      expect(json['name'], equals('Half'));
      expect(json['price'], equals(120.0));
      expect(json['isAvailable'], isTrue);
      expect(json['sortOrder'], equals(0));
    });

    test('OrderItemModel parses variationId and variationName with displayName', () {
      final jsonWithVar = {
        'id': 501,
        'menuItemId': 101,
        'menuItemName': 'Chicken Biryani',
        'variationId': 11,
        'variationName': 'Half',
        'quantity': 2,
        'unitPrice': 120.0,
        'subtotal': 240.0,
      };
      final itemWithVar = OrderItemModel.fromJson(jsonWithVar);
      expect(itemWithVar.variationId, equals(11));
      expect(itemWithVar.variationName, equals('Half'));
      expect(itemWithVar.displayName, equals('Chicken Biryani (Half)'));

      final jsonWithoutVar = {
        'id': 502,
        'menuItemId': 103,
        'menuItemName': 'Sweet Lassi',
        'quantity': 1,
        'unitPrice': 50.0,
        'subtotal': 50.0,
      };
      final itemWithoutVar = OrderItemModel.fromJson(jsonWithoutVar);
      expect(itemWithoutVar.variationId, isNull);
      expect(itemWithoutVar.variationName, isNull);
      expect(itemWithoutVar.displayName, equals('Sweet Lassi'));
    });
  });

  group('CartItemModel - Variations and Separate Lines', () {
    test('CartItemModel displayName formats name with variation', () {
      final cartItemHalf = CartItemModel(
        menuItem: biryaniMenuItem,
        variation: biryaniHalf,
        quantity: 1,
      );
      expect(cartItemHalf.displayName, equals('Chicken Biryani (Half)'));
      expect(cartItemHalf.unitPrice, equals(120.0));
      expect(cartItemHalf.itemKey, equals('101_11'));

      final cartItemFull = CartItemModel(
        menuItem: biryaniMenuItem,
        variation: biryaniFull,
        quantity: 2,
      );
      expect(cartItemFull.displayName, equals('Chicken Biryani (Full)'));
      expect(cartItemFull.unitPrice, equals(220.0));
      expect(cartItemFull.subtotal, equals(440.0));
      expect(cartItemFull.itemKey, equals('101_12'));

      final cartItemBase = CartItemModel(
        menuItem: lassiNoVariations,
        quantity: 3,
      );
      expect(cartItemBase.displayName, equals('Sweet Lassi'));
      expect(cartItemBase.unitPrice, equals(50.0));
      expect(cartItemBase.subtotal, equals(150.0));
      expect(cartItemBase.itemKey, equals('103_base'));
    });

    test('toOrderItemRequest formats payload with variationId when present', () {
      final cartItemHalf = CartItemModel(
        menuItem: biryaniMenuItem,
        variation: biryaniHalf,
        quantity: 2,
        notes: 'Extra raita',
      );
      final req = cartItemHalf.toOrderItemRequest();
      expect(req['menuItemId'], equals(101));
      expect(req['variationId'], equals(11));
      expect(req['quantity'], equals(2));
      expect(req['notes'], equals('Extra raita'));

      final cartItemBase = CartItemModel(
        menuItem: lassiNoVariations,
        quantity: 1,
      );
      final reqBase = cartItemBase.toOrderItemRequest();
      expect(reqBase['menuItemId'], equals(103));
      expect(reqBase.containsKey('variationId'), isFalse);
      expect(reqBase['quantity'], equals(1));
    });

    test('Half and Full portions have distinct itemKeys allowing separate cart entries', () {
      final cart = <String, CartItemModel>{};

      final half = CartItemModel(menuItem: biryaniMenuItem, variation: biryaniHalf, quantity: 1);
      final full = CartItemModel(menuItem: biryaniMenuItem, variation: biryaniFull, quantity: 2);

      cart[half.itemKey] = half;
      cart[full.itemKey] = full;

      expect(cart.length, equals(2));
      expect(cart.containsKey('101_11'), isTrue);
      expect(cart.containsKey('101_12'), isTrue);
      expect(cart['101_11']!.quantity, equals(1));
      expect(cart['101_12']!.quantity, equals(2));
      expect(cart['101_11']!.unitPrice, equals(120.0));
      expect(cart['101_12']!.unitPrice, equals(220.0));
    });
  });

  group('DineInOrderPage Widget Tests - Variations UI', () {
    testWidgets('Menu shows SELECT button and portions badge for items with multiple variations',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1200);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockMenuService(
        categories: testCategories,
        menuItems: [biryaniMenuItem, chaiMenuItem, lassiNoVariations],
      );

      await tester.pumpWidget(
        MaterialApp(
          home: DineInOrderPage(
            table: testTable,
            menuService: mockService,
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Biryani has 3 variations -> shows '3 portions' and 'SELECT'
      expect(find.text('Chicken Biryani'), findsOneWidget);
      expect(find.text('From ₹120.00'), findsOneWidget);
      expect(find.text('3 portions'), findsOneWidget);
      expect(find.text('SELECT'), findsOneWidget);

      // Lassi has no variations -> shows base price '₹50.00' and 'ADD'
      expect(find.text('Sweet Lassi'), findsOneWidget);
      expect(find.text('₹50.00'), findsOneWidget);
      expect(find.text('ADD'), findsWidgets);
    });

    testWidgets('Tapping SELECT on multi-variation item opens variation selection sheet',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1200);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockMenuService(
        categories: testCategories,
        menuItems: [biryaniMenuItem],
      );

      await tester.pumpWidget(
        MaterialApp(
          home: DineInOrderPage(
            table: testTable,
            menuService: mockService,
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Tap SELECT button
      await tester.tap(find.text('SELECT'));
      await tester.pumpAndSettle();

      // Sheet opens
      expect(find.text('Select portion / size'), findsOneWidget);
      expect(find.text('Half'), findsOneWidget);
      expect(find.text('₹120.00'), findsOneWidget);
      expect(find.text('Full'), findsOneWidget);
      expect(find.text('₹220.00'), findsOneWidget);
      expect(find.text('Family Pack'), findsOneWidget);
      // Family Pack is out of stock
      expect(find.text('Out of Stock'), findsOneWidget);

      // Add Half portion
      final addButtons = find.widgetWithText(ElevatedButton, 'ADD');
      expect(addButtons, findsWidgets);
      await tester.tap(addButtons.first);
      await tester.pumpAndSettle();

      // Half should now show quantity stepper inside sheet
      expect(find.text('1'), findsOneWidget);

      // Tap DONE to close sheet
      await tester.tap(find.text('DONE'));
      await tester.pumpAndSettle();

      // Cart bar should show 1 Item, ₹120.00
      expect(find.text('1 Items (1 qty)'), findsOneWidget);
      expect(find.text('₹120.00'), findsOneWidget);

      // Main menu card now shows in-cart badge
      expect(find.text('1 in cart'), findsOneWidget);
    });

    testWidgets('Both Half and Full portions appear as separate lines in Cart Sheet',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1200);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockMenuService(
        categories: testCategories,
        menuItems: [biryaniMenuItem],
      );

      await tester.pumpWidget(
        MaterialApp(
          home: DineInOrderPage(
            table: testTable,
            menuService: mockService,
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Open variation sheet
      await tester.tap(find.text('SELECT'));
      await tester.pumpAndSettle();

      // Add Half
      final addButtons = find.widgetWithText(ElevatedButton, 'ADD');
      await tester.tap(addButtons.first); // Half
      await tester.pumpAndSettle();

      // Add Full
      final addButtonsAfterFirst = find.widgetWithText(ElevatedButton, 'ADD');
      await tester.tap(addButtonsAfterFirst.first); // Full
      await tester.pumpAndSettle();

      // Close sheet
      await tester.tap(find.text('DONE'));
      await tester.pumpAndSettle();

      // Total items: 2 lines (2 qty), total: 120 + 220 = 340
      expect(find.text('2 Items (2 qty)'), findsOneWidget);
      expect(find.text('₹340.00'), findsOneWidget);

      // Open Cart bottom sheet
      await tester.tap(find.text('2 Items (2 qty)'));
      await tester.pumpAndSettle();

      // Verify separate cart lines
      expect(find.text('Chicken Biryani (Half)'), findsOneWidget);
      expect(find.text('Chicken Biryani (Full)'), findsOneWidget);
      expect(find.text('₹120.00'), findsOneWidget);
      expect(find.text('₹220.00'), findsOneWidget);
    });

    testWidgets('Single variation item auto-selects and increments normally',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1200);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockMenuService(
        categories: testCategories,
        menuItems: [chaiMenuItem],
      );

      await tester.pumpWidget(
        MaterialApp(
          home: DineInOrderPage(
            table: testTable,
            menuService: mockService,
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Chai has 1 variation -> shows ADD button directly
      expect(find.text('Masala Chai'), findsOneWidget);
      expect(find.text('ADD'), findsOneWidget);

      // Tap ADD
      await tester.tap(find.text('ADD'));
      await tester.pumpAndSettle();

      // Auto-selected Chai (Regular Cup)
      expect(find.text('1 Items (1 qty)'), findsOneWidget);
      expect(find.text('₹25.00'), findsOneWidget);

      // Open cart to verify name
      await tester.tap(find.text('1 Items (1 qty)'));
      await tester.pumpAndSettle();
      expect(find.text('Masala Chai (Regular Cup)'), findsOneWidget);
    });

    testWidgets('Client Change 1: Existing submitted variation line cannot be deleted or decreased',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1200);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final existingOrder = OrderModel(
        id: 99,
        restaurantId: 1,
        tableId: 1,
        orderType: 'DINE_IN',
        status: 'SUBMITTED',
        items: [
          OrderItemModel(
            id: 201,
            menuItemId: 101,
            menuItemName: 'Chicken Biryani',
            variationId: 11,
            variationName: 'Half',
            quantity: 2,
            unitPrice: 120.0,
            subtotal: 240.0,
          ),
        ],
        subtotal: 240.0,
        taxAmount: 0.0,
        discountAmount: 0.0,
        totalAmount: 240.0,
      );

      final mockService = MockMenuService(
        categories: testCategories,
        menuItems: [biryaniMenuItem],
      );

      await tester.pumpWidget(
        MaterialApp(
          home: DineInOrderPage(
            table: testTable,
            existingOrder: existingOrder,
            menuService: mockService,
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Open cart sheet
      await tester.tap(find.text('1 Items (2 qty)'));
      await tester.pumpAndSettle();

      // Delete button must NOT be visible for existing submitted item
      expect(find.text('Delete'), findsNothing);

      // Quantity is 2, original is 2: decrement button should be disabled (canDecrement = false)
      final minusButton = tester.widget<OutlinedButton>(
        find.widgetWithText(OutlinedButton, '-'),
      );
      expect(minusButton.onPressed, isNull);

      // Increment (+) button IS enabled
      final plusButton = tester.widget<ElevatedButton>(
        find.widgetWithText(ElevatedButton, '+'),
      );
      expect(plusButton.onPressed, isNotNull);

      // Tap (+) to increase to 3
      await tester.tap(find.widgetWithText(ElevatedButton, '+'));
      await tester.pumpAndSettle();

      // Now quantity is 3 > original 2, so decrement button IS enabled
      final minusButtonAfter = tester.widget<OutlinedButton>(
        find.widgetWithText(OutlinedButton, '-'),
      );
      expect(minusButtonAfter.onPressed, isNotNull);
    });
  });
}
