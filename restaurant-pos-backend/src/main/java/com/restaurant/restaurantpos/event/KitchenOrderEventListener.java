package com.restaurant.restaurantpos.event;

import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KitchenOrderEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final com.restaurant.restaurantpos.security.RestaurantWebSocketSessionManager restaurantWebSocketSessionManager;

    public KitchenOrderEventListener(SimpMessagingTemplate messagingTemplate) {
        this(messagingTemplate, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public KitchenOrderEventListener(
            SimpMessagingTemplate messagingTemplate,
            com.restaurant.restaurantpos.security.RestaurantWebSocketSessionManager restaurantWebSocketSessionManager) {
        this.messagingTemplate = messagingTemplate;
        this.restaurantWebSocketSessionManager = restaurantWebSocketSessionManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleOrderChangeEvent(OrderChangeEvent domainEvent) {
        KitchenOrderEvent event = domainEvent.getEvent();
        if (event == null || event.getRestaurantId() == null) {
            return;
        }

        if (restaurantWebSocketSessionManager != null && restaurantWebSocketSessionManager.isRestaurantDeactivated(event.getRestaurantId())) {
            return;
        }

        String destination = "/topic/restaurants/" + event.getRestaurantId() + "/kitchen/orders";

        System.out.println("[WS DEBUG]");
        System.out.println("ORDER CREATED");
        System.out.println("orderId=" + event.getOrderId());
        System.out.println("restaurantId=" + event.getRestaurantId());
        System.out.println("tableId=" + event.getTableId());
        System.out.println("eventType=" + event.getEventType());
        System.out.println("publishing=true");

        messagingTemplate.convertAndSend(destination, event);
    }
}
