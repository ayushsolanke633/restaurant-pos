# Step 23 — Settings & Restaurant Configuration Backend Documentation

## Purpose
Provides backend REST APIs to manage restaurant-level POS configuration settings (`tax_enabled`, `default_tax_rate`, `service_charge_enabled`, `default_service_charge_rate`, `currency`, `timezone`, `receipt_header`, `receipt_footer`, `invoice_prefix`, `auto_accept_orders`). Settings are 1-to-1 per restaurant (`restaurant_id` is UNIQUE).

---

## Key Principles & Behavior
1. **1-to-1 Relationship**: Every restaurant has exactly one `RestaurantSettings` record.
2. **Lazy Initialization**: If settings do not exist when requested or updated, default settings are lazily initialized automatically:
   - `currency`: `"INR"`
   - `timezone`: `"Asia/Kolkata"`
   - `tax_enabled`: `true`
   - `default_tax_rate`: `0.00`
   - `service_charge_enabled`: `false`
   - `default_service_charge_rate`: `0.00`
   - `receipt_header`: Restaurant name
   - `receipt_footer`: `"Thank you for visiting!"`
   - `invoice_prefix`: `"INV"`
   - `auto_accept_orders`: `false`
3. **Immutability of Past Data**: Updating settings only affects future bills and orders. Existing orders, bills, and payments remain untouched.
4. **Validation Rules**:
   - `default_tax_rate` and `default_service_charge_rate` must be between `0.00` and `100.00` (inclusive).
   - `currency` must be a valid 3-character ISO currency code (automatically trimmed and uppercased, e.g. `"USD"`, `"INR"`, `"EUR"`).
   - `timezone` must be a valid Java `ZoneId` (e.g. `"Asia/Kolkata"`, `"America/New_York"`).
   - `invoice_prefix` must be between 1 and 20 characters (automatically trimmed and uppercased, e.g. `"INV"`).
   - `receipt_header` and `receipt_footer` must not exceed 500 characters.

---

## Security & Tenant Isolation
- **Role Restriction**: Accessible **ONLY** by users with `ADMIN` role. Requests by `WAITER` or `KITCHEN` users return `403 Forbidden`. Unauthenticated requests return `401 Unauthorized`.
- **Tenant Isolation**: All setting reads and updates use `SecurityUtils.getCurrentRestaurantId()`. Client-supplied `restaurantId` fields or parameters are ignored.

---

## API Endpoints List

### 1. Get Restaurant Settings
`GET /api/settings`
- **Response**: `200 OK`
```json
{
  "id": 1,
  "restaurantId": 1,
  "taxEnabled": true,
  "defaultTaxRate": 0.00,
  "serviceChargeEnabled": false,
  "defaultServiceChargeRate": 0.00,
  "currency": "INR",
  "timezone": "Asia/Kolkata",
  "receiptHeader": "Royal Dine Restaurant",
  "receiptFooter": "Thank you for visiting!",
  "invoicePrefix": "INV",
  "autoAcceptOrders": false,
  "createdAt": "2026-09-10T12:00:00",
  "updatedAt": "2026-09-10T12:00:00"
}
```

### 2. Update Restaurant Settings
`PUT /api/settings`
- **Request Body**:
```json
{
  "taxEnabled": true,
  "defaultTaxRate": 5.00,
  "serviceChargeEnabled": true,
  "defaultServiceChargeRate": 2.50,
  "currency": "USD",
  "timezone": "America/New_York",
  "receiptHeader": "Welcome to Royal Dine",
  "receiptFooter": "Please come again!",
  "invoicePrefix": "BILL",
  "autoAcceptOrders": true
}
```
- **Response**: `200 OK` (Updated settings object)
- **Error Responses**:
  - `400 Bad Request` if invalid tax/service charge rate, invalid currency, invalid timezone, prefix/header/footer length constraints violated.
  - `403 Forbidden` if authenticated user is not an `ADMIN`.
  - `401 Unauthorized` if unauthenticated.
