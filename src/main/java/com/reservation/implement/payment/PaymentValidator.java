package com.reservation.implement.payment;

import com.reservation.api.exception.InvalidPaymentCombinationException;
import com.reservation.api.exception.PaymentAmountMismatchException;
import com.reservation.api.exception.UnsupportedPaymentMethodException;
import com.reservation.domain.payment.PaymentMethod;
import com.reservation.service.payment.dto.PaymentRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PaymentValidator {

    public void validate(List<PaymentRequest> requests, Set<PaymentMethod> supportedMethods) {
        Set<PaymentMethod> methods = requests.stream()
                .map(PaymentRequest::type)
                .collect(Collectors.toSet());

        methods.forEach(method -> {
            if (!supportedMethods.contains(method)) {
                throw new UnsupportedPaymentMethodException("지원하지 않는 결제수단: " + method);
            }
        });

        if (methods.contains(PaymentMethod.CREDIT_CARD) && methods.contains(PaymentMethod.Y_PAY)) {
            throw new InvalidPaymentCombinationException("CREDIT_CARD + Y_PAY 조합은 사용할 수 없습니다");
        }
    }

    public void validateAmount(List<PaymentRequest> requests, long productPrice) {
        long totalAmount = requests.stream().mapToLong(PaymentRequest::amount).sum();
        if (totalAmount != productPrice) {
            throw new PaymentAmountMismatchException(totalAmount, productPrice);
        }
    }
}
