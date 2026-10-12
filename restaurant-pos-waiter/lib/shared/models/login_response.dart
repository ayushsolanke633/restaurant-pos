class LoginResponse {
  final String token;
  final String tokenType;
  final int? expiresIn;
  final dynamic userId;
  final dynamic restaurantId;
  final String role;
  final String? name;
  final String? email;

  const LoginResponse({
    required this.token,
    this.tokenType = 'Bearer',
    this.expiresIn,
    this.userId,
    this.restaurantId,
    required this.role,
    this.name,
    this.email,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      token: json['token'] as String? ?? '',
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresIn: json['expiresIn'] is num ? (json['expiresIn'] as num).toInt() : null,
      userId: json['userId'],
      restaurantId: json['restaurantId'],
      role: (json['role'] as String? ?? '').toUpperCase(),
      name: json['name'] as String?,
      email: json['email'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'token': token,
      'tokenType': tokenType,
      'expiresIn': expiresIn,
      'userId': userId,
      'restaurantId': restaurantId,
      'role': role,
      'name': name,
      'email': email,
    };
  }
}
