import test from 'node:test';
import assert from 'node:assert/strict';

/**
 * Menu Item Variation Form Validator Simulator
 * Matches validation logic in MenuItemModal.jsx
 */
function validateMenuItemForm({ name, categoryId, price, hasVariations, variations, description }) {
  const errors = {};
  const trimmedName = (name || '').trim();

  if (!trimmedName) {
    errors.name = 'Menu item name is required.';
  } else if (trimmedName.length > 150) {
    errors.name = 'Name must not exceed 150 characters.';
  }

  if (!categoryId) {
    errors.categoryId = 'Please select a category.';
  }

  if (hasVariations) {
    if (!variations || variations.length === 0) {
      errors.variations = 'Please add at least one variation or uncheck variations.';
    } else {
      const namesSeen = new Set();
      const rowErrors = [];

      variations.forEach((v, idx) => {
        const vErr = {};
        const vName = (v.name || '').trim();

        if (!vName) {
          vErr.name = 'Name required';
        } else if (vName.length > 100) {
          vErr.name = 'Max 100 chars';
        } else {
          const lower = vName.toLowerCase();
          if (namesSeen.has(lower)) {
            vErr.name = 'Duplicate name';
          } else {
            namesSeen.add(lower);
          }
        }

        if (v.price === '' || v.price === null || v.price === undefined) {
          vErr.price = 'Price required';
        } else {
          const num = Number(v.price);
          if (isNaN(num)) {
            vErr.price = 'Invalid';
          } else if (num < 0) {
            vErr.price = 'Must be ≥ 0';
          }
        }

        if (Object.keys(vErr).length > 0) {
          rowErrors[idx] = vErr;
        }
      });

      if (rowErrors.length > 0) {
        errors.variationRows = rowErrors;
      }
    }
  } else {
    if (price === '' || price === null || price === undefined) {
      errors.price = 'Price is required.';
    } else {
      const num = Number(price);
      if (isNaN(num)) {
        errors.price = 'Please enter a valid numeric price.';
      } else if (num < 0) {
        errors.price = 'Price must be greater than or equal to 0.';
      }
    }
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

/**
 * Menu Item Cart & Order Selection Simulator
 * Matches OrderModal.jsx and AddItemsModal.jsx
 */
class OrderCartSimulator {
  constructor({ isAdmin = true, isEditing = false, initialItems = [] } = {}) {
    this.isAdmin = isAdmin;
    this.isEditing = isEditing;
    this.cartItems = [];
    this.initialItemQuantities = {};

    if (initialItems.length > 0) {
      this.cartItems = initialItems.map((item) => ({
        menuItemId: item.menuItemId,
        variationId: item.variationId || null,
        variationName: item.variationName || null,
        name: item.name || item.menuItemName || 'Item',
        unitPrice: Number(item.unitPrice || 0),
        quantity: item.quantity || 1,
        notes: item.notes || '',
      }));

      this.cartItems.forEach((i) => {
        const key = this.getItemKey(i);
        this.initialItemQuantities[key] = (this.initialItemQuantities[key] || 0) + i.quantity;
      });
    }
  }

  getItemKey(item) {
    return `${item.menuItemId}_${item.variationId || 'base'}`;
  }

  handleItemClick(menuItem) {
    const vars = Array.isArray(menuItem.variations) ? menuItem.variations : [];
    if (vars.length > 1) {
      // Must open dialog
      return { requiresModal: true, menuItem };
    } else if (vars.length === 1) {
      // Auto-select
      this.addItemWithVariation(menuItem, vars[0]);
      return { requiresModal: false, selectedVariation: vars[0] };
    } else {
      // Base item
      this.addItemWithVariation(menuItem, null);
      return { requiresModal: false, selectedVariation: null };
    }
  }

  addItemWithVariation(menuItem, variation) {
    const variationId = variation ? variation.id : null;
    const variationName = variation ? variation.name : null;
    const unitPrice = variation ? Number(variation.price) : Number(menuItem.price || 0);

    const existingIndex = this.cartItems.findIndex(
      (i) => i.menuItemId === menuItem.id && (i.variationId || null) === (variationId || null)
    );

    if (existingIndex > -1) {
      this.cartItems[existingIndex].quantity += 1;
    } else {
      this.cartItems.push({
        menuItemId: menuItem.id,
        variationId,
        variationName,
        name: menuItem.name,
        unitPrice,
        quantity: 1,
        notes: '',
      });
    }
  }

  updateQuantity(itemKey, delta) {
    const item = this.cartItems.find((i) => this.getItemKey(i) === itemKey);
    if (!item) return;

    const initialQty = this.isEditing ? (this.initialItemQuantities[itemKey] || 0) : 0;
    if (!this.isAdmin && this.isEditing && initialQty > 0 && delta < 0 && item.quantity <= initialQty) {
      throw new Error('Access Denied: Only ADMIN can decrease quantity of existing items.');
    }

    const newQty = item.quantity + delta;
    if (newQty <= 0) {
      if (!this.isAdmin && this.isEditing && initialQty > 0) {
        throw new Error('Access Denied: Only ADMIN can delete existing items.');
      }
      this.cartItems = this.cartItems.filter((i) => this.getItemKey(i) !== itemKey);
    } else {
      item.quantity = newQty;
    }
  }

  removeItem(itemKey) {
    const initialQty = this.isEditing ? (this.initialItemQuantities[itemKey] || 0) : 0;
    if (!this.isAdmin && this.isEditing && initialQty > 0) {
      throw new Error('Access Denied: Only ADMIN can remove existing items.');
    }
    this.cartItems = this.cartItems.filter((i) => this.getItemKey(i) !== itemKey);
  }

  getTotal() {
    return this.cartItems.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  }

  getSubmissionPayload() {
    return {
      items: this.cartItems.map((item) => ({
        menuItemId: item.menuItemId,
        variationId: item.variationId || null,
        quantity: item.quantity,
        notes: item.notes || null,
      })),
    };
  }
}

/**
 * Display format helper
 */
function formatItemDisplayName(item) {
  const baseName = item.menuItemName || item.name || 'Item';
  return item.variationName ? `${baseName} (${item.variationName})` : baseName;
}

// -------------------------------------------------------------
// TESTS
// -------------------------------------------------------------

test('1. Menu item without variations validates base price correctly', () => {
  const valid = validateMenuItemForm({
    name: 'Dal Fry',
    categoryId: 1,
    price: '110.00',
    hasVariations: false,
    variations: [],
  });
  assert.equal(valid.isValid, true);

  const missingPrice = validateMenuItemForm({
    name: 'Dal Fry',
    categoryId: 1,
    price: '',
    hasVariations: false,
  });
  assert.equal(missingPrice.isValid, false);
  assert.equal(missingPrice.errors.price, 'Price is required.');

  const negativePrice = validateMenuItemForm({
    name: 'Dal Fry',
    categoryId: 1,
    price: '-10',
    hasVariations: false,
  });
  assert.equal(negativePrice.isValid, false);
  assert.equal(negativePrice.errors.price, 'Price must be greater than or equal to 0.');
});

test('2. Menu item with variations validates variation names, prices, and prevents duplicates', () => {
  // Missing variation names & negative price
  const invalid = validateMenuItemForm({
    name: 'Chicken Biryani',
    categoryId: 2,
    hasVariations: true,
    variations: [
      { name: '', price: '120.00' },
      { name: 'Full', price: '-50.00' },
    ],
  });
  assert.equal(invalid.isValid, false);
  assert.equal(invalid.errors.variationRows[0].name, 'Name required');
  assert.equal(invalid.errors.variationRows[1].price, 'Must be ≥ 0');

  // Duplicate variation names (case-insensitive)
  const duplicate = validateMenuItemForm({
    name: 'Chicken Biryani',
    categoryId: 2,
    hasVariations: true,
    variations: [
      { name: 'Half', price: '120.00' },
      { name: 'half', price: '130.00' },
    ],
  });
  assert.equal(duplicate.isValid, false);
  assert.equal(duplicate.errors.variationRows[1].name, 'Duplicate name');

  // Valid variations
  const valid = validateMenuItemForm({
    name: 'Chicken Biryani',
    categoryId: 2,
    hasVariations: true,
    variations: [
      { name: 'Half', price: '120.00' },
      { name: 'Full', price: '220.00' },
    ],
  });
  assert.equal(valid.isValid, true);
});

test('3. Single variation auto-selection adds to cart without opening selection dialog', () => {
  const cart = new OrderCartSimulator();
  const singleVarItem = {
    id: 10,
    name: 'Special Thali',
    variations: [
      { id: 101, name: 'Standard', price: '180.00', isAvailable: true },
    ],
  };

  const result = cart.handleItemClick(singleVarItem);
  assert.equal(result.requiresModal, false);
  assert.equal(result.selectedVariation.name, 'Standard');

  assert.equal(cart.cartItems.length, 1);
  assert.equal(cart.cartItems[0].menuItemId, 10);
  assert.equal(cart.cartItems[0].variationId, 101);
  assert.equal(cart.cartItems[0].variationName, 'Standard');
  assert.equal(cart.cartItems[0].unitPrice, 180.00);
});

test('4. Multiple variations opens selection dialog; Half and Full are added as separate cart lines', () => {
  const cart = new OrderCartSimulator();
  const biryani = {
    id: 20,
    name: 'Mutton Biryani',
    variations: [
      { id: 201, name: 'Half', price: '160.00', isAvailable: true },
      { id: 202, name: 'Full', price: '300.00', isAvailable: true },
    ],
  };

  // Click biryani -> prompts modal
  const checkModal = cart.handleItemClick(biryani);
  assert.equal(checkModal.requiresModal, true);
  assert.equal(checkModal.menuItem.id, 20);

  // User selects Half from modal
  cart.addItemWithVariation(biryani, biryani.variations[0]);
  assert.equal(cart.cartItems.length, 1);
  assert.equal(cart.cartItems[0].variationName, 'Half');
  assert.equal(cart.cartItems[0].quantity, 1);
  assert.equal(cart.cartItems[0].unitPrice, 160.00);

  // User clicks biryani again and selects Full from modal
  cart.addItemWithVariation(biryani, biryani.variations[1]);
  assert.equal(cart.cartItems.length, 2);
  assert.equal(cart.cartItems[1].variationName, 'Full');
  assert.equal(cart.cartItems[1].quantity, 1);
  assert.equal(cart.cartItems[1].unitPrice, 300.00);

  // User adds another Half -> aggregates quantity on Half line only
  cart.addItemWithVariation(biryani, biryani.variations[0]);
  assert.equal(cart.cartItems.length, 2);
  assert.equal(cart.cartItems[0].quantity, 2);
  assert.equal(cart.cartItems[1].quantity, 1);

  // Total amount: (2 * 160) + (1 * 300) = 320 + 300 = 620
  assert.equal(cart.getTotal(), 620.00);

  // Submission payload has explicit variationIds
  const payload = cart.getSubmissionPayload();
  assert.deepEqual(payload.items, [
    { menuItemId: 20, variationId: 201, quantity: 2, notes: null },
    { menuItemId: 20, variationId: 202, quantity: 1, notes: null },
  ]);
});

test('5. Item without variations adds normally with null variation fields', () => {
  const cart = new OrderCartSimulator();
  const regularItem = {
    id: 30,
    name: 'Jeera Rice',
    price: '90.00',
    variations: [],
  };

  const result = cart.handleItemClick(regularItem);
  assert.equal(result.requiresModal, false);
  assert.equal(cart.cartItems.length, 1);
  assert.equal(cart.cartItems[0].menuItemId, 30);
  assert.equal(cart.cartItems[0].variationId, null);
  assert.equal(cart.cartItems[0].variationName, null);
  assert.equal(cart.cartItems[0].unitPrice, 90.00);
});

test('6. Client Change 1: Waiter cannot decrease or remove existing variation lines; Admin can', () => {
  const initialOrderItems = [
    { menuItemId: 20, variationId: 201, variationName: 'Half', unitPrice: 160.00, quantity: 2 },
    { menuItemId: 20, variationId: 202, variationName: 'Full', unitPrice: 300.00, quantity: 1 },
  ];

  // Waiter session
  const waiterCart = new OrderCartSimulator({
    isAdmin: false,
    isEditing: true,
    initialItems: initialOrderItems,
  });

  const halfKey = '20_201';
  const fullKey = '20_202';

  // Waiter attempts to decrease Half from 2 to 1 -> BLOCKED
  assert.throws(
    () => waiterCart.updateQuantity(halfKey, -1),
    /Only ADMIN can decrease quantity/
  );

  // Waiter attempts to remove Full line -> BLOCKED
  assert.throws(
    () => waiterCart.removeItem(fullKey),
    /Only ADMIN can remove existing items/
  );

  // Waiter CAN increase quantity (adds new portion)
  waiterCart.updateQuantity(halfKey, 1);
  assert.equal(waiterCart.cartItems.find((i) => waiterCart.getItemKey(i) === halfKey).quantity, 3);

  // Admin session
  const adminCart = new OrderCartSimulator({
    isAdmin: true,
    isEditing: true,
    initialItems: initialOrderItems,
  });

  // Admin decreases Half from 2 to 1 -> ALLOWED
  adminCart.updateQuantity(halfKey, -1);
  assert.equal(adminCart.cartItems.find((i) => adminCart.getItemKey(i) === halfKey).quantity, 1);

  // Admin removes Full line -> ALLOWED
  adminCart.removeItem(fullKey);
  assert.equal(adminCart.cartItems.length, 1);
  assert.equal(adminCart.cartItems[0].variationName, 'Half');
});

test('7. Order details, receipts, and KDS cards format item names with variation snapshot', () => {
  const itemWithVar = {
    menuItemId: 10,
    menuItemName: 'Chicken Biryani',
    variationName: 'Half',
    quantity: 2,
    unitPrice: 120.00,
  };

  const itemWithoutVar = {
    menuItemId: 15,
    menuItemName: 'Steam Rice',
    variationName: null,
    quantity: 1,
    unitPrice: 70.00,
  };

  assert.equal(formatItemDisplayName(itemWithVar), 'Chicken Biryani (Half)');
  assert.equal(formatItemDisplayName(itemWithoutVar), 'Steam Rice');
});
