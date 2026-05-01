package com.reservation.service.booking;

import com.reservation.api.exception.NonRetryablePaymentException;
import com.reservation.api.exception.RetryablePaymentException;
import com.reservation.domain.payment.Payment;
import com.reservation.implement.payment.PaymentFinder;
import com.reservation.service.payment.dto.PaymentRequest;
import com.reservation.service.payment.dto.PaymentResult;
import com.reservation.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final PaymentFinder paymentFinder;
    private final PaymentService paymentService;
    private final BookingService bookingService;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "retryPendingPayments", lockAtMostFor = "PT55S", lockAtLeastFor = "PT30S")
    public void retryPendingPayments() {
        List<Payment> pendingPayments = paymentFinder.getPendingPayments();

        if (pendingPayments.isEmpty()) return;

        Map<Long, List<Payment>> byBookingId = pendingPayments.stream()
                .collect(Collectors.groupingBy(p -> p.getBooking().getId()));

        log.info("PENDING 결제 {}건 재시도 시작", byBookingId.size());
        byBookingId.forEach(this::retryPayment);
    }

    private void retryPayment(Long bookingId, List<Payment> payments) {
        Long userId = payments.get(0).getBooking().getUser().getId();
        Long productId = payments.get(0).getBooking().getProduct().getId();
        int retryCount = payments.get(0).getBooking().getPaymentRetryCount();

        if (retryCount >= MAX_RETRY_COUNT) {
            log.warn("최대 재시도 횟수({}) 초과, 결제 최종 실패 처리: bookingId={}", MAX_RETRY_COUNT, bookingId);
            bookingService.failPayment(bookingId, productId);
            return;
        }

        List<PaymentRequest> paymentRequests = payments.stream()
                .map(p -> new PaymentRequest(p.getPaymentMethod(), p.getAmount()))
                .toList();

        try {
            List<PaymentResult> results = paymentService.pay(userId, bookingId, paymentRequests);
            bookingService.confirmPayment(bookingId, results);
            log.info("스케줄러 결제 재시도 성공 (CONFIRMED): bookingId={}", bookingId);
        } catch (NonRetryablePaymentException e) {
            bookingService.failPayment(bookingId, productId);
        } catch (RetryablePaymentException e) {
            bookingService.incrementRetryCount(bookingId);
        } catch (Exception e) {
            log.error("결제 재시도 중 예상치 못한 오류, 재시도 예정 ({}/{}): bookingId={}, error={}",
                    retryCount + 1, MAX_RETRY_COUNT, bookingId, e.getMessage());
            bookingService.incrementRetryCount(bookingId);
        }
    }
}
