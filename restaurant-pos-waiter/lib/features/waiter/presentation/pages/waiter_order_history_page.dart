import 'package:flutter/material.dart';
import '../../../../core/errors/app_exception.dart';
import '../../../../shared/models/order_model.dart';
import '../../../../shared/widgets/custom_card.dart';
import '../../data/auth_service.dart';
import '../../data/order_service.dart';

class WaiterOrderHistoryPage extends StatefulWidget {
  final OrderService? orderService;
  final AuthService? authService;

  const WaiterOrderHistoryPage({
    super.key,
    this.orderService,
    this.authService,
  });

  @override
  State<WaiterOrderHistoryPage> createState() => _WaiterOrderHistoryPageState();
}

class _WaiterOrderHistoryPageState extends State<WaiterOrderHistoryPage> {
  late final OrderService _orderService;

  String _selectedFilter = 'ALL';
  List<OrderModel> _orders = [];
  bool _isLoading = true;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _orderService = widget.orderService ?? OrderService();
    _fetchHistory();
  }

  Future<void> _fetchHistory({bool silent = false}) async {
    if (!mounted) return;
    if (!silent) {
      setState(() {
        _isLoading = true;
        _errorMessage = null;
      });
    }

    try {
      final history = await _orderService.getWaiterOrderHistory(
        status: _selectedFilter == 'ALL' ? null : _selectedFilter,
      );
      if (mounted) {
        setState(() {
          _orders = history;
          _isLoading = false;
          _errorMessage = null;
        });
      }
    } catch (e) {
      if (mounted) {
        String msg = 'Failed to load order history.';
        if (e is AppException) {
          msg = e.message;
        }
        setState(() {
          _errorMessage = msg;
          _isLoading = false;
        });
      }
    }
  }

  void _onFilterChanged(String newFilter) {
    if (_selectedFilter == newFilter) return;
    setState(() {
      _selectedFilter = newFilter;
    });
    _fetchHistory();
  }

  void _showOrderDetails(OrderModel order) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => HistoricalOrderDetailSheet(order: order),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Order History'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: () => _fetchHistory(),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Filter Bar
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
              child: Row(
                children: [
                  const Text(
                    'Filter Status:',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: [
                          _buildFilterChip('ALL', 'All History'),
                          const SizedBox(width: 8),
                          _buildFilterChip('COMPLETED', 'Completed'),
                          const SizedBox(width: 8),
                          _buildFilterChip('CANCELLED', 'Cancelled'),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Content Body
            Expanded(
              child: _buildBody(theme),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFilterChip(String value, String label) {
    final isSelected = _selectedFilter == value;
    final theme = Theme.of(context);

    return ChoiceChip(
      label: Text(label),
      selected: isSelected,
      onSelected: (_) => _onFilterChanged(value),
      selectedColor: theme.colorScheme.primaryContainer,
      labelStyle: TextStyle(
        color: isSelected ? theme.colorScheme.onPrimaryContainer : theme.colorScheme.onSurface,
        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
        fontSize: 13,
      ),
    );
  }

  Widget _buildBody(ThemeData theme) {
    if (_isLoading) {
      return const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Loading order history...'),
          ],
        ),
      );
    }

    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                _errorMessage!,
                textAlign: TextAlign.center,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.error,
                ),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: () => _fetchHistory(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_orders.isEmpty) {
      String emptyMessage = 'No order history yet';
      if (_selectedFilter == 'COMPLETED') {
        emptyMessage = 'No completed orders';
      } else if (_selectedFilter == 'CANCELLED') {
        emptyMessage = 'No cancelled orders';
      }

      return RefreshIndicator(
        onRefresh: () => _fetchHistory(silent: true),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(
              height: MediaQuery.of(context).size.height * 0.5,
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      emptyMessage,
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Completed or cancelled DINE IN orders will appear here.',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.outline,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: () => _fetchHistory(silent: true),
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: _orders.length,
        itemBuilder: (context, index) {
          final order = _orders[index];
          return _buildOrderCard(order, theme);
        },
      ),
    );
  }

  Widget _buildOrderCard(OrderModel order, ThemeData theme) {
    final isCompleted = order.isCompleted;
    final statusColor = isCompleted ? Colors.green[700]! : Colors.red[700]!;
    final statusBgColor = isCompleted ? Colors.green.withOpacity(0.12) : Colors.red.withOpacity(0.12);

    final tableDisplay = order.tableNumber != null
        ? 'Table #${order.tableNumber}'
        : (order.tableId != null ? 'Table #${order.tableId}' : 'DINE IN');

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: () => _showOrderDetails(order),
        borderRadius: BorderRadius.circular(12),
        child: CustomCard(
          child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Card Header: Order ID + Table + Status Badge
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.primaryContainer,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          '#${order.id}',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 13,
                            color: theme.colorScheme.onPrimaryContainer,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        tableDisplay,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                        ),
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: statusBgColor,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: statusColor.withOpacity(0.4)),
                    ),
                    child: Text(
                      order.status.toUpperCase(),
                      style: TextStyle(
                        color: statusColor,
                        fontWeight: FontWeight.bold,
                        fontSize: 12,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              const Divider(height: 1),
              const SizedBox(height: 12),

              // Summary Info Row
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${order.items.length} item${order.items.length == 1 ? '' : 's'}',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      if (order.createdAt != null && order.createdAt!.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          _formatDateTime(order.createdAt!),
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.outline,
                          ),
                        ),
                      ],
                    ],
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        'Total Amount',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      Text(
                        '₹${order.totalAmount.toStringAsFixed(2)}',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              if (order.waiterName != null && order.waiterName!.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  'Waiter: ${order.waiterName}',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.outline,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    ),
  );
}

  String _formatDateTime(String rawDate) {
    try {
      final parsed = DateTime.parse(rawDate).toLocal();
      final dateStr = '${parsed.day.toString().padLeft(2, '0')}/${parsed.month.toString().padLeft(2, '0')}/${parsed.year}';
      final timeStr = '${parsed.hour.toString().padLeft(2, '0')}:${parsed.minute.toString().padLeft(2, '0')}';
      return '$dateStr $timeStr';
    } catch (_) {
      return rawDate;
    }
  }
}

/// Read-only historical order detail modal sheet
class HistoricalOrderDetailSheet extends StatelessWidget {
  final OrderModel order;

  const HistoricalOrderDetailSheet({super.key, required this.order});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isCompleted = order.isCompleted;
    final statusColor = isCompleted ? Colors.green[700]! : Colors.red[700]!;
    final statusBgColor = isCompleted ? Colors.green.withOpacity(0.12) : Colors.red.withOpacity(0.12);

    final tableDisplay = order.tableNumber != null
        ? 'Table #${order.tableNumber}'
        : (order.tableId != null ? 'Table #${order.tableId}' : 'DINE IN');

    return Container(
      padding: const EdgeInsets.all(20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Drag handle
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: theme.colorScheme.outline.withOpacity(0.3),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Title & Badges
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Order #${order.id}',
                    style: theme.textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    tableDisplay,
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: statusBgColor,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: statusColor.withOpacity(0.4)),
                ),
                child: Text(
                  order.status.toUpperCase(),
                  style: TextStyle(
                    color: statusColor,
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          const Divider(),

          // Metadata Info
          if (order.createdAt != null)
            _buildMetaRow(context, 'Created', _formatDate(order.createdAt!)),
          if (order.updatedAt != null && order.updatedAt != order.createdAt)
            _buildMetaRow(context, 'Completed/Updated', _formatDate(order.updatedAt!)),
          if (order.waiterName != null && order.waiterName!.isNotEmpty)
            _buildMetaRow(context, 'Waiter', order.waiterName!),
          if (order.notes != null && order.notes!.isNotEmpty)
            _buildMetaRow(context, 'Order Notes', order.notes!),

          const SizedBox(height: 16),
          Text(
            'Order Items',
            style: theme.textTheme.titleSmall?.copyWith(
              fontWeight: FontWeight.bold,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 8),

          // Items List (Read-only historical prices)
          Flexible(
            child: SingleChildScrollView(
              child: Container(
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
                padding: const EdgeInsets.all(12),
                child: Column(
                  children: order.items.map((item) {
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 6.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Expanded(
                                child: Text(
                                  item.menuItemName,
                                  style: const TextStyle(fontWeight: FontWeight.w600),
                                ),
                              ),
                              Text(
                                '${item.quantity} x ₹${item.unitPrice.toStringAsFixed(2)} = ₹${item.subtotal.toStringAsFixed(2)}',
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  color: theme.colorScheme.onSurface,
                                ),
                              ),
                            ],
                          ),
                          if (item.notes != null && item.notes!.trim().isNotEmpty) ...[
                            const SizedBox(height: 2),
                            Text(
                              'Note: ${item.notes}',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                                fontStyle: FontStyle.italic,
                              ),
                            ),
                          ],
                        ],
                      ),
                    );
                  }).toList(),
                ),
              ),
            ),
          ),

          const SizedBox(height: 16),
          const Divider(),

          // Financial Summary
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Subtotal:', style: TextStyle(fontWeight: FontWeight.w500)),
              Text('₹${order.subtotal.toStringAsFixed(2)}'),
            ],
          ),
          if (order.taxAmount > 0) ...[
            const SizedBox(height: 4),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Tax:', style: TextStyle(fontWeight: FontWeight.w500)),
                Text('₹${order.taxAmount.toStringAsFixed(2)}'),
              ],
            ),
          ],
          if (order.discountAmount > 0) ...[
            const SizedBox(height: 4),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Discount:', style: TextStyle(fontWeight: FontWeight.w500)),
                Text('-₹${order.discountAmount.toStringAsFixed(2)}'),
              ],
            ),
          ],
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Total Amount:',
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
              ),
              Text(
                '₹${order.totalAmount.toStringAsFixed(2)}',
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: theme.colorScheme.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  Widget _buildMetaRow(BuildContext context, String label, String value) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 6.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          Text(value, style: theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }

  String _formatDate(String rawDate) {
    try {
      final parsed = DateTime.parse(rawDate).toLocal();
      final dateStr = '${parsed.day.toString().padLeft(2, '0')}/${parsed.month.toString().padLeft(2, '0')}/${parsed.year}';
      final timeStr = '${parsed.hour.toString().padLeft(2, '0')}:${parsed.minute.toString().padLeft(2, '0')}';
      return '$dateStr $timeStr';
    } catch (_) {
      return rawDate;
    }
  }
}
