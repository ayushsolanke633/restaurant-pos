import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/core/errors/app_exception.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/auth_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/data/table_service.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/waiter_tables_page.dart';
import 'package:restaurant_pos_waiter/shared/models/table_model.dart';
import 'package:restaurant_pos_waiter/shared/models/user_model.dart';

class FakeTableService extends TableService {
  List<TableModel>? mockTables;
  Exception? mockException;
  int getTablesCallCount = 0;

  FakeTableService({this.mockTables, this.mockException});

  @override
  Future<List<TableModel>> getTables() async {
    getTablesCallCount++;
    if (mockException != null) {
      throw mockException!;
    }
    return mockTables ?? [];
  }
}

class FakeAuthService extends AuthService {
  UserModel? mockUser;

  FakeAuthService({this.mockUser});

  @override
  Future<UserModel?> getStoredUser() async {
    return mockUser ??
        const UserModel(
          userId: 1,
          restaurantId: 2,
          role: 'WAITER',
          name: 'Waiter Senior',
          email: 'waiter.senior@example.com',
        );
  }

  @override
  Future<void> logout() async {}
}

void main() {
  group('WaiterTablesPage Widget Tests', () {
    testWidgets('6. Displays loading state while fetching tables', (tester) async {
      final tableService = FakeTableService(mockTables: []);
      final authService = FakeAuthService();

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterTablesPage(
            tableService: tableService,
            authService: authService,
          ),
        ),
      );

      // Verify progress indicator is rendered initially
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Loading tables...'), findsOneWidget);

      await tester.pumpAndSettle();
    });

    testWidgets('7. Displays empty state when no tables exist', (tester) async {
      final tableService = FakeTableService(mockTables: []);
      final authService = FakeAuthService();

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterTablesPage(
            tableService: tableService,
            authService: authService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.textContaining('No tables found'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('8. Displays error state when API call fails', (tester) async {
      final tableService = FakeTableService(
        mockException: const AppException('Unable to reach backend server.'),
      );
      final authService = FakeAuthService();

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterTablesPage(
            tableService: tableService,
            authService: authService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.textContaining('Unable to load tables'), findsOneWidget);
      expect(find.text('Unable to reach backend server.'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('Displays table grid with AVAILABLE, OCCUPIED, and BILLING statuses', (tester) async {
      final sampleTables = [
        const TableModel(
          id: 1,
          restaurantId: 2,
          tableNumber: '01',
          capacity: 4,
          status: 'AVAILABLE',
        ),
        const TableModel(
          id: 2,
          restaurantId: 2,
          tableNumber: '02',
          capacity: 2,
          status: 'OCCUPIED',
        ),
        const TableModel(
          id: 3,
          restaurantId: 2,
          tableNumber: '03',
          capacity: 6,
          status: 'BILLING',
        ),
      ];

      final tableService = FakeTableService(mockTables: sampleTables);
      final authService = FakeAuthService();

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterTablesPage(
            tableService: tableService,
            authService: authService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('TABLE 01'), findsOneWidget);
      expect(find.text('TABLE 02'), findsOneWidget);
      expect(find.text('TABLE 03'), findsOneWidget);
      expect(find.text('AVAILABLE'), findsOneWidget);
      expect(find.text('OCCUPIED'), findsOneWidget);
      expect(find.text('BILLING'), findsOneWidget);
    });

    testWidgets('9. AVAILABLE table selection navigates to DineInOrderPage', (tester) async {
      final sampleTables = [
        const TableModel(
          id: 1,
          restaurantId: 2,
          tableNumber: '01',
          capacity: 4,
          status: 'AVAILABLE',
        ),
      ];

      final tableService = FakeTableService(mockTables: sampleTables);
      final authService = FakeAuthService();

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterTablesPage(
            tableService: tableService,
            authService: authService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('TABLE 01'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('Table 01 Menu'), findsOneWidget);
    });

    testWidgets('10. OCCUPIED and BILLING tables open ActiveOrderDetailPage', (tester) async {
      final sampleTables = [
        const TableModel(
          id: 2,
          restaurantId: 2,
          tableNumber: '02',
          capacity: 2,
          status: 'OCCUPIED',
        ),
      ];

      final tableService = FakeTableService(mockTables: sampleTables);
      final authService = FakeAuthService();

      await tester.pumpWidget(
        MaterialApp(
          home: WaiterTablesPage(
            tableService: tableService,
            authService: authService,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Tap OCCUPIED table
      await tester.tap(find.text('TABLE 02'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(
        find.text('Table 02 Order'),
        findsOneWidget,
      );
    });
  });
}
