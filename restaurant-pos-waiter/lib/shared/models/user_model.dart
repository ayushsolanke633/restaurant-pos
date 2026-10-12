class UserModel {
  final dynamic userId;
  final dynamic restaurantId;
  final String role;
  final String? name;
  final String? email;

  const UserModel({
    this.userId,
    this.restaurantId,
    required this.role,
    this.name,
    this.email,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      userId: json['userId'],
      restaurantId: json['restaurantId'],
      role: (json['role'] as String? ?? '').toUpperCase(),
      name: json['name'] as String?,
      email: json['email'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'restaurantId': restaurantId,
      'role': role,
      'name': name,
      'email': email,
    };
  }
}
