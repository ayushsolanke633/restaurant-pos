package com.restaurant.restaurantpos.exception;

public class DuplicateSessionException extends RuntimeException {

    public DuplicateSessionException(String message) {
        super(message);
    }
}
