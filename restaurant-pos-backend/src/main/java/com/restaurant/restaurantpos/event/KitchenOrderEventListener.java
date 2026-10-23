package com.restaurant.restaurantpos.event;

import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KitchenOrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(KitchenOrderEventListener.class);

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

        if (log.isDebugEnabled()) {
            log.debug("[WS EVENT] Kitchen order event published: orderId={}, restaurantId={}, tableId={}, eventType={}",
                    event.getOrderId(), event.getRestaurantId(), event.getTableId(), event.getEventType());
        }

        messagingTemplate.convertAndSend(destination, event);
    }
}
