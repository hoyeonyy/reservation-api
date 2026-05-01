package com.reservation.service.payment.gateway;

import com.reservation.domain.payment.PaymentErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final long CREDIT_LIMIT = 1_000_000L;

    @Override
    public PgPaymentResponse charge(PgPaymentRequest request) {
        log.info("PG 결제 요청: method={}, amount={}, bookingId={}",
                request.method(), request.amount(), request.bookingId());

        if (request.amount() >= CREDIT_LIMIT) {
            log.warn("PG 결제 실패 - 한도 초과: amount={}", request.amount());
            return PgPaymentResponse.failure(
                    PaymentErrorCode.CREDIT_LIMIT_EXCEEDED,
                    "결제 한도(" + CREDIT_LIMIT + "원)를 초과했습니다."
            );
        }

        String pgTransactionId = request.method().name().substring(0, 2) + "-" + UUID.randomUUID();
        log.info("PG 결제 성공: pgTxId={}", pgTransactionId);
        return PgPaymentResponse.success(pgTransactionId);
    }

    @Override
    public void refund(String pgTransactionId, Long amount) {
        log.info("PG 환불 처리: pgTxId={}, amount={}", pgTransactionId, amount);
    }
}
