package com.reservation.api.exception;

public class LockInterruptedException extends RuntimeException {

    public LockInterruptedException(String message) {
        super(message);
    }
}
