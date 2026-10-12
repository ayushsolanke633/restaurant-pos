# Step 25 — Backend Notification / Order-Status Communication Layer Documentation

## Purpose
Provides a lightweight backend notification infrastructure allowing Waiters to receive `ORDER_READY` alerts, Admins to receive event logs (`ORDER_CREATED`, `ORDER_STATUS_CHANGED`, `ORDER_CANCELLED`, `PAYMENT_COMPLETED`), and all authenticated users (`ADMIN`, `WAITER`, `KITCHEN`) to manage their own notifications via REST APIs and real-time STOMP WebSocket topics.

---

## Key Principles & Behavior

1. **No External Push Services**: Notification delivery uses PostgreSQL persistence + existing WebSocket/STOMP. No Firebase, FCM, SMS, or external notification services are required in V1.
2. **User-Level Isolation**:
   - Notifications belong strictly to a `restaurant` and `recipientUser`.
   - REST endpoints (`/api/notifications/**`) return/modify ONLY the authenticated user's notifications.
   - Cross-user and cross-tenant notification access is strictly forbidden (`HTTP 403 / AccessDeniedException`).
3. **Recipient Rules**:
   - `ORDER_CREATED`: Active `ADMIN` users of the restaurant.
   - `ORDER_STATUS_CHANGED`: Active `ADMIN` users of the restaurant.
   - `ORDER_READY`: Active `ADMIN` users AND the `WAITER` who placed the `DINE_IN` order (if `waiterId != null`).
   - `ORDER_CANCELLED`: Active `ADMIN` users AND the `WAITER` who placed the order (if `waiterId != null`).
   - `PAYMENT_COMPLETED`: Active `ADMIN` users of the restaurant.
   - Inactive users never receive notifications.
4. **WebSocket Topic & STOMP Security**:
   - Topic destination: `/topic/restaurants/{restaurantId}/notifications/{userId}`
   - `StompAuthChannelInterceptor` enforces dual-level subscription validation:
     - `authenticatedRestaurantId == topicRestaurantId` (Tenant Isolation)
     - `authenticatedUserId == topicUserId` (User Isolation)
   - Mismatches throw `AccessDeniedException` and reject the subscription.
5. **Transaction Safety**:
   - Notifications are published over WebSocket strictly AFTER database transaction commit (`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`). Failed DB operations do not publish WebSocket notifications.

---

## Notification REST APIs (`/api/notifications`)

Accessible by any authenticated user (`ADMIN`, `WAITER`, `KITCHEN`).

### 1. Get All User Notifications
`GET /api/notifications`
- Returns all notifications for the authenticated user, sorted by `createdAt DESC`.

### 2. Get Unread User Notifications
`GET /api/notifications/unread`
- Returns unread notifications (`isRead = false`) for the authenticated user, sorted by `createdAt DESC`.

### 3. Get Unread Notification Count
`GET /api/notifications/unread/count`
- Response:
```json
{
  "count": 3
}
```

### 4. Mark Single Notification as Read
`PATCH /api/notifications/{id}/read`
- Marks the specified notification as read (`isRead = true`). Returns the updated `NotificationResponse`.

### 5. Mark All Notifications as Read
`PATCH /api/notifications/read-all`
- Marks all unread notifications for the authenticated user as read. Returns `{"count": 0}`.
