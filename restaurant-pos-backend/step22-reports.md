# Step 22 — Reports & Dashboard Backend Documentation

## Purpose
Provides comprehensive backend REST APIs for admin dashboard metrics and reporting. All calculations are performed dynamically against existing transactional database entities (`orders`, `order_items`, `bills`, `payments`, `inventory_items`, `restaurant_tables`, `users`, `customers`). No separate report or dashboard tables are used.

---

## Security & Tenant Isolation
- **Role Restriction**: Accessible **ONLY** by users with `ADMIN` role. Requests by `WAITER` or `KITCHEN` users return `403 Forbidden`. Unauthenticated requests return `401 Unauthorized`.
- **Tenant Isolation**: All queries enforce tenant scoping via `SecurityUtils.getCurrentRestaurantId()`. Client-supplied `restaurantId` query parameters are ignored.

---

## Revenue Definition
- **Revenue** strictly represents money collected from **successful payments** (`payment_status = 'PAID'`).
- Pending payments, failed payments, unpaid bills, and cancelled orders do **NOT** contribute to revenue.

---

## Date Range Behavior
- Query parameters `from` and `to` expect dates formatted as `YYYY-MM-DD`.
- `from` date is filtered starting at `00:00:00.000` (inclusive).
- `to` date is filtered ending at `23:59:59.999` (inclusive).
- Validation: `from` must be on or before `to`. Invalid date ranges return `HTTP 400 Bad Request`.
- Dashboard summary (`GET /api/reports/dashboard`) automatically uses the current server date.

---

## API Endpoints List

### 1. Today's Dashboard Summary
`GET /api/reports/dashboard`
Returns current day metrics (orders, revenue, tax, discount, average order value, payment method breakdown, low stock items, active tables).

### 2. Sales Report
`GET /api/reports/sales?from=YYYY-MM-DD&to=YYYY-MM-DD`
Returns total orders, completed/cancelled counts, revenue, tax, discount, AOV, and payment method amounts within `[from, to]`.

### 3. Daily Sales Breakdown
`GET /api/reports/sales/daily?from=YYYY-MM-DD&to=YYYY-MM-DD`
Returns an array containing sales metrics for every single day in the date range (includes days with zero sales).

### 4. Payment Method Breakdown
`GET /api/reports/payments?from=YYYY-MM-DD&to=YYYY-MM-DD`
Returns transaction counts and totals grouped by `CASH`, `UPI`, and `CARD`.

### 5. Order Statistics
`GET /api/reports/orders?from=YYYY-MM-DD&to=YYYY-MM-DD`
Returns order counts grouped by status (`NEW`, `ACCEPTED`, `PREPARING`, `READY`, `COMPLETED`, `CANCELLED`).

### 6. Order Type Report
`GET /api/reports/orders/types?from=YYYY-MM-DD&to=YYYY-MM-DD`
Returns order counts, completed/cancelled breakdown, and revenue grouped by `DINE_IN` and `TAKEAWAY`.

### 7. Top Selling Menu Items
`GET /api/reports/top-items?from=YYYY-MM-DD&to=YYYY-MM-DD&limit=10`
Returns top selling items sorted by quantity sold. Uses historical `OrderItem.unit_price`. Optional `limit` parameter (default 10, max 50).

### 8. Category Sales Report
`GET /api/reports/categories?from=YYYY-MM-DD&to=YYYY-MM-DD`
Returns category-level quantity sold and revenue sorted by revenue descending. Uses historical `OrderItem.unit_price`.

### 9. Inventory Summary
`GET /api/reports/inventory`
Returns total active items, low stock item count (`currentQuantity <= minimumQuantity`), out of stock item count (`currentQuantity <= 0`), and total inventory valuation (`currentQuantity * costPerUnit`).

### 10. Table Summary
`GET /api/reports/tables`
Returns total tables, available tables, occupied tables, and billing tables for the restaurant.

### 11. Staff Summary
`GET /api/reports/staff`
Returns total staff, active staff, inactive staff, and role breakdown (`ADMIN`, `WAITER`, `KITCHEN`).

### 12. Customer Summary
`GET /api/reports/customers`
Returns total customer count and new customers registered today.
