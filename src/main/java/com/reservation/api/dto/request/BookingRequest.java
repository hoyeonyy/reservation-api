package com.reservation.api.dto.request;

import com.reservation.service.payment.dto.PaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BookingRequest(
        @NotNull Long userId,
        @NotNull Long productId,
        @NotBlank String idempotencyKey,
        @NotEmpty @Valid List<PaymentRequest> paymentRequests
) {
}
