package com.reservation.implement.payment;

import com.reservation.api.exception.InvalidPaymentCombinationException;
import com.reservation.api.exception.PaymentAmountMismatchException;
import com.reservation.api.exception.UnsupportedPaymentMethodException;
import com.reservation.domain.payment.PaymentMethod;
import com.reservation.service.payment.dto.PaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentValidatorTest {

    private PaymentValidator validator;
    private Set<PaymentMethod> supportedMethods;

    @BeforeEach
    void setUp() {
        validator = new PaymentValidator();
        supportedMethods = Set.of(PaymentMethod.CREDIT_CARD, PaymentMethod.Y_PAY, PaymentMethod.Y_POINT);
    }

    @Test
    void validate_singleCreditCard_shouldPass() {
        List<PaymentRequest> requests = List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000L));

        assertThatCode(() -> validator.validate(requests, supportedMethods))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_singleYPay_shouldPass() {
        List<PaymentRequest> requests = List.of(new PaymentRequest(PaymentMethod.Y_PAY, 100000L));

        assertThatCode(() -> validator.validate(requests, supportedMethods))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_singleYPoint_shouldPass() {
        List<PaymentRequest> requests = List.of(new PaymentRequest(PaymentMethod.Y_POINT, 50000L));

        assertThatCode(() -> validator.validate(requests, supportedMethods))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_creditCardAndYPoint_shouldPass() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 80000L),
                new PaymentRequest(PaymentMethod.Y_POINT, 20000L)
        );

        assertThatCode(() -> validator.validate(requests, supportedMethods))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_creditCardAndYPay_shouldThrowInvalidPaymentCombinationException() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 50000L),
                new PaymentRequest(PaymentMethod.Y_PAY, 50000L)
        );

        assertThatThrownBy(() -> validator.validate(requests, supportedMethods))
                .isInstanceOf(InvalidPaymentCombinationException.class)
                .hasMessageContaining("CREDIT_CARD + Y_PAY");
    }

    @Test
    void validate_unsupportedMethod_shouldThrowUnsupportedPaymentMethodException() {
        Set<PaymentMethod> limitedMethods = Set.of(PaymentMethod.CREDIT_CARD);
        List<PaymentRequest> requests = List.of(new PaymentRequest(PaymentMethod.Y_PAY, 100000L));

        assertThatThrownBy(() -> validator.validate(requests, limitedMethods))
                .isInstanceOf(UnsupportedPaymentMethodException.class);
    }

    @Test
    void validateAmount_matchingTotal_shouldPass() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 80000L),
                new PaymentRequest(PaymentMethod.Y_POINT, 20000L)
        );

        assertThatCode(() -> validator.validateAmount(requests, 100000L))
                .doesNotThrowAnyException();
    }

    @Test
    void validateAmount_mismatchTotal_shouldThrowPaymentAmountMismatchException() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 50000L)
        );

        assertThatThrownBy(() -> validator.validateAmount(requests, 100000L))
                .isInstanceOf(PaymentAmountMismatchException.class)
                .hasMessageContaining("상품 가격");
    }
}
