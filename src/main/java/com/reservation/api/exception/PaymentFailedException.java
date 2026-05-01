package com.reservation.api.exception;

import com.reservation.domain.payment.PaymentErrorCode;

public class PaymentFailedException extends RuntimeException {

    private final PaymentErrorCode errorCode;

    public PaymentFailedException(String message) {
        super(message);
        this.errorCode = PaymentErrorCode.UNKNOWN_ERROR;
    }

    public PaymentFailedException(PaymentErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PaymentErrorCode getErrorCode() {
        return errorCode;
    }
}
