package com.reservation.domain.payment;

import com.reservation.domain.booking.Booking;
import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    private Booking booking;

    @BeforeEach
    void setUp() {
        User user = TestFixtures.createUser(1L, "user1", "user1@test.com");
        Product product = TestFixtures.createProduct(1L, "호텔", 150000L, 10);
        booking = Booking.create(user, product, "idem-key-001", 150000L);
    }

    @Test
    void createPending_shouldCreatePaymentWithPendingStatus() {
        Payment payment = Payment.createPending(booking, PaymentMethod.CREDIT_CARD, 150000L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(payment.getAmount()).isEqualTo(150000L);
        assertThat(payment.getBooking()).isEqualTo(booking);
        assertThat(payment.getPgTransactionId()).isNull();
    }

    @Test
    void confirm_shouldChangeStatusToSuccessAndSetPgTransactionId() {
        Payment payment = Payment.createPending(booking, PaymentMethod.CREDIT_CARD, 150000L);

        payment.confirm("CC-txid-12345");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPgTransactionId()).isEqualTo("CC-txid-12345");
    }

    @Test
    void fail_shouldChangeStatusToFailed() {
        Payment payment = Payment.createPending(booking, PaymentMethod.Y_PAY, 150000L);

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
