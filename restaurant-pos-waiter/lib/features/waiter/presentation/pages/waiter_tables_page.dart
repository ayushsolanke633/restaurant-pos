import 'dart:async';
import 'package:flutter/material.dart';
import '../../../../core/errors/app_exception.dart';
import '../../../../core/network/websocket_service.dart';
import '../../../../shared/models/table_model.dart';
import '../../../../shared/models/user_model.dart';
import '../../data/auth_service.dart';
import '../../data/table_service.dart';
import 'active_order_detail_page.dart';
import 'dine_in_order_page.dart';
import 'waiter_order_history_page.dart';
import 'waiter_profile_page.dart';

class WaiterTablesPage extends StatefulWidget {
  final TableService? tableService;
  final AuthService? authService;
  final WebSocketService? webSocketService;

  const WaiterTablesPage({
    super.key,
    this.tableService,
    this.authService,
    this.webSocketService,
  });

  @override
  State<WaiterTablesPage> createState() => _WaiterTablesPageState();
}

class _WaiterTablesPageState extends State<WaiterTablesPage> with WidgetsBindingObserver {
  late final TableService _tableService;
  late final AuthService _authService;
  late final WebSocketService _webSocketService;

  StreamSubscription? _orderEventSubscription;

  List<TableModel> _tables = [];
  UserModel? _user;
  bool _isLoading = true;
  String? _errorMessage;
  int? _selectedTableId;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _tableService = widget.tableService ?? TableService();
    _authService = widget.authService ?? AuthService();
    _webSocketService = widget.webSocketService ?? WebSocketService.instance();
    _initData();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _orderEventSubscription?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      if (_user?.restaurantId != null) {
        _webSocketService.connect(restaurantId: _user!.restaurantId);
      }
      _fetchTables(silent: true);
    }
  }

  Future<void> _initData() async {
    final user = await _authService.getStoredUser();
    if (mounted) {
      setState(() {
        _user = user;
      });
    }

    if (_user?.restaurantId != null) {
      _webSocketService.connect(restaurantId: _user!.restaurantId);
      _orderEventSubscription?.cancel();
      _orderEventSubscription = _webSocketService.orderEventsStream.listen((_) {
        _fetchTables(silent: true);
      });
    }

    await _fetchTables();
  }

  Future<void> _fetchTables({bool silent = false}) async {
    if (!mounted) return;
    if (!silent) {
      setState(() {
        _isLoading = true;
        _errorMessage = null;
      });
    }

    try {
      final tables = await _tableService.getTables();
      tables.sort((a, b) {
        final numA = int.tryParse(a.tableNumber) ?? 0;
        final numB = int.tryParse(b.tableNumber) ?? 0;
        if (numA != numB) {
          return numA.compareTo(numB);
        }
        return a.tableNumber.compareTo(b.tableNumber);
      });
      if (mounted) {
        setState(() {
          _tables = tables;
          _isLoading = false;
        });
      }
    } on AppException catch (e) {
      if (e.code == '401') {
        await _webSocketService.disconnect();
        await _authService.logout();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Your session has expired. Please login again.'),
              backgroundColor: Colors.red,
            ),
          );
          Navigator.pushNamedAndRemoveUntil(context, '/login', (route) => false);
        }
        return;
      }
      if (mounted) {
        setState(() {
          _errorMessage = e.message;
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _errorMessage = 'Unable to load tables. Please check your connection.';
          _isLoading = false;
        });
      }
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

  void _onTableTap(TableModel table) {
    setState(() {
      _selectedTableId = table.id;
    });

    if (table.isAvailable) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => DineInOrderPage(table: table),
        ),
      ).then((_) => _fetchTables());
    } else {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => ActiveOrderDetailPage(table: table),
        ),
      ).then((_) => _fetchTables());
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final availableCount = _tables.where((t) => t.isAvailable).length;
    final occupiedCount = _tables.where((t) => t.isOccupied).length;
    final billingCount = _tables.where((t) => t.isBilling).length;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Tables'),
            if (_user != null)
              Text(
                '${_user!.name ?? _user!.email ?? "Waiter"} • Restaurant #${_user!.restaurantId ?? ""}',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: const Color(0xFF716C62),
                ),
              ),
          ],
        ),
        automaticallyImplyLeading: false,
        actions: [
          TextButton(
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
            child: const Text('History'),
          ),
          TextButton(
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => WaiterProfilePage(
                    authService: _authService,
                    webSocketService: _webSocketService,
                  ),
                ),
              );
            },
            child: const Text('Profile'),
          ),
          TextButton(
            onPressed: () => _fetchTables(),
            child: const Text('Refresh'),
          ),
          TextButton(
            onPressed: _handleLogout,
            child: const Text('Logout'),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // WebSocket Connection Status Sub-Header
            ValueListenableBuilder<bool>(
              valueListenable: _webSocketService.isConnectedNotifier,
              builder: (context, isConnected, _) {
                return Container(
                  width: double.infinity,
                  color: isConnected ? Colors.green.withOpacity(0.1) : Colors.amber.withOpacity(0.15),
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                  child: Row(
                    children: [
                      Container(
                        width: 8,
                        height: 8,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: isConnected ? Colors.green[700] : Colors.amber[800],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        isConnected ? 'Live updates connected' : 'Live updates unavailable',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: isConnected ? Colors.green[800] : Colors.amber[900],
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),

            // Status Summary Header
            if (!_isLoading && _errorMessage == null && _tables.isNotEmpty)
              Container(
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.5),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    _buildSummaryChip(
                      label: 'Available',
                      count: availableCount,
                      color: Colors.green[700]!,
                    ),
                    _buildSummaryChip(
                      label: 'Occupied',
                      count: occupiedCount,
                      color: Colors.orange[800]!,
                    ),
                    _buildSummaryChip(
                      label: 'Billing',
                      count: billingCount,
                      color: Colors.purple[700]!,
                    ),
                  ],
                ),
              ),

            // Main Content Area
            Expanded(
              child: _buildBody(theme),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSummaryChip({
    required String label,
    required int count,
    required Color color,
  }) {
    return Row(
      children: [
        Text(
          '$label: ',
          style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: color),
        ),
        Text(
          '$count',
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: color),
        ),
      ],
    );
  }

  Widget _buildBody(ThemeData theme) {
    if (_isLoading) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Loading tables...'),
          ],
        ),
      );
    }

    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Unable to load tables',
                style: theme.textTheme.titleLarge,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                _errorMessage!,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: () => _fetchTables(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_tables.isEmpty) {
      return RefreshIndicator(
        onRefresh: () => _fetchTables(),
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: Container(
            height: 400,
            alignment: Alignment.center,
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  'No tables found',
                  style: theme.textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                Text(
                  'There are currently no tables configured for this restaurant.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  onPressed: () => _fetchTables(),
                  child: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: () => _fetchTables(),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final crossAxisCount = constraints.maxWidth > 600 ? 4 : 2;
          return GridView.builder(
            padding: const EdgeInsets.all(16),
            physics: const AlwaysScrollableScrollPhysics(),
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: crossAxisCount,
              crossAxisSpacing: 16,
              mainAxisSpacing: 16,
              childAspectRatio: 1.0,
            ),
            itemCount: _tables.length,
            itemBuilder: (context, index) {
              final table = _tables[index];
              return _buildTableCard(theme, table);
            },
          );
        },
      ),
    );
  }

  Widget _buildTableCard(ThemeData theme, TableModel table) {
    Color statusColor;
    Color backgroundColor;
    Color borderColor;
    String actionCue;

    switch (table.parsedStatus) {
      case TableStatus.available:
        statusColor = const Color(0xFF665C4F);
        backgroundColor = const Color(0xFFE7E0D4);
        borderColor = const Color(0xFFD6CEBE);
        actionCue = 'Tap to create order';
        break;
      case TableStatus.occupied:
        statusColor = const Color(0xFF75452F);
        backgroundColor = const Color(0xFFE5D1C5);
        borderColor = const Color(0xFFD4BDB0);
        actionCue = 'Tap to view order';
        break;
      case TableStatus.billing:
        statusColor = const Color(0xFF4E483E);
        backgroundColor = const Color(0xFFDDD7C8);
        borderColor = const Color(0xFFCFC8B7);
        actionCue = 'Tap to view order';
        break;
      case TableStatus.unknown:
        statusColor = const Color(0xFF716C62);
        backgroundColor = const Color(0xFFEBE5DA);
        borderColor = const Color(0xFFD2CCBF);
        actionCue = 'Tap to view';
        break;
    }

    final isSelected = _selectedTableId == table.id;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => _onTableTap(table),
        borderRadius: BorderRadius.circular(16),
        child: Container(
          decoration: BoxDecoration(
            color: backgroundColor,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: isSelected ? theme.colorScheme.primary : borderColor,
              width: isSelected ? 3.0 : (table.isAvailable ? 2.0 : 1.0),
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 6,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          padding: const EdgeInsets.all(12),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'TABLE ${table.tableNumber}',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                  letterSpacing: 0.5,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              Text(
                '${table.capacity} seats',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              Text(
                actionCue,
                style: TextStyle(
                  fontSize: 11,
                  fontStyle: FontStyle.italic,
                  color: statusColor,
                ),
              ),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
                decoration: BoxDecoration(
                  color: statusColor.withOpacity(0.12),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  table.status,
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: statusColor,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 1.0,
                  ),
                  textAlign: TextAlign.center,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
