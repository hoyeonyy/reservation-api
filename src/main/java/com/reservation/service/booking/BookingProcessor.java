package com.reservation.service.booking;

import com.reservation.domain.booking.Booking;
import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import com.reservation.event.PaymentRequestEvent;
import com.reservation.implement.booking.BookingFinder;
import com.reservation.implement.booking.BookingValidator;
import com.reservation.implement.booking.BookingWriter;
import com.reservation.implement.inventory.InventoryProcessor;
import com.reservation.implement.payment.PaymentValidator;
import com.reservation.service.payment.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingProcessor {

    private final BookingFinder bookingFinder;
    private final BookingWriter bookingWriter;
    private final BookingValidator bookingValidator;
    private final InventoryProcessor inventoryProcessor;
    private final PaymentValidator paymentValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Booking process(Long userId, Long productId, String idempotencyKey, List<PaymentRequest> paymentRequests) {
        Optional<Booking> existing = bookingValidator.checkIdempotency(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = bookingFinder.getUser(userId);
        Product product = bookingFinder.getProduct(productId);

        paymentValidator.validateAmount(paymentRequests, product.getPrice());

        inventoryProcessor.decrementStock(productId);

        long totalAmount = paymentRequests.stream().mapToLong(PaymentRequest::amount).sum();
        Booking booking = bookingWriter.saveBooking(user, product, idempotencyKey, totalAmount);
        bookingWriter.savePayments(booking, paymentRequests);

        eventPublisher.publishEvent(new PaymentRequestEvent(booking.getId(), userId, productId, paymentRequests));
        return booking;
    }
}
