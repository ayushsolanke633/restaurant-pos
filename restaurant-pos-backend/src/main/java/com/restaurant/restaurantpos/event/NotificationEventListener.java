package com.restaurant.restaurantpos.event;

import com.restaurant.restaurantpos.dto.notification.NotificationResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleNotificationCreated(NotificationCreatedEvent domainEvent) {
        NotificationResponse notification = domainEvent.getNotification();
        if (notification == null || notification.getRestaurantId() == null || notification.getRecipientUserId() == null) {
            return;
        }

        String destination = "/topic/restaurants/" + notification.getRestaurantId()
                + "/notifications/" + notification.getRecipientUserId();

        messagingTemplate.convertAndSend(destination, notification);
    }
}
