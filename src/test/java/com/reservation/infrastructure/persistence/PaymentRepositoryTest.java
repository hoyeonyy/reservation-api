package com.reservation.infrastructure.persistence;

import com.reservation.domain.booking.Booking;
import com.reservation.domain.payment.Payment;
import com.reservation.domain.payment.PaymentMethod;
import com.reservation.domain.payment.PaymentStatus;
import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PaymentRepository paymentRepository;

    private Booking booking;

    @BeforeEach
    void setUp() {
        User user = em.persist(TestFixtures.createUser("user1", "user1@test.com"));
        Product product = em.persist(TestFixtures.createProduct("호텔", 150000L, 10));
        booking = em.persist(Booking.create(user, product, "idem-key-001", 150000L));
        em.flush();
    }

    @Test
    void findByBookingIdAndStatus_pending_shouldReturnPendingPayments() {
        Payment pending = em.persist(Payment.createPending(booking, PaymentMethod.CREDIT_CARD, 150000L));
        em.flush();

        List<Payment> result = paymentRepository.findByBookingIdAndStatus(booking.getId(), PaymentStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(pending.getId());
        assertThat(result.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void findByBookingIdAndStatus_afterConfirm_shouldReturnEmpty() {
        Payment payment = em.persist(Payment.createPending(booking, PaymentMethod.CREDIT_CARD, 150000L));
        payment.confirm("CC-txid");
        em.flush();

        List<Payment> result = paymentRepository.findByBookingIdAndStatus(booking.getId(), PaymentStatus.PENDING);

        assertThat(result).isEmpty();
    }

    @Test
    void findByBookingIdAndStatus_multiplePayments_shouldReturnOnlyMatching() {
        Payment pending = em.persist(Payment.createPending(booking, PaymentMethod.CREDIT_CARD, 100000L));
        Payment yPoint = em.persist(Payment.createPending(booking, PaymentMethod.Y_POINT, 50000L));
        yPoint.confirm("YPT-txid");
        em.flush();

        List<Payment> result = paymentRepository.findByBookingIdAndStatus(booking.getId(), PaymentStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
    }

    @Test
    void findByStatusWithBookingDetails_shouldFetchBookingUserAndProduct() {
        em.persist(Payment.createPending(booking, PaymentMethod.Y_POINT, 150000L));
        em.flush();
        em.clear();

        List<Payment> result = paymentRepository.findByStatusWithBookingDetails(PaymentStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBooking().getUser().getUsername()).isEqualTo("user1");
        assertThat(result.get(0).getBooking().getProduct().getName()).isEqualTo("호텔");
    }

    @Test
    void findByStatusWithBookingDetails_noMatch_shouldReturnEmpty() {
        List<Payment> result = paymentRepository.findByStatusWithBookingDetails(PaymentStatus.PENDING);

        assertThat(result).isEmpty();
    }
}
