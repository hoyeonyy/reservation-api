package com.reservation.service.payment.strategy;

import com.reservation.api.exception.NonRetryablePaymentException;
import com.reservation.api.exception.RetryablePaymentException;
import com.reservation.domain.payment.PaymentMethod;
import com.reservation.service.payment.dto.PaymentContext;
import com.reservation.service.payment.dto.PaymentResult;
import com.reservation.service.payment.gateway.PaymentGateway;
import com.reservation.service.payment.gateway.PgPaymentRequest;
import com.reservation.service.payment.gateway.PgPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditCardChargeable implements Chargeable {

    private final PaymentGateway paymentGateway;

    @Override
    public PaymentMethod supportedMethod() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public PaymentResult pay(PaymentContext context) {
        PgPaymentResponse response = paymentGateway.charge(
                new PgPaymentRequest(context.userId(), context.bookingId(), context.amount(), PaymentMethod.CREDIT_CARD)
        );
        if (!response.success()) {
            log.warn("신용카드 결제 실패: code={}, message={}", response.errorCode(), response.errorMessage());
            if (response.errorCode().isRetryable()) {
                throw new RetryablePaymentException(response.errorCode(), response.errorMessage());
            }
            throw new NonRetryablePaymentException(response.errorCode(), response.errorMessage());
        }
        log.info("신용카드 결제 완료: amount={}, pgTxId={}", context.amount(), response.pgTransactionId());
        return new PaymentResult(PaymentMethod.CREDIT_CARD, context.amount(), response.pgTransactionId());
    }

    @Override
    public void refund(PaymentResult result) {
        paymentGateway.refund(result.pgTransactionId(), result.amount());
        log.info("신용카드 환불 처리: pgTxId={}, amount={}", result.pgTransactionId(), result.amount());
    }
}
