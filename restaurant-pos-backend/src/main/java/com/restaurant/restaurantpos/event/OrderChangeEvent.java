package com.restaurant.restaurantpos.event;

import com.restaurant.restaurantpos.dto.kitchen.KitchenOrderEvent;
import lombok.Getter;

@Getter
public class OrderChangeEvent {

    private final KitchenOrderEvent event;

    public OrderChangeEvent(KitchenOrderEvent event) {
        this.event = event;
    }
}
