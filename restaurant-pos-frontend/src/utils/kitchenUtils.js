/**
 * Utility helper functions for Kitchen Display System (KDS)
 */

/**
 * Calculates combined dish quantities and order breakdowns
 * for ACCEPTED and PREPARING orders only.
 * 
 * Rules:
 * - INCLUDE ONLY: ACCEPTED, PREPARING orders
 * - EXCLUDE: NEW, READY, COMPLETED, CANCELLED orders
 * - Group by menuItemId
 * - Sum total quantities
 * - Preserve order breakdown (orderId, orderType, tableNumber, quantity, notes)
 * 
 * @param {Array} orders - List of active kitchen orders from state
 * @returns {Array} List of grouped combined dish objects
 */
export const calculateCombinedDishes = (orders = []) => {
  if (!Array.isArray(orders) || orders.length === 0) {
    return [];
  }

  // Filter ONLY ACCEPTED and PREPARING orders
  const activeOrders = orders.filter(
    (order) => order && (order.status === 'ACCEPTED' || order.status === 'PREPARING')
  );

  const combinedMap = new Map();

  activeOrders.forEach((order) => {
    if (!order.items || !Array.isArray(order.items)) return;

    order.items.forEach((item) => {
      // Grouping key: use menuItemId + variation (or fallback to menuItemName)
      const varSuffix = item.variationId ? `_var_${item.variationId}` : (item.variationName ? `_var_${item.variationName}` : '');
      const key = `${item.menuItemId || item.menuItemName || 'unknown'}${varSuffix}`;

      if (!combinedMap.has(key)) {
        const dishDisplayName = item.variationName
          ? `${item.menuItemName || 'Unnamed Dish'} (${item.variationName})`
          : (item.menuItemName || 'Unnamed Dish');

        combinedMap.set(key, {
          key,
          menuItemId: item.menuItemId,
          variationId: item.variationId || null,
          variationName: item.variationName || null,
          menuItemName: dishDisplayName,
          categoryName: item.categoryName || '',
          totalQuantity: 0,
          orders: [],
        });
      }

      const entry = combinedMap.get(key);
      const itemQty = Number(item.quantity) || 0;
      entry.totalQuantity += itemQty;

      entry.orders.push({
        orderId: order.orderId,
        orderType: order.orderType,
        tableNumber: order.tableNumber,
        tableId: order.tableId,
        quantity: itemQty,
        notes: item.notes || null,
        orderNotes: order.notes || null,
        status: order.status,
      });
    });
  });

  // Convert map values to array and sort by total quantity descending
  return Array.from(combinedMap.values()).sort((a, b) => b.totalQuantity - a.totalQuantity);
};
