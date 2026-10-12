import 'menu_item_model.dart';

class CartItemModel {
  final MenuItemModel menuItem;
  int quantity;
  String? notes;

  CartItemModel({
    required this.menuItem,
    this.quantity = 1,
    this.notes,
  });

  double get unitPrice => menuItem.price;
  double get subtotal => unitPrice * quantity;

  Map<String, dynamic> toOrderItemRequest() {
    return {
      'menuItemId': menuItem.id,
      'quantity': quantity,
      if (notes != null && notes!.trim().isNotEmpty) 'notes': notes!.trim(),
    };
  }

  CartItemModel copyWith({
    MenuItemModel? menuItem,
    int? quantity,
    String? notes,
  }) {
    return CartItemModel(
      menuItem: menuItem ?? this.menuItem,
      quantity: quantity ?? this.quantity,
      notes: notes ?? this.notes,
    );
  }
}
