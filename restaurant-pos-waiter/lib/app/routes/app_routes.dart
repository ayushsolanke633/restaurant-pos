import 'package:flutter/material.dart';
import '../auth_check_page.dart';
import '../../features/waiter/presentation/pages/connection_page.dart';
import '../../features/waiter/presentation/pages/login_page.dart';
import '../../features/waiter/presentation/pages/waiter_order_history_page.dart';
import '../../features/waiter/presentation/pages/waiter_profile_page.dart';
import '../../features/waiter/presentation/pages/waiter_tables_page.dart';

class AppRoutes {
  AppRoutes._();

  static const String initial = '/';
  static const String login = '/login';
  static const String home = '/home';
  static const String tables = '/tables';
  static const String connection = '/connection';
  static const String profile = '/profile';
  static const String orderHistory = '/order-history';

  static Map<String, WidgetBuilder> get routes {
    return {
      initial: (context) => const AuthCheckPage(),
      login: (context) => const LoginPage(),
      home: (context) => const WaiterTablesPage(),
      tables: (context) => const WaiterTablesPage(),
      connection: (context) => const ConnectionPage(),
      profile: (context) => const WaiterProfilePage(),
      orderHistory: (context) => const WaiterOrderHistoryPage(),
    };
  }
}

