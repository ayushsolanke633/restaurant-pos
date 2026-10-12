import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/shared/models/category_model.dart';
import 'package:restaurant_pos_waiter/shared/models/menu_item_model.dart';

void main() {
  group('Category Inactive Restrictions in Waiter App Models', () {
    test('CategoryModel parses isActive from JSON correctly', () {
      final activeJson = {
        'id': 1,
        'name': 'Drinks',
        'description': 'Beverages',
        'isActive': true,
      };
      final inactiveJson = {
        'id': 2,
        'name': 'Desserts',
        'description': 'Sweets',
        'isActive': false,
      };

      final activeCat = CategoryModel.fromJson(activeJson);
      final inactiveCat = CategoryModel.fromJson(inactiveJson);

      expect(activeCat.isActive, isTrue);
      expect(inactiveCat.isActive, isFalse);
    });

    test('MenuItemModel parses categoryIsActive from JSON correctly', () {
      final activeItemJson = {
        'id': 101,
        'categoryId': 1,
        'categoryName': 'Drinks',
        'categoryIsActive': true,
        'name': 'Soda',
        'price': 2.50,
        'isAvailable': true,
      };
      final inactiveCatItemJson = {
        'id': 102,
        'categoryId': 2,
        'categoryName': 'Desserts',
        'categoryIsActive': false,
        'name': 'Cake',
        'price': 5.00,
        'isAvailable': true,
      };

      final activeItem = MenuItemModel.fromJson(activeItemJson);
      final inactiveItem = MenuItemModel.fromJson(inactiveCatItemJson);

      expect(activeItem.categoryIsActive, isTrue);
      expect(inactiveItem.categoryIsActive, isFalse);
    });

    test('Filtering inactive categories and items leaves only active items', () {
      final categories = [
        const CategoryModel(id: 1, name: 'Drinks', isActive: true),
        const CategoryModel(id: 2, name: 'Desserts', isActive: false),
      ];

      final menuItems = [
        const MenuItemModel(id: 101, categoryId: 1, categoryIsActive: true, name: 'Soda', price: 2.50),
        const MenuItemModel(id: 102, categoryId: 2, categoryIsActive: false, name: 'Cake', price: 5.00),
      ];

      final activeCategories = categories.where((c) => c.isActive).toList();
      final activeCatIds = activeCategories.map((c) => c.id).toSet();

      final filteredItems = menuItems.where((item) {
        final catActive = item.categoryIsActive;
        final belongsToActive = item.categoryId == null || activeCatIds.contains(item.categoryId);
        return catActive && belongsToActive;
      }).toList();

      expect(activeCategories.length, equals(1));
      expect(activeCategories.first.name, equals('Drinks'));
      expect(filteredItems.length, equals(1));
      expect(filteredItems.first.name, equals('Soda'));
    });
  });
}
