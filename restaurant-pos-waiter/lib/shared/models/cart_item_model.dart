import 'menu_item_model.dart';

class CartItemModel {
  final MenuItemModel menuItem;
  final MenuItemVariationModel? variation;
  int quantity;
  String? notes;

  CartItemModel({
    required this.menuItem,
    this.variation,
    this.quantity = 1,
    this.notes,
  });

  int get menuItemId => menuItem.id;
  int? get variationId => variation?.id;
  String? get variationName => variation?.name;

  String get itemKey => '${menuItem.id}_${variation?.id ?? 'base'}';

  String get displayName =>
      variation != null ? '${menuItem.name} (${variation!.name})' : menuItem.name;

  double get unitPrice => variation != null ? variation!.price : menuItem.price;
  double get subtotal => unitPrice * quantity;

  Map<String, dynamic> toOrderItemRequest() {
    return {
      'menuItemId': menuItem.id,
      if (variation != null) 'variationId': variation!.id,
      'quantity': quantity,
      if (notes != null && notes!.trim().isNotEmpty) 'notes': notes!.trim(),
    };
  }

  CartItemModel copyWith({
    MenuItemModel? menuItem,
    MenuItemVariationModel? variation,
    int? quantity,
    String? notes,
  }) {
    return CartItemModel(
      menuItem: menuItem ?? this.menuItem,
      variation: variation ?? this.variation,
      quantity: quantity ?? this.quantity,
      notes: notes ?? this.notes,
    );
  }
}
