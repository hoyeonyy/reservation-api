package com.reservation.service.checkout;

import com.reservation.api.dto.response.CheckoutResponse;
import com.reservation.implement.booking.BookingFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final BookingFinder bookingFinder;

    @Transactional(readOnly = true)
    public CheckoutResponse checkout(Long productId) {
        return CheckoutResponse.of(bookingFinder.getProduct(productId));
    }
}
