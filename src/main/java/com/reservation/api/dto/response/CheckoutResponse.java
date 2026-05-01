package com.reservation.api.dto.response;

import com.reservation.domain.product.Product;

import java.time.LocalDate;

public record CheckoutResponse(
        Long productId,
        String name,
        Long price,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer remainingStock
) {
    public static CheckoutResponse of(Product product) {
        return new CheckoutResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCheckInDate(),
                product.getCheckOutDate(),
                product.getRemainingStock()
        );
    }
}
