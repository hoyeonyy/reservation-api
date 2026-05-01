package com.reservation.domain.booking;

import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingTest {

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = TestFixtures.createUser(1L, "user1", "user1@test.com");
        product = TestFixtures.createProduct(1L, "호텔", 150000L, 10);
    }

    @Test
    void create_shouldCreateBookingWithReservedStatus() {
        Booking booking = Booking.create(user, product, "idem-key-001", 150000L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.RESERVED);
        assertThat(booking.getIdempotencyKey()).isEqualTo("idem-key-001");
        assertThat(booking.getTotalAmount()).isEqualTo(150000L);
        assertThat(booking.getUser()).isEqualTo(user);
        assertThat(booking.getProduct()).isEqualTo(product);
    }

    @Test
    void confirm_shouldChangeStatusToConfirmed() {
        Booking booking = Booking.create(user, product, "idem-key-001", 150000L);

        booking.confirm();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void fail_shouldChangeStatusToFailed() {
        Booking booking = Booking.create(user, product, "idem-key-001", 150000L);

        booking.fail();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
    }
}
