enum TableStatus { available, occupied, billing, unknown }

class TableModel {
  final int id;
  final int restaurantId;
  final String tableNumber;
  final int capacity;
  final String status;
  final String? createdAt;
  final String? updatedAt;

  const TableModel({
    required this.id,
    required this.restaurantId,
    required this.tableNumber,
    required this.capacity,
    required this.status,
    this.createdAt,
    this.updatedAt,
  });

  TableStatus get parsedStatus {
    switch (status.toUpperCase()) {
      case 'AVAILABLE':
        return TableStatus.available;
      case 'OCCUPIED':
        return TableStatus.occupied;
      case 'BILLING':
        return TableStatus.billing;
      default:
        return TableStatus.unknown;
    }
  }

  bool get isAvailable => parsedStatus == TableStatus.available;
  bool get isOccupied => parsedStatus == TableStatus.occupied;
  bool get isBilling => parsedStatus == TableStatus.billing;

  factory TableModel.fromJson(Map<String, dynamic> json) {
    return TableModel(
      id: json['id'] is int
          ? json['id'] as int
          : (json['id'] != null ? (int.tryParse(json['id'].toString()) ?? 0) : 0),
      restaurantId: json['restaurantId'] is int
          ? json['restaurantId'] as int
          : (json['restaurantId'] != null ? (int.tryParse(json['restaurantId'].toString()) ?? 0) : 0),
      tableNumber: (json['tableNumber'] ?? '').toString(),
      capacity: json['capacity'] is int
          ? json['capacity'] as int
          : (json['capacity'] != null ? (int.tryParse(json['capacity'].toString()) ?? 0) : 0),
      status: (json['status'] ?? 'AVAILABLE').toString().toUpperCase(),
      createdAt: json['createdAt']?.toString(),
      updatedAt: json['updatedAt']?.toString(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'restaurantId': restaurantId,
      'tableNumber': tableNumber,
      'capacity': capacity,
      'status': status,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
    };
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TableModel &&
          runtimeType == other.runtimeType &&
          id == other.id &&
          restaurantId == other.restaurantId &&
          tableNumber == other.tableNumber &&
          capacity == other.capacity &&
          status == other.status;

  @override
  int get hashCode =>
      id.hashCode ^
      restaurantId.hashCode ^
      tableNumber.hashCode ^
      capacity.hashCode ^
      status.hashCode;
}
