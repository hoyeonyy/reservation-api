package com.reservation.implement.payment;

import com.reservation.domain.payment.Payment;
import com.reservation.domain.payment.PaymentStatus;
import com.reservation.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentFinder {

    private final PaymentRepository paymentRepository;

    public List<Payment> getPendingPayments(Long bookingId) {
        return paymentRepository.findByBookingIdAndStatus(bookingId, PaymentStatus.PENDING);
    }

    public List<Payment> getPendingPayments() {
        return paymentRepository.findByStatusWithBookingDetails(PaymentStatus.PENDING);
    }
}
