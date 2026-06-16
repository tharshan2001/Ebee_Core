package com.ebee.order.exception;

/**
 * Thrown when a generated order number collides with an existing row
 * (the unique constraint on {@code orders.order_number} fires). The
 * service catches this specifically and the {@code @Retryable} on
 * {@code OrderService.createOrder} only retries on this exception,
 * not on any {@link org.springframework.dao.DataIntegrityViolationException}
 * -- a CHECK or FK violation should fail fast.
 */
public class DuplicateOrderNumberException extends RuntimeException {

    public DuplicateOrderNumberException(String message, Throwable cause) {
        super(message, cause);
    }
}
