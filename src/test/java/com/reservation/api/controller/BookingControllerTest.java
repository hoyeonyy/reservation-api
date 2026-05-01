package com.reservation.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.api.dto.response.BookingResponse;
import com.reservation.domain.booking.Booking;
import com.reservation.domain.booking.BookingStatus;
import com.reservation.domain.payment.PaymentMethod;
import com.reservation.service.booking.BookingService;
import com.reservation.service.payment.dto.PaymentRequest;
import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @Test
    void book_validRequest_shouldReturn200() throws Exception {
        Booking booking = Booking.create(
                TestFixtures.createUser(1L, "user1", "user1@test.com"),
                TestFixtures.createProduct(1L, "호텔", 150000L, 10),
                "idem-key-001", 150000L
        );
        when(bookingService.book(anyLong(), anyLong(), anyString(), anyList()))
                .thenReturn(booking);

        Map<String, Object> body = Map.of(
                "userId", 1,
                "productId", 1,
                "idempotencyKey", "idem-key-001",
                "paymentRequests", List.of(
                        Map.of("type", "CREDIT_CARD", "amount", 150000)
                )
        );

        mockMvc.perform(post("/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void book_missingUserId_shouldReturn400() throws Exception {
        Map<String, Object> body = Map.of(
                "productId", 1,
                "idempotencyKey", "idem-key-001",
                "paymentRequests", List.of(
                        Map.of("type", "CREDIT_CARD", "amount", 150000)
                )
        );

        mockMvc.perform(post("/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void book_blankIdempotencyKey_shouldReturn400() throws Exception {
        Map<String, Object> body = Map.of(
                "userId", 1,
                "productId", 1,
                "idempotencyKey", "   ",
                "paymentRequests", List.of(
                        Map.of("type", "CREDIT_CARD", "amount", 150000)
                )
        );

        mockMvc.perform(post("/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void book_emptyPaymentRequests_shouldReturn400() throws Exception {
        Map<String, Object> body = Map.of(
                "userId", 1,
                "productId", 1,
                "idempotencyKey", "idem-key-001",
                "paymentRequests", List.of()
        );

        mockMvc.perform(post("/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void book_zeroAmountInPaymentRequest_shouldReturn400() throws Exception {
        Map<String, Object> body = Map.of(
                "userId", 1,
                "productId", 1,
                "idempotencyKey", "idem-key-001",
                "paymentRequests", List.of(
                        Map.of("type", "CREDIT_CARD", "amount", 0)
                )
        );

        mockMvc.perform(post("/booking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
