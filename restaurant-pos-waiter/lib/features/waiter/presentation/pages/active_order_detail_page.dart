import 'dart:async';
import 'package:flutter/material.dart';
import '../../../../core/errors/app_exception.dart';
import '../../../../core/network/websocket_service.dart';
import '../../../../shared/models/order_model.dart';
import '../../../../shared/models/table_model.dart';
import '../../../../shared/models/user_model.dart';
import '../../data/auth_service.dart';
import '../../data/order_service.dart';
import 'dine_in_order_page.dart';

class ActiveOrderDetailPage extends StatefulWidget {
  final TableModel table;
  final OrderModel? initialOrder;
  final OrderService? orderService;
  final AuthService? authService;
  final WebSocketService? webSocketService;

  const ActiveOrderDetailPage({
    super.key,
    required this.table,
    this.initialOrder,
    this.orderService,
    this.authService,
    this.webSocketService,
  });

  @override
  State<ActiveOrderDetailPage> createState() => _ActiveOrderDetailPageState();
}

class _ActiveOrderDetailPageState extends State<ActiveOrderDetailPage>
    with WidgetsBindingObserver {
  late final OrderService _orderService;
  late final AuthService _authService;
  late final WebSocketService _webSocketService;

  StreamSubscription? _webSocketSubscription;

  OrderModel? _order;
  UserModel? _currentUser;
  bool _isLoading = true;
  bool _isProcessing = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _orderService = widget.orderService ?? OrderService();
    _authService = widget.authService ?? AuthService();
    _webSocketService = widget.webSocketService ?? WebSocketService.instance();
    _order = widget.initialOrder;
    _subscribeToOrderEvents();
    _loadData();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _webSocketSubscription?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _fetchActiveOrder(silent: true);
    }
  }

  void _subscribeToOrderEvents() {
    _webSocketSubscription?.cancel();
    _webSocketSubscription = _webSocketService.orderEventsStream.listen((
      event,
    ) {
      if (!mounted) return;
      final eventOrderId = event['id'] ?? event['orderId'];
      final eventTableId = event['tableId'];
      if ((_order != null &&
              eventOrderId != null &&
              eventOrderId.toString() == _order!.id.toString()) ||
          (eventTableId != null &&
              eventTableId.toString() == widget.table.id.toString())) {
        _fetchActiveOrder(silent: true);
      }
    });
  }

  Future<void> _loadData() async {
    final user = await _authService.getStoredUser();
    if (mounted) {
      setState(() {
        _currentUser = user;
      });
    }

    if (_order != null) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
      return;
    }

    await _fetchActiveOrder();
  }

  Future<void> _fetchActiveOrder({bool silent = false}) async {
    if (!mounted) return;
    if (!silent) {
      setState(() {
        _isLoading = true;
        _errorMessage = null;
      });
    }

    try {
      final fetchedOrder = await _orderService.getActiveOrderByTable(
        widget.table.id,
      );
      if (mounted) {
        setState(() {
          _order = fetchedOrder;
          _isLoading = false;
        });
      }
    } on AppException catch (e) {
      if (e.code == '401') {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Your session has expired. Please login again.'),
              backgroundColor: Colors.red,
            ),
          );
          Navigator.pushNamedAndRemoveUntil(
            context,
            '/login',
            (route) => false,
          );
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
          _errorMessage = 'Failed to load active order details.';
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _handleCancelOrder() async {
    if (_order == null) return;

    if (_order!.isCompleted || _order!.isCancelled) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Order is already ${_order!.status.toLowerCase()}.'),
          backgroundColor: Colors.red,
        ),
      );
      _fetchActiveOrder();
      return;
    }

    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cancel Order?'),
        content: Text(
          'Are you sure you want to cancel Order #${_order!.id}? Table ${widget.table.tableNumber} will become AVAILABLE.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('No, Keep Order'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'Yes, Cancel Order',
              style: TextStyle(color: Colors.white),
            ),
          ),
        ],
      ),
    );

    if (confirm != true) return;

    setState(() {
      _isProcessing = true;
    });

    try {
      await _orderService.cancelOrder(_order!.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Order #${_order!.id} cancelled successfully. Table released.',
          ),
          backgroundColor: Colors.green[700],
        ),
      );
      if (Navigator.canPop(context)) {
        Navigator.pop(context, true); // Return to tables page with refresh signal
      } else {
        Navigator.pushReplacementNamed(context, '/tables');
      }
    } on AppException catch (e) {
      if (!mounted) return;
      setState(() {
        _isProcessing = false;
      });
      final msg = e.message.trim().isNotEmpty
          ? e.message.trim()
          : 'Unable to cancel order.';
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(msg), backgroundColor: Colors.red));
      _fetchActiveOrder();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isProcessing = false;
      });
      final msg = e.toString().toLowerCase().contains('socket') ||
              e.toString().toLowerCase().contains('network') ||
              e.toString().toLowerCase().contains('connection')
          ? 'Unable to cancel order. Please check network connection.'
          : 'Unable to cancel order: ${e.toString()}';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(msg),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  bool get _canAddItems {
    if (_order == null) return false;
    final status = _order!.status.toUpperCase();
    return status == 'NEW' ||
        status == 'ACCEPTED' ||
        status == 'PREPARING' ||
        status == 'READY';
  }

  void _handleAddItems() {
    if (_order == null) return;
    if (!_canAddItems) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Cannot add items to an order with status ${_order!.status}.',
          ),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => DineInOrderPage(
          table: widget.table,
          existingOrder: _order,
          isAddOnMode: true,
        ),
      ),
    ).then((_) {
      _fetchActiveOrder();
    });
  }

  void _handleModifyOrder() {
    if (_order == null) return;

    if (!_order!.isNew) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Order cannot be modified after kitchen acceptance.'),
          backgroundColor: Colors.orange,
        ),
      );
      _fetchActiveOrder();
      return;
    }

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) =>
            DineInOrderPage(table: widget.table, existingOrder: _order),
      ),
    ).then((updated) {
      if (updated == true) {
        _fetchActiveOrder();
      }
    });
  }

  Color _getStatusColor(String status) {
    switch (status.toUpperCase()) {
      case 'NEW':
        return Colors.blue[700]!;
      case 'ACCEPTED':
        return Colors.amber[800]!;
      case 'PREPARING':
        return Colors.orange[800]!;
      case 'READY':
        return Colors.green[700]!;
      case 'COMPLETED':
        return Colors.purple[700]!;
      case 'CANCELLED':
        return Colors.red[700]!;
      default:
        return Colors.grey[700]!;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text('Table ${widget.table.tableNumber} Order'),
        actions: [
          TextButton(
            onPressed: () => _fetchActiveOrder(),
            child: const Text('Refresh'),
          ),
        ],
      ),
      body: SafeArea(child: _buildBody(theme)),
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
            Text('Loading order details...'),
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
                _errorMessage!,
                style: theme.textTheme.titleMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: () => _fetchActiveOrder(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_order == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'No active order found for Table ${widget.table.tableNumber}.',
                style: theme.textTheme.titleMedium,
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Back to Tables'),
              ),
            ],
          ),
        ),
      );
    }

    final isOwnerWaiter = _currentUser != null &&
        (_order!.waiterId == null ||
            _order!.waiterId.toString() == _currentUser!.userId.toString());

    final statusColor = _getStatusColor(_order!.status);

    return Column(
      children: [
        // Top Order Info Banner
        Container(
          width: double.infinity,
          color: statusColor.withOpacity(0.08),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Order #${_order!.id}',
                    style: theme.textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: statusColor,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Text(
                      _order!.status,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 13,
                        letterSpacing: 1.0,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Text(
                    'Table ${widget.table.tableNumber}',
                    style: theme.textTheme.bodyMedium,
                  ),
                  const SizedBox(width: 16),
                  Text(
                    _order!.waiterName ?? 'Waiter',
                    style: theme.textTheme.bodyMedium,
                  ),
                ],
              ),
            ],
          ),
        ),

        // Status Notice Banner if Not NEW
        if (!_order!.isNew)
          Container(
            color: Colors.amber.withOpacity(0.15),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: Text(
              'Order is ${_order!.status}. Cannot be modified after kitchen acceptance.',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: Colors.amber[900],
              ),
            ),
          ),

        // Order Items List
        Expanded(
          child: ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: _order!.items.length,
            separatorBuilder: (ctx, i) => const Divider(),
            itemBuilder: (ctx, index) {
              final item = _order!.items[index];
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 4.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Text(
                            item.menuItemName,
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        Text(
                          '₹${item.subtotal.toStringAsFixed(2)}',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${item.quantity} × ₹${item.unitPrice.toStringAsFixed(2)}',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    if (item.notes != null && item.notes!.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 4.0),
                        child: Text(
                          'Note: ${item.notes}',
                          style: TextStyle(
                            fontSize: 13,
                            fontStyle: FontStyle.italic,
                            color: Colors.orange[900],
                          ),
                        ),
                      ),
                  ],
                ),
              );
            },
          ),
        ),

        // Order Total & Actions Footer
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.08),
                blurRadius: 10,
                offset: const Offset(0, -4),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Items Count:', style: theme.textTheme.titleMedium),
                  Text(
                    '${_order!.items.length}',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'TOTAL:',
                    style: theme.textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    '₹${_order!.totalAmount.toStringAsFixed(2)}',
                    style: theme.textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Billing & payment notice for Waiter
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                margin: const EdgeInsets.only(bottom: 12),
                decoration: BoxDecoration(
                  color: Colors.blue.withOpacity(0.08),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.blue.withOpacity(0.3)),
                ),
                child: Row(
                  children: [
                    Icon(Icons.info_outline, size: 18, color: Colors.blue[800]),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Please ask Admin to generate the bill.',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: Colors.blue[900],
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              if (_isProcessing)
                const CircularProgressIndicator()
              else ...[
                if (_canAddItems && isOwnerWaiter) ...[
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        backgroundColor: theme.colorScheme.primary,
                        foregroundColor: Colors.white,
                      ),
                      onPressed: _handleAddItems,
                      child: const Text(
                        'ADD ITEMS',
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                ],
                if (_order!.isNew && isOwnerWaiter)
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            foregroundColor: Colors.red,
                            side: const BorderSide(color: Colors.red),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                          ),
                          onPressed: _handleCancelOrder,
                          child: const Text('CANCEL ORDER'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                          ),
                          onPressed: _handleModifyOrder,
                          child: const Text('MODIFY ORDER'),
                        ),
                      ),
                    ],
                  )
                else if (!_order!.isCompleted && !_order!.isCancelled && isOwnerWaiter)
                  Column(
                    children: [
                      SizedBox(
                        width: double.infinity,
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            foregroundColor: Colors.red,
                            side: const BorderSide(color: Colors.red),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                          ),
                          onPressed: _handleCancelOrder,
                          child: const Text('CANCEL ORDER'),
                        ),
                      ),
                      const SizedBox(height: 8),
                      SizedBox(
                        width: double.infinity,
                        child: OutlinedButton(
                          onPressed: () => Navigator.pop(context),
                          child: const Text('BACK TO TABLES'),
                        ),
                      ),
                    ],
                  )
                else
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('BACK TO TABLES'),
                    ),
                  ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}
