package com.example.core.call.model;

public final class CallError {
    public enum Domain {
        RTC,
        TOKEN,
        VALIDATION,
        INTERNAL
    }

    private final Domain domain;
    private final int code;
    private final String message;

    public CallError(Domain domain, int code, String message) {
        this.domain = domain;
        this.code = code;
        this.message = message == null ? "Unknown error" : message;
    }

    public Domain getDomain() {
        return domain;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
