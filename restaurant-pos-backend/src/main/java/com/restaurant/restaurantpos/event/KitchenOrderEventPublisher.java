package com.restaurant.restaurantpos.event;

import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderEvent;
import com.restaurant.restaurantpos.entity.Order;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class KitchenOrderEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public KitchenOrderEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishOrderCreated(Order order) {
        publishEvent("ORDER_CREATED", order);
    }

    public void publishOrderUpdated(Order order) {
        publishEvent("ORDER_UPDATED", order);
    }

    public void publishOrderStatusChanged(Order order) {
        publishEvent("ORDER_STATUS_CHANGED", order);
    }

    private void publishEvent(String eventType, Order order) {
        if (order == null || order.getRestaurant() == null) {
            return;
        }

        KitchenOrderEvent event = KitchenOrderEvent.builder()
                .eventType(eventType)
                .orderId(order.getId())
                .restaurantId(order.getRestaurant().getId())
                .orderType(order.getOrderType())
                .tableId(order.getTable() != null ? order.getTable().getId() : null)
                .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();

        applicationEventPublisher.publishEvent(new OrderChangeEvent(event));
    }
}
