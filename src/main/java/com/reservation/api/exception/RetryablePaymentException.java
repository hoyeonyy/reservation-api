package com.reservation.api.exception;

import com.reservation.domain.payment.PaymentErrorCode;

public class RetryablePaymentException extends PaymentFailedException {

    public RetryablePaymentException(PaymentErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
