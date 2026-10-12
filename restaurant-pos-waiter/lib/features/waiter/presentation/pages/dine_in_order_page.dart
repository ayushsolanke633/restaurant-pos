import 'package:flutter/material.dart';
import '../../../../core/errors/app_exception.dart';
import '../../../../shared/models/cart_item_model.dart';
import '../../../../shared/models/category_model.dart';
import '../../../../shared/models/menu_item_model.dart';
import '../../../../shared/models/order_model.dart';
import '../../../../shared/models/table_model.dart';
import '../../data/menu_service.dart';
import 'order_review_page.dart';

class DineInOrderPage extends StatefulWidget {
  final TableModel table;
  final OrderModel? existingOrder; // Non-null if modifying existing order
  final bool isAddOnMode;
  final MenuService? menuService;

  const DineInOrderPage({
    super.key,
    required this.table,
    this.existingOrder,
    this.isAddOnMode = false,
    this.menuService,
  });

  @override
  State<DineInOrderPage> createState() => _DineInOrderPageState();
}

class _DineInOrderPageState extends State<DineInOrderPage>
    with SingleTickerProviderStateMixin {
  late final MenuService _menuService;

  List<CategoryModel> _categories = [];
  List<MenuItemModel> _menuItems = [];
  final Map<int, CartItemModel> _cart = {}; // menuItemId -> CartItemModel

  int? _selectedCategoryId; // null = All
  bool _isLoading = true;
  String? _errorMessage;
  String _searchQuery = '';
  final TextEditingController _searchController = TextEditingController();

  final List<String> _quickNotePresets = [
    'Less spicy',
    'No onion',
    'Extra cheese',
    'Less oil',
    'No garlic',
  ];

  @override
  void initState() {
    super.initState();
    _menuService = widget.menuService ?? MenuService();
    _initData();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _initData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final categoriesFuture = _menuService.getCategories();
      final itemsFuture = _menuService.getMenuItems();

      final results = await Future.wait([categoriesFuture, itemsFuture]);

      final rawCategories = results[0] as List<CategoryModel>;
      final rawMenuItems = results[1] as List<MenuItemModel>;

      _categories = rawCategories.where((c) => c.isActive).toList();
      final activeCategoryIds = _categories.map((c) => c.id).toSet();

      _menuItems = rawMenuItems.where((item) {
        final catActive = item.categoryIsActive;
        final belongsToActiveCategory =
            item.categoryId == null ||
            activeCategoryIds.contains(item.categoryId);
        return catActive && belongsToActiveCategory;
      }).toList();

      if (_selectedCategoryId != null &&
          !activeCategoryIds.contains(_selectedCategoryId)) {
        _selectedCategoryId = null;
      }

      // Pre-fill cart only if modifying existing order in normal edit mode (NOT add-on mode)
      if (!widget.isAddOnMode && widget.existingOrder != null) {
        for (var item in widget.existingOrder!.items) {
          final matchedMenuItem = _menuItems.firstWhere(
            (m) => m.id == item.menuItemId,
            orElse: () => MenuItemModel(
              id: item.menuItemId,
              name: item.menuItemName,
              price: item.unitPrice,
              isAvailable: true,
            ),
          );
          _cart[item.menuItemId] = CartItemModel(
            menuItem: matchedMenuItem,
            quantity: item.quantity,
            notes: item.notes,
          );
        }
      }

      // Purge any cart items that belong to an inactive category
      _cart.removeWhere((menuItemId, cartItem) {
        final item = cartItem.menuItem;
        if (!item.categoryIsActive) return true;
        if (item.categoryId != null &&
            !activeCategoryIds.contains(item.categoryId))
          return true;
        return false;
      });

      if (mounted) {
        setState(() {
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
          _errorMessage = 'Unable to load menu. Please check your connection.';
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _errorMessage = 'Unable to load menu. Please check your connection.';
          _isLoading = false;
        });
      }
    }
  }

  List<MenuItemModel> get _filteredMenuItems {
    return _menuItems.where((item) {
      final matchesCategory =
          _selectedCategoryId == null || item.categoryId == _selectedCategoryId;
      final matchesSearch =
          _searchQuery.isEmpty ||
          item.name.toLowerCase().contains(_searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    }).toList();
  }

  int get _totalItemsCount => _cart.length;

  int get _totalQuantityCount =>
      _cart.values.fold(0, (sum, i) => sum + i.quantity);

  double get _totalSubtotal =>
      _cart.values.fold(0.0, (sum, i) => sum + i.subtotal);

  void _addItemToCart(MenuItemModel menuItem) {
    if (!menuItem.isAvailable) return;

    setState(() {
      if (_cart.containsKey(menuItem.id)) {
        _cart[menuItem.id]!.quantity += 1;
      } else {
        _cart[menuItem.id] = CartItemModel(menuItem: menuItem, quantity: 1);
      }
    });
  }

  void _decrementCartItem(int menuItemId) {
    setState(() {
      if (_cart.containsKey(menuItemId)) {
        if (_cart[menuItemId]!.quantity > 1) {
          _cart[menuItemId]!.quantity -= 1;
        } else {
          _cart.remove(menuItemId);
        }
      }
    });
  }

  void _removeCartItem(int menuItemId) {
    setState(() {
      _cart.remove(menuItemId);
    });
  }

  void _updateItemNote(int menuItemId, String note) {
    setState(() {
      if (_cart.containsKey(menuItemId)) {
        _cart[menuItemId]!.notes = note;
      }
    });
  }

  void _showCartBottomSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => StatefulBuilder(
        builder: (ctx, setSheetState) {
          final theme = Theme.of(ctx);

          return Container(
            height: MediaQuery.of(ctx).size.height * 0.85,
            decoration: BoxDecoration(
              color: theme.colorScheme.surface,
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(20),
              ),
            ),
            child: Column(
              children: [
                // Sheet Header
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 20,
                    vertical: 16,
                  ),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerHighest,
                    borderRadius: const BorderRadius.vertical(
                      top: Radius.circular(20),
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Cart • Table ${widget.table.tableNumber}',
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      TextButton(
                        onPressed: () => Navigator.pop(ctx),
                        child: const Text('Close'),
                      ),
                    ],
                  ),
                ),

                // Cart Items List
                Expanded(
                  child: _cart.isEmpty
                      ? Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text(
                                'Cart is empty',
                                style: theme.textTheme.titleMedium,
                              ),
                            ],
                          ),
                        )
                      : ListView.separated(
                          padding: const EdgeInsets.all(16),
                          itemCount: _cart.length,
                          separatorBuilder: (c, i) => const Divider(),
                          itemBuilder: (c, index) {
                            final cartItem = _cart.values.toList()[index];
                            final item = cartItem.menuItem;

                            return Card(
                              elevation: 0,
                              color: theme.colorScheme.surfaceContainerHighest
                                  .withOpacity(0.3),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Padding(
                                padding: const EdgeInsets.all(12.0),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      mainAxisAlignment:
                                          MainAxisAlignment.spaceBetween,
                                      children: [
                                        Expanded(
                                          child: Text(
                                            item.name,
                                            style: theme.textTheme.titleMedium
                                                ?.copyWith(
                                                  fontWeight: FontWeight.bold,
                                                ),
                                          ),
                                        ),
                                        TextButton(
                                          onPressed: () {
                                            _removeCartItem(item.id);
                                            setSheetState(() {});
                                            setState(() {});
                                          },
                                          child: const Text(
                                            'Delete',
                                            style: TextStyle(color: Colors.red),
                                          ),
                                        ),
                                      ],
                                    ),
                                    Row(
                                      mainAxisAlignment:
                                          MainAxisAlignment.spaceBetween,
                                      children: [
                                        Text(
                                          '₹${item.price.toStringAsFixed(2)}',
                                          style: theme.textTheme.bodyMedium
                                              ?.copyWith(
                                                color:
                                                    theme.colorScheme.primary,
                                                fontWeight: FontWeight.bold,
                                              ),
                                        ),
                                        // Quantity Controls [-] QTY [+]
                                        Row(
                                          children: [
                                            OutlinedButton(
                                              style: OutlinedButton.styleFrom(
                                                minimumSize: const Size(32, 32),
                                                padding: EdgeInsets.zero,
                                              ),
                                              onPressed: () {
                                                _decrementCartItem(item.id);
                                                setSheetState(() {});
                                                setState(() {});
                                              },
                                              child: const Text(
                                                '-',
                                                style: TextStyle(
                                                  fontSize: 18,
                                                  fontWeight: FontWeight.bold,
                                                ),
                                              ),
                                            ),
                                            Padding(
                                              padding:
                                                  const EdgeInsets.symmetric(
                                                    horizontal: 12,
                                                  ),
                                              child: Text(
                                                '${cartItem.quantity}',
                                                style: theme
                                                    .textTheme
                                                    .titleMedium
                                                    ?.copyWith(
                                                      fontWeight:
                                                          FontWeight.bold,
                                                    ),
                                              ),
                                            ),
                                            ElevatedButton(
                                              style: ElevatedButton.styleFrom(
                                                minimumSize: const Size(32, 32),
                                                padding: EdgeInsets.zero,
                                              ),
                                              onPressed: () {
                                                _addItemToCart(item);
                                                setSheetState(() {});
                                                setState(() {});
                                              },
                                              child: const Text(
                                                '+',
                                                style: TextStyle(
                                                  fontSize: 18,
                                                  fontWeight: FontWeight.bold,
                                                ),
                                              ),
                                            ),
                                          ],
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 8),

                                    // Item Note Input Box
                                    TextFormField(
                                      initialValue: cartItem.notes,
                                      decoration: InputDecoration(
                                        labelText: 'Item Note (optional)',
                                        hintText:
                                            'e.g. Less spicy, Extra cheese',
                                        isDense: true,

                                        border: OutlineInputBorder(
                                          borderRadius: BorderRadius.circular(
                                            8,
                                          ),
                                        ),
                                      ),
                                      onChanged: (val) {
                                        _updateItemNote(item.id, val);
                                      },
                                    ),

                                    const SizedBox(height: 8),
                                    // Quick Note Chips
                                    Wrap(
                                      spacing: 6,
                                      runSpacing: 4,
                                      children: _quickNotePresets.map((preset) {
                                        final currentNotes =
                                            cartItem.notes ?? '';
                                        final isSelected = currentNotes
                                            .contains(preset);
                                        return FilterChip(
                                          label: Text(
                                            preset,
                                            style: const TextStyle(
                                              fontSize: 11,
                                            ),
                                          ),
                                          selected: isSelected,
                                          visualDensity: VisualDensity.compact,
                                          onSelected: (selected) {
                                            String newNote;
                                            if (selected) {
                                              newNote = currentNotes.isEmpty
                                                  ? preset
                                                  : '$currentNotes; $preset';
                                            } else {
                                              newNote = currentNotes
                                                  .replaceAll('$preset; ', '')
                                                  .replaceAll('; $preset', '')
                                                  .replaceAll(preset, '')
                                                  .trim();
                                            }
                                            _updateItemNote(item.id, newNote);
                                            setSheetState(() {});
                                            setState(() {});
                                          },
                                        );
                                      }).toList(),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                ),

                // Sheet Bottom Action Bar
                if (_cart.isNotEmpty)
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surface,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.05),
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
                            Text(
                              'Subtotal:',
                              style: theme.textTheme.titleMedium?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            Text(
                              '₹${_totalSubtotal.toStringAsFixed(2)}',
                              style: theme.textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                                color: theme.colorScheme.primary,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              padding: const EdgeInsets.symmetric(vertical: 14),
                              backgroundColor: theme.colorScheme.primary,
                              foregroundColor: Colors.white,
                            ),
                            onPressed: () {
                              Navigator.pop(ctx);
                              _navigateToReview();
                            },
                            child: const Text(
                              'PROCEED TO REVIEW ORDER',
                              style: TextStyle(fontWeight: FontWeight.bold),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  Future<void> _navigateToReview() async {
    if (_cart.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please select at least one item to place an order.'),
        ),
      );
      return;
    }

    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => OrderReviewPage(
          table: widget.table,
          cartItems: _cart.values.toList(),
          existingOrder: widget.existingOrder,
          isAddOnMode: widget.isAddOnMode,
        ),
      ),
    );

    if (result == 'refresh_menu' && mounted) {
      _initData();
    }
  }

  Future<bool> _showLeaveConfirmationDialog() async {
    if (_cart.isEmpty) return true;
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Leave this order?'),
        content: const Text('Your selected items will be removed.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Stay'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Theme.of(ctx).colorScheme.error,
              foregroundColor: Theme.of(ctx).colorScheme.onError,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Leave'),
          ),
        ],
      ),
    );
    return confirm ?? false;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return PopScope(
      canPop: _cart.isEmpty,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) return;
        final shouldPop = await _showLeaveConfirmationDialog();
        if (shouldPop && context.mounted) {
          Navigator.pop(context);
        }
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            widget.isAddOnMode
                ? 'Add Items to Order • Table ${widget.table.tableNumber}'
                : 'Table ${widget.table.tableNumber} Menu',
          ),
          bottom: PreferredSize(
            preferredSize: const Size.fromHeight(60),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 16.0,
                vertical: 8.0,
              ),
              child: TextField(
                controller: _searchController,
                decoration: InputDecoration(
                  hintText: 'Search food items...',
                  isDense: true,
                  suffixIcon: _searchQuery.isNotEmpty
                      ? TextButton(
                          onPressed: () {
                            _searchController.clear();
                            setState(() {
                              _searchQuery = '';
                            });
                          },
                          child: const Text('Clear'),
                        )
                      : null,
                  filled: true,
                  fillColor: theme.colorScheme.surface,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(30),
                    borderSide: BorderSide.none,
                  ),
                ),
                onChanged: (val) {
                  setState(() {
                    _searchQuery = val;
                  });
                },
              ),
            ),
          ),
        ),
        body: SafeArea(child: _buildBody(theme)),
        bottomNavigationBar: _cart.isNotEmpty
            ? _buildCartBottomBar(theme)
            : null,
      ),
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
            Text('Loading menu categories and items...'),
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
              ElevatedButton(onPressed: _initData, child: const Text('Retry')),
            ],
          ),
        ),
      );
    }

    final items = _filteredMenuItems;

    return Column(
      children: [
        // Category Horizontal Selector
        if (_categories.isNotEmpty)
          Container(
            height: 50,
            color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
            child: ListView.builder(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              itemCount: _categories.length + 1,
              itemBuilder: (ctx, index) {
                if (index == 0) {
                  final isSelected = _selectedCategoryId == null;
                  return Padding(
                    padding: const EdgeInsets.only(right: 8.0),
                    child: ChoiceChip(
                      label: const Text('All Items'),
                      selected: isSelected,
                      onSelected: (_) {
                        setState(() {
                          _selectedCategoryId = null;
                        });
                      },
                    ),
                  );
                }

                final category = _categories[index - 1];
                final isSelected = _selectedCategoryId == category.id;

                return Padding(
                  padding: const EdgeInsets.only(right: 8.0),
                  child: ChoiceChip(
                    label: Text(category.name),
                    selected: isSelected,
                    onSelected: (_) {
                      setState(() {
                        _selectedCategoryId = category.id;
                      });
                    },
                  ),
                );
              },
            ),
          ),

        // Menu Items List Grid
        Expanded(
          child: items.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        'No menu items found.',
                        style: theme.textTheme.titleMedium,
                      ),
                    ],
                  ),
                )
              : ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: items.length,
                  separatorBuilder: (ctx, i) => const SizedBox(height: 12),
                  itemBuilder: (ctx, index) {
                    final item = items[index];
                    final cartItem = _cart[item.id];
                    final inCartQty = cartItem?.quantity ?? 0;
                    final isAvailable = item.isAvailable;

                    return Card(
                      elevation: 1,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                        side: BorderSide(
                          color: inCartQty > 0
                              ? theme.colorScheme.primary
                              : Colors.transparent,
                          width: inCartQty > 0 ? 2.0 : 0,
                        ),
                      ),
                      color: isAvailable ? null : Colors.grey[100],
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            // Item Details
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          item.name,
                                          style: theme.textTheme.titleMedium
                                              ?.copyWith(
                                                fontWeight: FontWeight.bold,
                                                color: isAvailable
                                                    ? null
                                                    : Colors.grey[600],
                                              ),
                                        ),
                                      ),
                                      if (!isAvailable)
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 8,
                                            vertical: 2,
                                          ),
                                          decoration: BoxDecoration(
                                            color: Colors.red[100],
                                            borderRadius: BorderRadius.circular(
                                              6,
                                            ),
                                          ),
                                          child: Text(
                                            'Unavailable',
                                            style: TextStyle(
                                              fontSize: 11,
                                              fontWeight: FontWeight.bold,
                                              color: Colors.red[800],
                                            ),
                                          ),
                                        ),
                                    ],
                                  ),
                                  if (item.description != null &&
                                      item.description!.isNotEmpty)
                                    Padding(
                                      padding: const EdgeInsets.only(top: 4.0),
                                      child: Text(
                                        item.description!,
                                        style: theme.textTheme.bodySmall
                                            ?.copyWith(
                                              color: theme
                                                  .colorScheme
                                                  .onSurfaceVariant,
                                            ),
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  const SizedBox(height: 8),
                                  Text(
                                    '₹${item.price.toStringAsFixed(2)}',
                                    style: theme.textTheme.titleMedium
                                        ?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: isAvailable
                                              ? theme.colorScheme.primary
                                              : Colors.grey[600],
                                        ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 16),

                            // Add / Qty Controls
                            if (!isAvailable)
                              const Text(
                                'Out of Stock',
                                style: TextStyle(
                                  color: Colors.grey,
                                  fontSize: 12,
                                ),
                              )
                            else if (inCartQty == 0)
                              ElevatedButton(
                                style: ElevatedButton.styleFrom(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 16,
                                    vertical: 10,
                                  ),
                                ),
                                onPressed: () => _addItemToCart(item),
                                child: const Text('ADD'),
                              )
                            else
                              Container(
                                decoration: BoxDecoration(
                                  color: theme.colorScheme.primaryContainer,
                                  borderRadius: BorderRadius.circular(20),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    TextButton(
                                      style: TextButton.styleFrom(
                                        minimumSize: const Size(36, 36),
                                        padding: EdgeInsets.zero,
                                      ),
                                      onPressed: () =>
                                          _decrementCartItem(item.id),
                                      child: const Text(
                                        '-',
                                        style: TextStyle(
                                          fontSize: 18,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                    ),
                                    Text(
                                      '$inCartQty',
                                      style: theme.textTheme.titleMedium
                                          ?.copyWith(
                                            fontWeight: FontWeight.bold,
                                          ),
                                    ),
                                    TextButton(
                                      style: TextButton.styleFrom(
                                        minimumSize: const Size(36, 36),
                                        padding: EdgeInsets.zero,
                                      ),
                                      onPressed: () => _addItemToCart(item),
                                      child: const Text(
                                        '+',
                                        style: TextStyle(
                                          fontSize: 18,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _buildCartBottomBar(ThemeData theme) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.12),
            blurRadius: 10,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: SafeArea(
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            InkWell(
              onTap: _showCartBottomSheet,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(
                        '$_totalItemsCount Items ($_totalQuantityCount qty)',
                        style: theme.textTheme.labelMedium?.copyWith(
                          color: theme.colorScheme.secondary,
                        ),
                      ),
                    ],
                  ),
                  Text(
                    '₹${_totalSubtotal.toStringAsFixed(2)}',
                    style: theme.textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                ],
              ),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 14,
                ),
                backgroundColor: theme.colorScheme.primary,
                foregroundColor: Colors.white,
              ),
              onPressed: _navigateToReview,
              child: const Text(
                'REVIEW ORDER',
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
