package com.reservation.api.exception;

public class InvalidPaymentCombinationException extends RuntimeException {

    public InvalidPaymentCombinationException(String message) {
        super(message);
    }
}
