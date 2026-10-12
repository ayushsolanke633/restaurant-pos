package com.restaurant.restaurantpos.event;

import com.restaurant.restaurantpos.dto.notification.NotificationResponse;
import lombok.Getter;

@Getter
public class NotificationCreatedEvent {

    private final NotificationResponse notification;

    public NotificationCreatedEvent(NotificationResponse notification) {
        this.notification = notification;
    }
}
