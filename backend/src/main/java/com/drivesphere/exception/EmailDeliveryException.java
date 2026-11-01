package com.drivesphere.exception;

/**
 * Exception thrown when an external email delivery provider fails to dispatch a message.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
