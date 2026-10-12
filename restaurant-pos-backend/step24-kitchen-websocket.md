# Step 24 — Kitchen / KDS Backend + Real-Time Order Updates Documentation

## Purpose
Provides backend infrastructure for the Kitchen Display System (KDS). Includes kitchen-specific order queue REST APIs, queue filtering, strict KITCHEN role authorization, real-time WebSocket STOMP order updates, STOMP JWT authentication, subscription-level tenant & role isolation, and transaction-safe event publishing.

---

## Architecture & Principles

1. **Kitchen Responsibilities**:
   - View kitchen queue (`NEW`, `ACCEPTED`, `PREPARING`, `READY`).
   - Move orders through allowed status progression:
     - `NEW` → `ACCEPTED`
     - `ACCEPTED` → `PREPARING`
     - `PREPARING` → `READY`
2. **Kitchen Protections**:
   - Kitchen CANNOT create orders, modify order items, change prices/quantities, cancel orders, or mark orders `COMPLETED`.
   - Completion remains strictly controlled by successful payment through the billing flow (`READY` → `COMPLETED`).
3. **Real-time WebSocket STOMP Infrastructure**:
   - Handshake Endpoint: `/ws`
   - Application Prefix: `/app`
   - Broker Prefix: `/topic`
   - Kitchen Topic: `/topic/restaurants/{restaurantId}/kitchen/orders`
4. **Subscription Security & Tenant Isolation**:
   - STOMP `StompAuthChannelInterceptor` verifies JWT authentication on `CONNECT`.
   - On `SUBSCRIBE` to `/topic/restaurants/{restaurantId}/kitchen/orders`:
     - Validates `authenticatedRestaurantId == targetRestaurantId`. Mismatches throw `AccessDeniedException`.
     - Validates `role` is `KITCHEN` or `ADMIN`. `WAITER` subscribers are rejected.
5. **Transaction Safety**:
   - Order events are published using `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` to ensure events are sent ONLY AFTER database transactions commit cleanly.

---

## Kitchen REST APIs (`/api/kitchen`)

All endpoints require `KITCHEN` role.

### 1. Get Kitchen Queue
`GET /api/kitchen/orders`
- Returns orders with status `NEW`, `ACCEPTED`, `PREPARING`, `READY`, sorted by `createdAt ASC` (oldest first).

### 2. Get Kitchen Queue with Status Filter
`GET /api/kitchen/orders?status=NEW`
- Supported filters: `NEW`, `ACCEPTED`, `PREPARING`, `READY`.
- Rejects `COMPLETED`, `CANCELLED`, or invalid status strings with `HTTP 400 Bad Request`.

### 3. Get Kitchen Order Detail
`GET /api/kitchen/orders/{id}`
- Returns detailed kitchen order info including order-level notes, item-level notes, and category name.

### 4. Accept Order
`PATCH /api/kitchen/orders/{id}/accept`
- Transitions `NEW` → `ACCEPTED`. Records `OrderStatusHistory`. Triggers `ORDER_STATUS_CHANGED` event.

### 5. Start Preparing Order
`PATCH /api/kitchen/orders/{id}/preparing`
- Transitions `ACCEPTED` → `PREPARING`. Records `OrderStatusHistory`. Triggers `ORDER_STATUS_CHANGED` event.

### 6. Mark Order Ready
`PATCH /api/kitchen/orders/{id}/ready`
- Transitions `PREPARING` → `READY`. Records `OrderStatusHistory`. Triggers `ORDER_STATUS_CHANGED` event.

---

## WebSocket Events

### Event Types
- `ORDER_CREATED`: Published when a new order is created by Waiter/Admin.
- `ORDER_UPDATED`: Published when an existing `NEW` order items/notes are modified by Waiter/Admin.
- `ORDER_STATUS_CHANGED`: Published when status changes (`NEW` → `ACCEPTED`, `ACCEPTED` → `PREPARING`, `PREPARING` → `READY`, `READY` → `COMPLETED` on payment, or `CANCELLED`).

### Sample Event Payload
```json
{
  "eventType": "ORDER_STATUS_CHANGED",
  "orderId": 42,
  "restaurantId": 1,
  "orderType": "DINE_IN",
  "tableId": 5,
  "tableNumber": 12,
  "status": "ACCEPTED",
  "createdAt": "2026-09-10T12:00:00",
  "updatedAt": "2026-09-10T12:05:00"
}
```

---

## Security Matrix

| Role | Kitchen Queue REST | Kitchen Accept/Prepare/Ready | Subscribe to Kitchen Topic | Modify Order Items | Cancel Order | Payment / Complete |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **KITCHEN** | ✅ Allowed | ✅ Allowed | ✅ Allowed | ❌ Forbidden | ❌ Forbidden | ❌ Forbidden |
| **ADMIN** | ❌ Forbidden (KDS REST) | ❌ Forbidden (KDS REST) | ✅ Allowed | ✅ Allowed | ✅ Allowed | ✅ Allowed |
| **WAITER** | ❌ Forbidden | ❌ Forbidden | ❌ Forbidden | ✅ (NEW orders) | ✅ (NEW orders) | ❌ Forbidden |
