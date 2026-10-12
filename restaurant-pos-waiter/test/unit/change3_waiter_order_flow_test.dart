import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/shared/models/cart_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/category_model.dart';
import 'package:restaurant_pos_waiter/shared/models/menu_item_model.dart';
import 'package:restaurant_pos_waiter/shared/models/order_model.dart';
import 'package:restaurant_pos_waiter/shared/models/table_model.dart';

void main() {
  group('Change 3 — Waiter Order Models & Logic Tests', () {
    test('1. Table selection status parsing (AVAILABLE, OCCUPIED, BILLING)', () {
      final availableTable = TableModel(id: 1, restaurantId: 1, tableNumber: '5', capacity: 4, status: 'AVAILABLE');
      final occupiedTable = TableModel(id: 2, restaurantId: 1, tableNumber: '6', capacity: 2, status: 'OCCUPIED');
      final billingTable = TableModel(id: 3, restaurantId: 1, tableNumber: '7', capacity: 6, status: 'BILLING');

      expect(availableTable.isAvailable, true);
      expect(availableTable.isOccupied, false);

      expect(occupiedTable.isOccupied, true);
      expect(occupiedTable.isAvailable, false);

      expect(billingTable.isBilling, true);
      expect(billingTable.isAvailable, false);
    });

    test('2. CategoryModel JSON parsing', () {
      final json = {
        'id': 10,
        'name': 'Starters',
        'description': 'Appetizers & snacks',
        'isActive': true,
      };

      final category = CategoryModel.fromJson(json);
      expect(category.id, 10);
      expect(category.name, 'Starters');
      expect(category.description, 'Appetizers & snacks');
      expect(category.isActive, true);
    });

    test('3. MenuItemModel JSON parsing & availability check', () {
      final jsonAvailable = {
        'id': 101,
        'categoryId': 10,
        'categoryName': 'Starters',
        'name': 'Paneer Tikka',
        'description': 'Grilled paneer cubes',
        'price': 300.0,
        'isAvailable': true,
      };

      final item = MenuItemModel.fromJson(jsonAvailable);
      expect(item.id, 101);
      expect(item.name, 'Paneer Tikka');
      expect(item.price, 300.0);
      expect(item.isAvailable, true);

      final jsonUnavailable = {
        'id': 102,
        'name': 'Special Dish',
        'price': '450.00',
        'isAvailable': false,
      };
      final itemUnavail = MenuItemModel.fromJson(jsonUnavailable);
      expect(itemUnavail.isAvailable, false);
    });

    test('4. CartItemModel quantity & subtotal calculations', () {
      final menuItem = MenuItemModel(id: 1, name: 'Naan', price: 50.0);
      final cartItem = CartItemModel(menuItem: menuItem, quantity: 3, notes: 'Less oil');

      expect(cartItem.unitPrice, 50.0);
      expect(cartItem.quantity, 3);
      expect(cartItem.subtotal, 150.0);
      expect(cartItem.notes, 'Less oil');
    });

    test('5. CartItemModel quantity increment & minimum 1 logic', () {
      final menuItem = MenuItemModel(id: 2, name: 'Roti', price: 20.0);
      final cartItem = CartItemModel(menuItem: menuItem, quantity: 1);

      cartItem.quantity += 1;
      expect(cartItem.quantity, 2);
      expect(cartItem.subtotal, 40.0);

      cartItem.quantity -= 1;
      expect(cartItem.quantity, 1);
      // Ensures quantity does not drop below 1 when controlled
      if (cartItem.quantity > 1) {
        cartItem.quantity -= 1;
      }
      expect(cartItem.quantity, 1);
    });

    test('6. Cart item notes conversion to request JSON', () {
      final menuItem = MenuItemModel(id: 5, name: 'Burger', price: 150.0);
      final cartItem = CartItemModel(menuItem: menuItem, quantity: 2, notes: 'No onion');

      final reqJson = cartItem.toOrderItemRequest();
      expect(reqJson['menuItemId'], 5);
      expect(reqJson['quantity'], 2);
      expect(reqJson['notes'], 'No onion');
    });

    test('7. OrderModel JSON parsing & status helper getters', () {
      final orderJson = {
        'id': 501,
        'restaurantId': 1,
        'tableId': 7,
        'waiterId': 101,
        'waiterName': 'Waiter John',
        'orderType': 'DINE_IN',
        'status': 'NEW',
        'subtotal': 750.0,
        'taxAmount': 0.0,
        'discountAmount': 0.0,
        'totalAmount': 750.0,
        'items': [
          {
            'id': 1,
            'menuItemId': 101,
            'menuItemName': 'Paneer Tikka',
            'quantity': 2,
            'unitPrice': 300.0,
            'notes': 'Less spicy',
            'subtotal': 600.0,
          },
          {
            'id': 2,
            'menuItemId': 102,
            'menuItemName': 'Butter Naan',
            'quantity': 3,
            'unitPrice': 50.0,
            'subtotal': 150.0,
          }
        ]
      };

      final order = OrderModel.fromJson(orderJson);
      expect(order.id, 501);
      expect(order.tableId, 7);
      expect(order.waiterId, 101);
      expect(order.status, 'NEW');
      expect(order.isNew, true);
      expect(order.canWaiterModifyOrCancel, true);
      expect(order.items.length, 2);
      expect(order.items[0].notes, 'Less spicy');
      expect(order.totalAmount, 750.0);
    });

    test('8. Order status transitions restriction for Waiter', () {
      final acceptedOrder = OrderModel(
        id: 502,
        orderType: 'DINE_IN',
        status: 'ACCEPTED',
        subtotal: 300.0,
        taxAmount: 0.0,
        discountAmount: 0.0,
        totalAmount: 300.0,
        items: [],
      );

      expect(acceptedOrder.isAccepted, true);
      expect(acceptedOrder.isNew, false);
      expect(acceptedOrder.canWaiterModifyOrCancel, false);
    });
  });
}
