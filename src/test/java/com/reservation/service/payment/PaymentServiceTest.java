package com.reservation.service.payment;

import com.reservation.api.exception.InvalidPaymentCombinationException;
import com.reservation.api.exception.PaymentFailedException;
import com.reservation.domain.payment.PaymentMethod;
import com.reservation.implement.payment.PaymentValidator;
import com.reservation.service.payment.dto.PaymentContext;
import com.reservation.service.payment.dto.PaymentRequest;
import com.reservation.service.payment.dto.PaymentResult;
import com.reservation.service.payment.strategy.Chargeable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private Chargeable creditCardChargeable;
    private Chargeable yPointChargeable;
    private PaymentValidator paymentValidator;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        creditCardChargeable = mock(Chargeable.class);
        when(creditCardChargeable.supportedMethod()).thenReturn(PaymentMethod.CREDIT_CARD);

        yPointChargeable = mock(Chargeable.class);
        when(yPointChargeable.supportedMethod()).thenReturn(PaymentMethod.Y_POINT);

        paymentValidator = new PaymentValidator();
        paymentService = new PaymentService(List.of(creditCardChargeable, yPointChargeable), paymentValidator);
    }

    @Test
    void pay_singleCreditCard_shouldReturnResult() {
        PaymentResult result = new PaymentResult(PaymentMethod.CREDIT_CARD, 100000L, "CC-txid");
        when(creditCardChargeable.pay(any(PaymentContext.class))).thenReturn(result);

        List<PaymentResult> results = paymentService.pay(1L, 1L,
                List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000L)));

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isEqualTo(result);
        verify(creditCardChargeable).pay(any());
    }

    @Test
    void pay_compositePayment_shouldExecuteAllChargeables() {
        PaymentResult ccResult = new PaymentResult(PaymentMethod.CREDIT_CARD, 80000L, "CC-txid");
        PaymentResult ypResult = new PaymentResult(PaymentMethod.Y_POINT, 20000L, "YPT-txid");
        when(creditCardChargeable.pay(any())).thenReturn(ccResult);
        when(yPointChargeable.pay(any())).thenReturn(ypResult);

        List<PaymentResult> results = paymentService.pay(1L, 1L, List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 80000L),
                new PaymentRequest(PaymentMethod.Y_POINT, 20000L)
        ));

        assertThat(results).hasSize(2);
        verify(creditCardChargeable).pay(any());
        verify(yPointChargeable).pay(any());
    }

    @Test
    void pay_secondPaymentFails_shouldRollbackFirstAndThrow() {
        PaymentResult ccResult = new PaymentResult(PaymentMethod.CREDIT_CARD, 80000L, "CC-txid");
        when(creditCardChargeable.pay(any())).thenReturn(ccResult);
        when(yPointChargeable.pay(any())).thenThrow(new RuntimeException("포인트 결제 실패"));

        assertThatThrownBy(() -> paymentService.pay(1L, 1L, List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 80000L),
                new PaymentRequest(PaymentMethod.Y_POINT, 20000L)
        ))).isInstanceOf(PaymentFailedException.class);

        verify(creditCardChargeable).refund(ccResult);
    }

    @Test
    void pay_invalidCombination_shouldThrowBeforeAnyPayment() {
        Chargeable yPayChargeable = mock(Chargeable.class);
        when(yPayChargeable.supportedMethod()).thenReturn(PaymentMethod.Y_PAY);
        PaymentService serviceWithYPay = new PaymentService(
                List.of(creditCardChargeable, yPayChargeable), paymentValidator);

        assertThatThrownBy(() -> serviceWithYPay.pay(1L, 1L, List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 50000L),
                new PaymentRequest(PaymentMethod.Y_PAY, 50000L)
        ))).isInstanceOf(InvalidPaymentCombinationException.class);

        verify(creditCardChargeable, never()).pay(any());
        verify(yPayChargeable, never()).pay(any());
    }

    @Test
    void pay_firstPaymentFails_shouldNotRollbackAnything() {
        when(creditCardChargeable.pay(any())).thenThrow(new RuntimeException("카드 결제 실패"));

        assertThatThrownBy(() -> paymentService.pay(1L, 1L,
                List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000L))))
                .isInstanceOf(PaymentFailedException.class);

        verify(creditCardChargeable, never()).refund(any());
    }
}
