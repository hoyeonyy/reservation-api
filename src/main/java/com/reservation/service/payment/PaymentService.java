package com.reservation.service.payment;

import com.reservation.api.exception.PaymentFailedException;
import com.reservation.api.exception.RetryablePaymentException;
import com.reservation.domain.payment.PaymentErrorCode;
import com.reservation.domain.payment.PaymentMethod;
import com.reservation.implement.payment.PaymentValidator;
import com.reservation.service.payment.dto.PaymentContext;
import com.reservation.service.payment.dto.PaymentRequest;
import com.reservation.service.payment.dto.PaymentResult;
import com.reservation.service.payment.strategy.Chargeable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

    private final Map<PaymentMethod, Chargeable> chargeableMap;
    private final PaymentValidator paymentValidator;

    public PaymentService(List<Chargeable> chargeables, PaymentValidator paymentValidator) {
        this.chargeableMap = chargeables.stream()
                .collect(Collectors.toMap(Chargeable::supportedMethod, c -> c));
        this.paymentValidator = paymentValidator;
    }

    public List<PaymentResult> pay(Long userId, Long bookingId, List<PaymentRequest> requests) {
        paymentValidator.validate(requests, chargeableMap.keySet());

        List<PaymentResult> completed = new ArrayList<>();
        for (PaymentRequest request : requests) {
            PaymentContext context = new PaymentContext(userId, bookingId, request.amount());
            try {
                completed.add(chargeableMap.get(request.type()).pay(context));
            } catch (PaymentFailedException e) {
                log.warn("결제 실패, 롤백 시작: method={}, error={}", request.type(), e.getMessage());
                rollback(completed);
                throw e;
            } catch (Exception e) {
                log.warn("결제 중 예상치 못한 오류, 롤백 시작: method={}, error={}", request.type(), e.getMessage());
                rollback(completed);
                throw new RetryablePaymentException(PaymentErrorCode.UNKNOWN_ERROR, e.getMessage());
            }
        }
        return completed;
    }

    private void rollback(List<PaymentResult> completed) {
        completed.forEach(result -> {
            try {
                chargeableMap.get(result.method()).refund(result);
            } catch (Exception e) {
                log.error("환불 실패 (운영 모니터링 필요): method={}, error={}", result.method(), e.getMessage());
            }
        });
    }
}
