package com.restaurant.restaurantpos.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when persisting a token revocation record fails (e.g. database unavailability,
 * transient connection loss, or unresolvable write conflict).
 * Maps to HTTP 503 Service Unavailable.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class RevocationStorageException extends RuntimeException {

    public RevocationStorageException(String message) {
        super(message);
    }

    public RevocationStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
