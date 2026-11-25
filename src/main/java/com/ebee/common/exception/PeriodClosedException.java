package com.ebee.common.exception;

/** Thrown when a transaction would be posted into an accounting period that isn't open. */
public class PeriodClosedException extends RuntimeException {
    public PeriodClosedException(String message) {
        super(message);
    }
}
