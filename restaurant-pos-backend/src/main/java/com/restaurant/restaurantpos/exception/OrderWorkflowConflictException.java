package com.restaurant.restaurantpos.exception;

public class OrderWorkflowConflictException extends RuntimeException {
    public OrderWorkflowConflictException(String message) {
        super(message);
    }
}
