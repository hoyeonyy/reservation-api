package com.reservation.api.controller;

import com.reservation.api.dto.response.CheckoutResponse;
import com.reservation.service.checkout.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @GetMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@RequestParam Long productId) {
        return ResponseEntity.ok(checkoutService.checkout(productId));
    }
}
