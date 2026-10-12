class OrderItemModel {
  final int? id;
  final int menuItemId;
  final String menuItemName;
  final int quantity;
  final double unitPrice;
  final String? notes;
  final double subtotal;

  OrderItemModel({
    this.id,
    required this.menuItemId,
    required this.menuItemName,
    required this.quantity,
    required this.unitPrice,
    this.notes,
    required this.subtotal,
  });

  factory OrderItemModel.fromJson(Map<String, dynamic> json) {
    return OrderItemModel(
      id: json['id'] != null
          ? (json['id'] is int ? json['id'] : int.parse(json['id'].toString()))
          : null,
      menuItemId: json['menuItemId'] is int
          ? json['menuItemId']
          : int.parse((json['menuItemId'] ?? 0).toString()),
      menuItemName: json['menuItemName'] as String? ?? 'Item',
      quantity: json['quantity'] is int
          ? json['quantity']
          : int.parse((json['quantity'] ?? 1).toString()),
      unitPrice: json['unitPrice'] != null
          ? double.parse(json['unitPrice'].toString())
          : 0.0,
      notes: json['notes'] as String?,
      subtotal: json['subtotal'] != null
          ? double.parse(json['subtotal'].toString())
          : 0.0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'menuItemId': menuItemId,
      'menuItemName': menuItemName,
      'quantity': quantity,
      'unitPrice': unitPrice,
      'notes': notes,
      'subtotal': subtotal,
    };
  }
}

class OrderModel {
  final int id;
  final int? restaurantId;
  final int? tableId;
  final int? tableNumber;
  final int? customerId;
  final int? waiterId;
  final String? waiterName;
  final String orderType;
  final String status;
  final double subtotal;
  final double taxAmount;
  final double discountAmount;
  final double totalAmount;
  final String? notes;
  final String? createdAt;
  final String? updatedAt;
  final List<OrderItemModel> items;

  OrderModel({
    required this.id,
    this.restaurantId,
    this.tableId,
    this.tableNumber,
    this.customerId,
    this.waiterId,
    this.waiterName,
    required this.orderType,
    required this.status,
    required this.subtotal,
    required this.taxAmount,
    required this.discountAmount,
    required this.totalAmount,
    this.notes,
    this.createdAt,
    this.updatedAt,
    required this.items,
  });

  bool get isNew => status.toUpperCase() == 'NEW';
  bool get isAccepted => status.toUpperCase() == 'ACCEPTED';
  bool get isPreparing => status.toUpperCase() == 'PREPARING';
  bool get isReady => status.toUpperCase() == 'READY';
  bool get isCompleted => status.toUpperCase() == 'COMPLETED';
  bool get isCancelled => status.toUpperCase() == 'CANCELLED';

  bool get canWaiterModifyOrCancel => isNew;

  factory OrderModel.fromJson(Map<String, dynamic> json) {
    var rawItems = json['items'] as List<dynamic>? ?? [];
    List<OrderItemModel> itemsList =
        rawItems.map((itemJson) => OrderItemModel.fromJson(itemJson)).toList();

    return OrderModel(
      id: json['id'] is int ? json['id'] : int.parse(json['id'].toString()),
      restaurantId: json['restaurantId'] != null
          ? (json['restaurantId'] is int
              ? json['restaurantId']
              : int.parse(json['restaurantId'].toString()))
          : null,
      tableId: json['tableId'] != null
          ? (json['tableId'] is int
              ? json['tableId']
              : int.parse(json['tableId'].toString()))
          : null,
      tableNumber: json['tableNumber'] != null
          ? (json['tableNumber'] is int
              ? json['tableNumber']
              : int.parse(json['tableNumber'].toString()))
          : null,
      customerId: json['customerId'] != null
          ? (json['customerId'] is int
              ? json['customerId']
              : int.parse(json['customerId'].toString()))
          : null,
      waiterId: json['waiterId'] != null
          ? (json['waiterId'] is int
              ? json['waiterId']
              : int.parse(json['waiterId'].toString()))
          : null,
      waiterName: json['waiterName'] as String?,
      orderType: json['orderType'] as String? ?? 'DINE_IN',
      status: json['status'] as String? ?? 'NEW',
      subtotal: json['subtotal'] != null
          ? double.parse(json['subtotal'].toString())
          : 0.0,
      taxAmount: json['taxAmount'] != null
          ? double.parse(json['taxAmount'].toString())
          : 0.0,
      discountAmount: json['discountAmount'] != null
          ? double.parse(json['discountAmount'].toString())
          : 0.0,
      totalAmount: json['totalAmount'] != null
          ? double.parse(json['totalAmount'].toString())
          : 0.0,
      notes: json['notes'] as String?,
      createdAt: json['createdAt'] as String?,
      updatedAt: json['updatedAt'] as String?,
      items: itemsList,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'restaurantId': restaurantId,
      'tableId': tableId,
      'tableNumber': tableNumber,
      'customerId': customerId,
      'waiterId': waiterId,
      'waiterName': waiterName,
      'orderType': orderType,
      'status': status,
      'subtotal': subtotal,
      'taxAmount': taxAmount,
      'discountAmount': discountAmount,
      'totalAmount': totalAmount,
      'notes': notes,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
      'items': items.map((i) => i.toJson()).toList(),
    };
  }
}
