package com.reservation.implement.booking;

import com.reservation.domain.booking.Booking;
import com.reservation.domain.payment.Payment;
import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import com.reservation.infrastructure.persistence.BookingRepository;
import com.reservation.infrastructure.persistence.PaymentRepository;
import com.reservation.service.payment.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingWriter {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    public Booking saveBooking(User user, Product product, String idempotencyKey, long totalAmount) {
        return bookingRepository.save(Booking.create(user, product, idempotencyKey, totalAmount));
    }

    public Booking updateBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    public void savePayments(Booking booking, List<PaymentRequest> paymentRequests) {
        paymentRequests.forEach(pr ->
                paymentRepository.save(Payment.createPending(booking, pr.type(), pr.amount())));
    }
}
