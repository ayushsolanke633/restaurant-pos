import 'package:flutter/foundation.dart';
import '../features/waiter/data/auth_service.dart';

enum AuthStatus { initial, authenticated, unauthenticated }

class AuthStateNotifier extends ValueNotifier<AuthStatus> {
  final AuthService _authService;

  AuthStateNotifier({AuthService? authService})
      : _authService = authService ?? AuthService(),
        super(AuthStatus.initial);

  Future<void> checkAuthStatus() async {
    final loggedIn = await _authService.isLoggedIn();
    if (loggedIn) {
      value = AuthStatus.authenticated;
    } else {
      value = AuthStatus.unauthenticated;
    }
  }

  void markAuthenticated() {
    value = AuthStatus.authenticated;
  }

  Future<void> logout() async {
    await _authService.logout();
    value = AuthStatus.unauthenticated;
  }
}
