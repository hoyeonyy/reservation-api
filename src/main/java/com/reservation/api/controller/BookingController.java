package com.reservation.api.controller;

import com.reservation.api.dto.request.BookingRequest;
import com.reservation.api.dto.response.BookingResponse;
import com.reservation.service.booking.BookingService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/booking")
    public ResponseEntity<BookingResponse> book(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.ok(BookingResponse.from(
                bookingService.book(
                        request.userId(),
                        request.productId(),
                        request.idempotencyKey(),
                        request.paymentRequests()
                )
        ));
    }
}
