package com.action;

/**
 * A business rule failed (duplicate date, insufficient balance, wrong status...) or a database
 * call failed. Any steps already made have been reversed by CompOffManager; the message is shown
 * to the user, next to {@link #getField()} when it is set.
 */
public class CompOffException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String field;

    public CompOffException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
