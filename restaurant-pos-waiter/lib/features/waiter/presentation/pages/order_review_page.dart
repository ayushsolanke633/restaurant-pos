import 'package:flutter/material.dart';
import '../../../../core/errors/app_exception.dart';
import '../../../../shared/models/cart_item_model.dart';
import '../../../../shared/models/order_model.dart';
import '../../../../shared/models/table_model.dart';
import '../../data/order_service.dart';
import 'order_confirmation_page.dart';

class OrderReviewPage extends StatefulWidget {
  final TableModel table;
  final List<CartItemModel> cartItems;
  final String? globalNotes;
  final OrderModel? existingOrder; // Non-null if modifying existing order
  final bool isAddOnMode;
  final OrderService? orderService;

  const OrderReviewPage({
    super.key,
    required this.table,
    required this.cartItems,
    this.globalNotes,
    this.existingOrder,
    this.isAddOnMode = false,
    this.orderService,
  });

  @override
  State<OrderReviewPage> createState() => _OrderReviewPageState();
}

class _OrderReviewPageState extends State<OrderReviewPage> {
  late final OrderService _orderService;
  bool _isSubmitting = false;

  @override
  void initState() {
    super.initState();
    _orderService = widget.orderService ?? OrderService();
  }

  double get _subtotal {
    return widget.cartItems.fold(0.0, (sum, item) => sum + item.subtotal);
  }

  Future<void> _submitOrder() async {
    if (_isSubmitting) return;
    setState(() {
      _isSubmitting = true;
    });

    try {
      if (widget.isAddOnMode && widget.existingOrder != null) {
        // Add items to existing order
        final updatedOrder = await _orderService.addItemsToOrder(
          widget.existingOrder!.id,
          items: widget.cartItems,
          notes: widget.globalNotes,
        );
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Items added to order successfully.'),
            backgroundColor: Colors.green,
          ),
        );
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(
            builder: (context) => OrderConfirmationPage(
              order: updatedOrder,
              tableNumber: widget.table.tableNumber,
              isUpdated: true,
            ),
          ),
          (route) => route.isFirst,
        );
      } else if (widget.existingOrder != null) {
        // Update existing order (full overwrite for NEW order)
        final updatedOrder = await _orderService.updateOrder(
          widget.existingOrder!.id,
          items: widget.cartItems,
          notes: widget.globalNotes,
        );
        if (!mounted) return;
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(
            builder: (context) => OrderConfirmationPage(
              order: updatedOrder,
              tableNumber: widget.table.tableNumber,
              isUpdated: true,
            ),
          ),
          (route) => route.isFirst,
        );
      } else {
        // Create new order
        final createdOrder = await _orderService.createOrder(
          tableId: widget.table.id,
          items: widget.cartItems,
          notes: widget.globalNotes,
        );
        if (!mounted) return;
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(
            builder: (context) => OrderConfirmationPage(
              order: createdOrder,
              tableNumber: widget.table.tableNumber,
            ),
          ),
          (route) => route.isFirst,
        );
      }
    } on AppException catch (e) {
      if (!mounted) return;
      setState(() {
        _isSubmitting = false;
      });

      if (e.code == '401') {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Your session has expired. Please login again.'),
            backgroundColor: Colors.red,
          ),
        );
        Navigator.pushNamedAndRemoveUntil(context, '/login', (route) => false);
        return;
      }

      String displayError = e.message;
      if (e.message.toLowerCase().contains('category') || e.message.toLowerCase().contains('inactive')) {
        displayError = e.message;
      } else if (e.message.toLowerCase().contains('bill has already been generated') ||
          e.message.toLowerCase().contains('bill already exists')) {
        displayError = 'Items cannot be added because the bill has already been generated.';
      } else if (e.code == '403') {
        displayError = 'You do not have permission to modify this order.';
      } else if (e.code == '404') {
        displayError = 'Order or menu item could not be found.';
      } else if (e.code == '500') {
        displayError = 'Something went wrong on the server. Please try again.';
      } else if (e.code == '409' ||
          e.message.toLowerCase().contains('active order') ||
          e.message.toLowerCase().contains('table is not available')) {
        if (e.message.toLowerCase().contains('another waiter')) {
          displayError = 'This table already has an active order assigned to another waiter.';
        } else {
          displayError = 'An active order already exists for this table.';
        }
      }

      final isTableConflict = displayError.toLowerCase().contains('active order');
      final isCategoryError = displayError.toLowerCase().contains('category') ||
          displayError.toLowerCase().contains('inactive');

      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Order Error'),
          content: Text(displayError),
          actions: [
            ElevatedButton(
              onPressed: () {
                Navigator.pop(ctx); // Close dialog
                if (isTableConflict) {
                  Navigator.popUntil(context, (route) => route.isFirst); // Back to tables
                } else if (isCategoryError) {
                  Navigator.pop(context, 'refresh_menu'); // Back to menu page to refresh
                } else if (widget.isAddOnMode) {
                  Navigator.pop(context); // Back to active order details
                }
              },
              child: const Text('OK'),
            ),
          ],
        ),
      );
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _isSubmitting = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Unable to connect to the restaurant server. Please check the Wi-Fi connection.'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isEditing = widget.existingOrder != null;

    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.isAddOnMode
              ? 'ADD ITEMS REVIEW'
              : (isEditing ? 'MODIFY ORDER' : 'ORDER REVIEW'),
        ),
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Header Card
            Container(
              width: double.infinity,
              color: theme.colorScheme.primaryContainer.withOpacity(0.4),
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'TABLE ${widget.table.tableNumber}',
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        widget.isAddOnMode ? 'ADD-ON ITEMS' : 'DINE IN',
                        style: theme.textTheme.labelMedium?.copyWith(
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1.0,
                        ),
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primary,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      '${widget.cartItems.length} Items',
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 13,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Cart Items List
            Expanded(
              child: ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: widget.cartItems.length,
                separatorBuilder: (ctx, i) => const Divider(),
                itemBuilder: (ctx, index) {
                  final item = widget.cartItems[index];
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 6.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: Text(
                                item.menuItem.name,
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
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '${item.quantity} × ₹${item.unitPrice.toStringAsFixed(2)}',
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                        if (item.notes != null && item.notes!.trim().isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(top: 6.0),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                              decoration: BoxDecoration(
                                color: Colors.orange.withOpacity(0.12),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                'Note: ${item.notes!.trim()}',
                                style: TextStyle(
                                  fontSize: 13,
                                  fontStyle: FontStyle.italic,
                                  color: Colors.orange[900],
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  );
                },
              ),
            ),

            // Total & Buttons Bar
            Container(
              padding: const EdgeInsets.all(20),
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
                  const Divider(),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'ESTIMATED TOTAL',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        '₹${_subtotal.toStringAsFixed(2)}',
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  Row(
                    children: [
                      Expanded(
                        flex: 1,
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(vertical: 14),
                          ),
                          onPressed: _isSubmitting ? null : () => Navigator.pop(context),
                          child: const Text('BACK'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        flex: 2,
                        child: ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(vertical: 14),
                            backgroundColor: theme.colorScheme.primary,
                            foregroundColor: Colors.white,
                          ),
                          onPressed: (widget.cartItems.isEmpty || _isSubmitting) ? null : _submitOrder,
                          child: _isSubmitting
                              ? const SizedBox(
                                  height: 20,
                                  width: 20,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : Text(
                                  widget.isAddOnMode
                                      ? 'ADD TO EXISTING ORDER'
                                      : (isEditing ? 'UPDATE ORDER' : 'PLACE ORDER'),
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                    letterSpacing: 1.0,
                                  ),
                                ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
