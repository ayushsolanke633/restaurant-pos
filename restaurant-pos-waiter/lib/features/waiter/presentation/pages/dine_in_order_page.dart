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
  final Map<String, CartItemModel> _cart = {}; // itemKey -> CartItemModel
  final Map<String, int> _originalQuantities = {}; // itemKey -> originalQuantity for existing submitted orders

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
      _originalQuantities.clear();
      if (!widget.isAddOnMode && widget.existingOrder != null) {
        for (var item in widget.existingOrder!.items) {
          final key = '${item.menuItemId}_${item.variationId ?? 'base'}';
          _originalQuantities[key] =
              (_originalQuantities[key] ?? 0) + item.quantity;
          final matchedMenuItem = _menuItems.firstWhere(
            (m) => m.id == item.menuItemId,
            orElse: () => MenuItemModel(
              id: item.menuItemId,
              name: item.menuItemName,
              price: item.unitPrice,
              isAvailable: true,
            ),
          );
          MenuItemVariationModel? matchedVar;
          if (item.variationId != null) {
            final vars = matchedMenuItem.variations
                .where((v) => v.id == item.variationId)
                .toList();
            if (vars.isNotEmpty) {
              matchedVar = vars.first;
            } else {
              matchedVar = MenuItemVariationModel(
                id: item.variationId!,
                name: item.variationName ?? 'Variation',
                price: item.unitPrice,
                isAvailable: true,
              );
            }
          }
          _cart[key] = CartItemModel(
            menuItem: matchedMenuItem,
            variation: matchedVar,
            quantity: item.quantity,
            notes: item.notes,
          );
        }
      }

      // Purge any cart items that belong to an inactive category
      _cart.removeWhere((itemKey, cartItem) {
        final item = cartItem.menuItem;
        if (!item.categoryIsActive) return true;
        if (item.categoryId != null &&
            !activeCategoryIds.contains(item.categoryId)) {
          return true;
        }
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

  int _getMenuItemCartQuantity(MenuItemModel item) {
    return _cart.values
        .where((c) => c.menuItem.id == item.id)
        .fold(0, (sum, c) => sum + c.quantity);
  }

  void _onMenuItemTapped(MenuItemModel menuItem) {
    if (!menuItem.isAvailable) return;

    if (menuItem.variations.isEmpty) {
      _addItemWithVariation(menuItem, null);
    } else if (menuItem.variations.length == 1) {
      final variation = menuItem.variations.first;
      if (variation.isAvailable) {
        _addItemWithVariation(menuItem, variation);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${menuItem.name} (${variation.name}) is out of stock.'),
          ),
        );
      }
    } else {
      _showVariationSelectionSheet(menuItem);
    }
  }

  void _addItemWithVariation(
    MenuItemModel menuItem,
    MenuItemVariationModel? variation,
  ) {
    if (!menuItem.isAvailable) return;
    if (variation != null && !variation.isAvailable) return;

    final dummy = CartItemModel(
      menuItem: menuItem,
      variation: variation,
      quantity: 1,
    );
    final key = dummy.itemKey;

    setState(() {
      if (_cart.containsKey(key)) {
        _cart[key]!.quantity += 1;
      } else {
        _cart[key] = CartItemModel(
          menuItem: menuItem,
          variation: variation,
          quantity: 1,
        );
      }
    });
  }

  void _decrementCartItem(String itemKey) {
    setState(() {
      if (_cart.containsKey(itemKey)) {
        final originalQty = _originalQuantities[itemKey] ?? 0;
        if (originalQty > 0 && _cart[itemKey]!.quantity <= originalQty) {
          // Cannot decrease existing submitted item quantity
          return;
        }
        if (_cart[itemKey]!.quantity > 1) {
          _cart[itemKey]!.quantity -= 1;
        } else if (originalQty == 0) {
          _cart.remove(itemKey);
        }
      }
    });
  }

  void _removeCartItem(String itemKey) {
    setState(() {
      final originalQty = _originalQuantities[itemKey] ?? 0;
      if (originalQty > 0) {
        // Cannot delete existing submitted item
        return;
      }
      _cart.remove(itemKey);
    });
  }

  void _updateItemNote(String itemKey, String note) {
    setState(() {
      if (_cart.containsKey(itemKey)) {
        _cart[itemKey]!.notes = note;
      }
    });
  }

  void _showVariationSelectionSheet(MenuItemModel menuItem) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (sheetCtx, setSheetState) {
          final theme = Theme.of(sheetCtx);

          return Container(
            padding: const EdgeInsets.only(bottom: 24),
            decoration: BoxDecoration(
              color: theme.colorScheme.surface,
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(20),
              ),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Header
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
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              menuItem.name,
                              style: theme.textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Select portion / size',
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close),
                        onPressed: () => Navigator.pop(sheetCtx),
                      ),
                    ],
                  ),
                ),

                // Variations List
                Flexible(
                  child: ListView.separated(
                    shrinkWrap: true,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 12,
                    ),
                    itemCount: menuItem.variations.length,
                    separatorBuilder: (c, i) => const Divider(height: 1),
                    itemBuilder: (c, index) {
                      final variation = menuItem.variations[index];
                      final itemKey = '${menuItem.id}_${variation.id}';
                      final inCartQty = _cart[itemKey]?.quantity ?? 0;
                      final originalQty = _originalQuantities[itemKey] ?? 0;
                      final isAvailable = variation.isAvailable;
                      final canDecrement =
                          originalQty == 0 || inCartQty > originalQty;

                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    variation.name,
                                    style: theme.textTheme.titleMedium?.copyWith(
                                      fontWeight: FontWeight.bold,
                                      color: isAvailable
                                          ? null
                                          : Colors.grey[600],
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '₹${variation.price.toStringAsFixed(2)}',
                                    style: theme.textTheme.bodyMedium?.copyWith(
                                      color: isAvailable
                                          ? theme.colorScheme.primary
                                          : Colors.grey[600],
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            if (!isAvailable)
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 8,
                                  vertical: 4,
                                ),
                                decoration: BoxDecoration(
                                  color: Colors.red[50],
                                  borderRadius: BorderRadius.circular(6),
                                  border: Border.all(color: Colors.red[200]!),
                                ),
                                child: Text(
                                  'Out of Stock',
                                  style: TextStyle(
                                    color: Colors.red[700],
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              )
                            else if (inCartQty == 0)
                              ElevatedButton(
                                onPressed: () {
                                  _addItemWithVariation(menuItem, variation);
                                  setSheetState(() {});
                                  setState(() {});
                                },
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
                                      onPressed: canDecrement
                                          ? () {
                                              _decrementCartItem(itemKey);
                                              setSheetState(() {});
                                              setState(() {});
                                            }
                                          : null,
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
                                      onPressed: () {
                                        _addItemWithVariation(
                                          menuItem,
                                          variation,
                                        );
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
                              ),
                          ],
                        ),
                      );
                    },
                  ),
                ),

                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      backgroundColor: theme.colorScheme.primary,
                      foregroundColor: Colors.white,
                    ),
                    onPressed: () => Navigator.pop(sheetCtx),
                    child: const Text(
                      'DONE',
                      style: TextStyle(fontWeight: FontWeight.bold),
                    ),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
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
                            final itemKey = cartItem.itemKey;
                            final originalQty =
                                _originalQuantities[itemKey] ?? 0;
                            final isExistingSubmittedItem = originalQty > 0;
                            final canDecrement =
                                !isExistingSubmittedItem ||
                                cartItem.quantity > originalQty;

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
                                            cartItem.displayName,
                                            style: theme.textTheme.titleMedium
                                                ?.copyWith(
                                                  fontWeight: FontWeight.bold,
                                                ),
                                          ),
                                        ),
                                        if (!isExistingSubmittedItem)
                                          TextButton(
                                            onPressed: () {
                                              _removeCartItem(itemKey);
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
                                          '₹${cartItem.unitPrice.toStringAsFixed(2)}',
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
                                              onPressed: canDecrement
                                                  ? () {
                                                      _decrementCartItem(itemKey);
                                                      setSheetState(() {});
                                                      setState(() {});
                                                    }
                                                  : null,
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
                                                _addItemWithVariation(
                                                  cartItem.menuItem,
                                                  cartItem.variation,
                                                );
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
                                        _updateItemNote(itemKey, val);
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
                                            _updateItemNote(itemKey, newNote);
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
                    final inCartQty = _getMenuItemCartQuantity(item);
                    final isAvailable = item.isAvailable;
                    final hasVariations = item.hasVariations;

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
                                  Row(
                                    children: [
                                      Text(
                                        hasVariations
                                            ? 'From ₹${item.minPrice.toStringAsFixed(2)}'
                                            : '₹${item.price.toStringAsFixed(2)}',
                                        style: theme.textTheme.titleMedium
                                            ?.copyWith(
                                              fontWeight: FontWeight.bold,
                                              color: isAvailable
                                                  ? theme.colorScheme.primary
                                                  : Colors.grey[600],
                                            ),
                                      ),
                                      if (hasVariations) ...[
                                        const SizedBox(width: 8),
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 6,
                                            vertical: 2,
                                          ),
                                          decoration: BoxDecoration(
                                            color: theme.colorScheme.primaryContainer
                                                .withOpacity(0.6),
                                            borderRadius:
                                                BorderRadius.circular(4),
                                          ),
                                          child: Text(
                                            '${item.variations.length} portions',
                                            style: TextStyle(
                                              fontSize: 11,
                                              color: theme.colorScheme
                                                  .onPrimaryContainer,
                                              fontWeight: FontWeight.w500,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ],
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
                            else if (hasVariations && item.variations.length > 1)
                              inCartQty == 0
                                  ? ElevatedButton(
                                      style: ElevatedButton.styleFrom(
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 16,
                                          vertical: 10,
                                        ),
                                      ),
                                      onPressed: () => _onMenuItemTapped(item),
                                      child: const Text('SELECT'),
                                    )
                                  : OutlinedButton.icon(
                                      style: OutlinedButton.styleFrom(
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 12,
                                          vertical: 8,
                                        ),
                                        side: BorderSide(
                                          color: theme.colorScheme.primary,
                                        ),
                                      ),
                                      onPressed: () =>
                                          _showVariationSelectionSheet(item),
                                      icon: const Icon(Icons.check, size: 16),
                                      label: Text(
                                        '$inCartQty in cart',
                                        style: const TextStyle(
                                          fontWeight: FontWeight.bold,
                                        ),
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
                                onPressed: () => _onMenuItemTapped(item),
                                child: const Text('ADD'),
                              )
                            else ...[
                              // Single variation or 0 variations
                              Builder(
                                builder: (context) {
                                  final singleVariation = hasVariations
                                      ? item.variations.first
                                      : null;
                                  final itemKey =
                                      '${item.id}_${singleVariation?.id ?? 'base'}';
                                  final originalQty =
                                      _originalQuantities[itemKey] ?? 0;
                                  final canDecrement =
                                      originalQty == 0 ||
                                      inCartQty > originalQty;

                                  return Container(
                                    decoration: BoxDecoration(
                                      color: theme
                                          .colorScheme.primaryContainer,
                                      borderRadius:
                                          BorderRadius.circular(20),
                                    ),
                                    child: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        TextButton(
                                          style: TextButton.styleFrom(
                                            minimumSize: const Size(36, 36),
                                            padding: EdgeInsets.zero,
                                          ),
                                          onPressed: canDecrement
                                              ? () =>
                                                  _decrementCartItem(itemKey)
                                              : null,
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
                                          onPressed: () =>
                                              _onMenuItemTapped(item),
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
                                  );
                                },
                              ),
                            ],
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
