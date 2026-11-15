class MenuItemVariationModel {
  final int id;
  final String name;
  final double price;
  final bool isAvailable;
  final int sortOrder;

  const MenuItemVariationModel({
    required this.id,
    required this.name,
    required this.price,
    this.isAvailable = true,
    this.sortOrder = 0,
  });

  factory MenuItemVariationModel.fromJson(Map<String, dynamic> json) {
    return MenuItemVariationModel(
      id: json['id'] is int ? json['id'] : int.parse(json['id'].toString()),
      name: json['name'] as String? ?? '',
      price: json['price'] != null ? double.parse(json['price'].toString()) : 0.0,
      isAvailable: json['isAvailable'] as bool? ?? true,
      sortOrder: json['sortOrder'] is int
          ? json['sortOrder']
          : (json['sortOrder'] != null ? int.parse(json['sortOrder'].toString()) : 0),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'price': price,
      'isAvailable': isAvailable,
      'sortOrder': sortOrder,
    };
  }
}

class MenuItemModel {
  final int id;
  final int? categoryId;
  final String? categoryName;
  final bool categoryIsActive;
  final String name;
  final String? description;
  final double price;
  final String? imageUrl;
  final bool isAvailable;
  final List<MenuItemVariationModel> variations;

  const MenuItemModel({
    required this.id,
    this.categoryId,
    this.categoryName,
    this.categoryIsActive = true,
    required this.name,
    this.description,
    required this.price,
    this.imageUrl,
    this.isAvailable = true,
    this.variations = const [],
  });

  factory MenuItemModel.fromJson(Map<String, dynamic> json) {
    var rawVariations = json['variations'] as List<dynamic>? ?? [];
    List<MenuItemVariationModel> variationsList = rawVariations
        .map((v) => MenuItemVariationModel.fromJson(v as Map<String, dynamic>))
        .toList();

    return MenuItemModel(
      id: json['id'] is int ? json['id'] : int.parse(json['id'].toString()),
      categoryId: json['categoryId'] != null
          ? (json['categoryId'] is int ? json['categoryId'] : int.parse(json['categoryId'].toString()))
          : null,
      categoryName: json['categoryName'] as String?,
      categoryIsActive: json['categoryIsActive'] as bool? ?? true,
      name: json['name'] as String? ?? '',
      description: json['description'] as String?,
      price: json['price'] != null ? double.parse(json['price'].toString()) : 0.0,
      imageUrl: json['imageUrl'] as String?,
      isAvailable: json['isAvailable'] as bool? ?? true,
      variations: variationsList,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'categoryId': categoryId,
      'categoryName': categoryName,
      'categoryIsActive': categoryIsActive,
      'name': name,
      'description': description,
      'price': price,
      'imageUrl': imageUrl,
      'isAvailable': isAvailable,
      'variations': variations.map((v) => v.toJson()).toList(),
    };
  }

  bool get hasVariations => variations.isNotEmpty;

  double get minPrice {
    if (variations.isEmpty) return price;
    return variations.map((v) => v.price).reduce((a, b) => a < b ? a : b);
  }
}
