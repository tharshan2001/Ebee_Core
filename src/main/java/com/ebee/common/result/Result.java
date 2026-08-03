package com.ebee.common.result;

public record Result<T>(T data, boolean success, String message) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(data, true, null);
    }

    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(data, true, message);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(null, false, message);
    }

    public static <T> Result<T> notFound(String entity, String field, Object value) {
        return new Result<>(null, false, entity + " not found with " + field + ": " + value);
    }

    public T getData() {
        return data;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
