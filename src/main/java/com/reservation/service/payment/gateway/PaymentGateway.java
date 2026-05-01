package com.reservation.service.payment.gateway;

public interface PaymentGateway {

    PgPaymentResponse charge(PgPaymentRequest request);

    void refund(String pgTransactionId, Long amount);
}
