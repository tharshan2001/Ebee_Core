package com.ebee.purchase.exception;

public class DuplicatePoNumberException extends RuntimeException {

    public DuplicatePoNumberException(String message, Throwable cause) {
        super(message, cause);
    }
}
