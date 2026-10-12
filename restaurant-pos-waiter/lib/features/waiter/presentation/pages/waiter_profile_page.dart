import 'package:flutter/material.dart';
import '../../../../core/network/websocket_service.dart';
import '../../../../shared/models/user_model.dart';
import '../../../../shared/widgets/custom_card.dart';
import '../../data/auth_service.dart';
import 'waiter_order_history_page.dart';

class WaiterProfilePage extends StatefulWidget {
  final AuthService? authService;
  final WebSocketService? webSocketService;

  const WaiterProfilePage({super.key, this.authService, this.webSocketService});

  @override
  State<WaiterProfilePage> createState() => _WaiterProfilePageState();
}

class _WaiterProfilePageState extends State<WaiterProfilePage> {
  late final AuthService _authService;
  late final WebSocketService _webSocketService;

  UserModel? _user;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _authService = widget.authService ?? AuthService();
    _webSocketService = widget.webSocketService ?? WebSocketService.instance();
    _loadUser();
  }

  Future<void> _loadUser() async {
    final user = await _authService.getStoredUser();
    if (mounted) {
      setState(() {
        _user = user;
        _isLoading = false;
      });
    }
  }

  Future<void> _handleLogout() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Logout'),
        content: const Text('Are you sure you want to logout?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Theme.of(ctx).colorScheme.error,
              foregroundColor: Theme.of(ctx).colorScheme.onError,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Logout'),
          ),
        ],
      ),
    );

    if (confirm != true) return;

    await _webSocketService.disconnect();
    await _authService.logout();

    if (!mounted) return;
    Navigator.pushNamedAndRemoveUntil(context, '/login', (route) => false);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(title: const Text('My Profile')),
      body: SafeArea(
        child: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : SingleChildScrollView(
                padding: const EdgeInsets.all(24.0),
                child: Center(
                  child: CustomCard(
                    child: Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Text(
                            _user?.name ?? 'Waiter',
                            style: theme.textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 24),
                          const Divider(),
                          const SizedBox(height: 16),
                          _buildProfileRow(
                            context,
                            label: 'Name',
                            value: _user?.name ?? 'Not Available',
                          ),
                          const SizedBox(height: 16),
                          _buildProfileRow(
                            context,
                            label: 'Email',
                            value: _user?.email ?? 'Not Available',
                          ),
                          const SizedBox(height: 16),
                          _buildProfileRow(
                            context,
                            label: 'Role',
                            value: _user?.role ?? 'WAITER',
                          ),
                          if (_user?.restaurantId != null) ...[
                            const SizedBox(height: 16),
                            _buildProfileRow(
                              context,
                              label: 'Restaurant',
                              value: 'Restaurant #${_user!.restaurantId}',
                            ),
                          ],
                          const SizedBox(height: 24),
                          OutlinedButton(
                            style: OutlinedButton.styleFrom(
                              padding: const EdgeInsets.symmetric(vertical: 14),
                            ),
                            onPressed: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (context) => WaiterOrderHistoryPage(
                                    authService: _authService,
                                  ),
                                ),
                              );
                            },
                            child: const Text(
                              'View Order History',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          const SizedBox(height: 16),
                          ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: theme.colorScheme.error,
                              foregroundColor: theme.colorScheme.onError,
                              padding: const EdgeInsets.symmetric(vertical: 14),
                            ),
                            onPressed: _handleLogout,
                            child: const Text(
                              'Logout',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
      ),
    );
  }

  Widget _buildProfileRow(
    BuildContext context, {
    required String label,
    required String value,
  }) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        Text(
          value,
          style: theme.textTheme.bodyLarge?.copyWith(
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}
