import 'package:flutter/material.dart';
import '../core/network/backend_url_resolver.dart';
import '../features/waiter/data/auth_service.dart';
import '../features/waiter/presentation/pages/connection_page.dart';
import '../features/waiter/presentation/pages/login_page.dart';
import '../features/waiter/presentation/pages/waiter_tables_page.dart';

class AuthCheckPage extends StatefulWidget {
  final AuthService? authService;
  final BackendUrlResolver? urlResolver;

  const AuthCheckPage({
    super.key,
    this.authService,
    this.urlResolver,
  });

  @override
  State<AuthCheckPage> createState() => _AuthCheckPageState();
}

class _AuthCheckPageState extends State<AuthCheckPage> {
  late final AuthService _authService;
  late final BackendUrlResolver _urlResolver;

  bool _isChecking = true;
  bool _backendAvailable = false;
  bool? _isLoggedIn;

  @override
  void initState() {
    super.initState();
    _authService = widget.authService ?? AuthService();
    _urlResolver = widget.urlResolver ?? BackendUrlResolver();
    _initApp();
  }

  Future<void> _initApp() async {
    setState(() {
      _isChecking = true;
    });

    final url = await _urlResolver.resolveUrl();

    if (!mounted) return;

    if (url == null || url.isEmpty) {
      setState(() {
        _isChecking = false;
        _backendAvailable = false;
      });
      return;
    }

    final loggedIn = await _authService.isLoggedIn();

    if (!mounted) return;

    setState(() {
      _isChecking = false;
      _backendAvailable = true;
      _isLoggedIn = loggedIn;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_isChecking) {
      return const Scaffold(
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text('Discovering AnnaDhara Server...'),
            ],
          ),
        ),
      );
    }

    if (!_backendAvailable) {
      return ConnectionPage(
        onConnected: _initApp,
        urlResolver: _urlResolver,
      );
    }

    if (_isLoggedIn == true) {
      return const WaiterTablesPage();
    } else {
      return const LoginPage();
    }
  }
}
