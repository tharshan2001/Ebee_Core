package com.ebee.combo.exception;

public class ComboImmutabilityException extends RuntimeException {

    public ComboImmutabilityException(String message) {
        super(message);
    }

    public ComboImmutabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
