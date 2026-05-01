package com.reservation.event;

import com.reservation.service.booking.BookingService;
import com.reservation.service.payment.dto.PaymentResult;
import com.reservation.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final PaymentService paymentService;
    private final BookingService bookingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentEvent(PaymentRequestEvent event) {
        try {
            List<PaymentResult> results = paymentService.pay(
                    event.userId(), event.bookingId(), event.paymentRequests());
            bookingService.confirmPayment(event.bookingId(), results);
            log.info("결제 성공 (CONFIRMED): bookingId={}", event.bookingId());
        } catch (Exception e) {
            log.error("결제 실패, 보상 트랜잭션 실행: bookingId={}, error={}", event.bookingId(), e.getMessage());
            bookingService.failPayment(event.bookingId(), event.productId());
        }
    }
}
