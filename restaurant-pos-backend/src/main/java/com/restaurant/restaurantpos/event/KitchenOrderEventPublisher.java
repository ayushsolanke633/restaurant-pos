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
        publishEvent("ORDER_CREATED", order, null, null);
    }

    public void publishOrderUpdated(Order order) {
        publishEvent("ORDER_UPDATED", order, null, null);
    }

    public void publishOrderUpdated(Order order, Integer batchNumber, Boolean isUpdate) {
        publishEvent("ORDER_UPDATED", order, batchNumber, isUpdate);
    }

    public void publishOrderStatusChanged(Order order) {
        publishEvent("ORDER_STATUS_CHANGED", order, null, null);
    }

    public void publishOrderStatusChanged(Order order, Integer batchNumber, Boolean isUpdate) {
        publishEvent("ORDER_STATUS_CHANGED", order, batchNumber, isUpdate);
    }

    private void publishEvent(String eventType, Order order, Integer batchNumber, Boolean isUpdate) {
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
                .batchNumber(batchNumber)
                .isUpdate(isUpdate)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();

        applicationEventPublisher.publishEvent(new OrderChangeEvent(event));
    }
}
