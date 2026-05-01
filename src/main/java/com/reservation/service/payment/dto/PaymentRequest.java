package com.reservation.service.payment.dto;

import com.reservation.domain.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
        @NotNull PaymentMethod type,
        @NotNull @Positive Long amount
) {
}
