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
  });

  factory MenuItemModel.fromJson(Map<String, dynamic> json) {
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
    };
  }
}
