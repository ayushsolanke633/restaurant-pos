import 'package:flutter/material.dart';
import '../../data/auth_service.dart';
import '../../data/table_service.dart';
import 'waiter_tables_page.dart';

class WaiterHomePage extends StatelessWidget {
  final AuthService? authService;
  final TableService? tableService;

  const WaiterHomePage({
    super.key,
    this.authService,
    this.tableService,
  });

  @override
  Widget build(BuildContext context) {
    return WaiterTablesPage(
      authService: authService,
      tableService: tableService,
    );
  }
}
