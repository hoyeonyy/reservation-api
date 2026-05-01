package com.reservation.implement.booking;

import com.reservation.api.exception.EntityNotFoundException;
import com.reservation.domain.booking.Booking;
import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import com.reservation.infrastructure.persistence.BookingRepository;
import com.reservation.infrastructure.persistence.ProductRepository;
import com.reservation.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BookingFinder {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다: " + bookingId));
    }

    public Optional<Booking> findByIdempotencyKey(String idempotencyKey) {
        return bookingRepository.findByIdempotencyKey(idempotencyKey);
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다: " + userId));
    }

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다: " + productId));
    }
}
