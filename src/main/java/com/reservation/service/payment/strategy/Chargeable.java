package com.reservation.service.payment.strategy;

import com.reservation.domain.payment.PaymentMethod;
import com.reservation.service.payment.dto.PaymentContext;
import com.reservation.service.payment.dto.PaymentResult;

public interface Chargeable {

    PaymentResult pay(PaymentContext context);

    void refund(PaymentResult result);

    PaymentMethod supportedMethod();
}
