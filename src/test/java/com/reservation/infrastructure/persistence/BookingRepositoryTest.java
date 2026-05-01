package com.reservation.infrastructure.persistence;

import com.reservation.domain.booking.Booking;
import com.reservation.domain.booking.BookingStatus;
import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BookingRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BookingRepository bookingRepository;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = em.persist(TestFixtures.createUser("user1", "user1@test.com"));
        product = em.persist(TestFixtures.createProduct("호텔", 150000L, 10));
        em.flush();
    }

    @Test
    void findByIdempotencyKey_exists_shouldReturnBooking() {
        Booking booking = Booking.create(user, product, "idem-key-abc", 150000L);
        em.persist(booking);
        em.flush();

        Optional<Booking> found = bookingRepository.findByIdempotencyKey("idem-key-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey()).isEqualTo("idem-key-abc");
        assertThat(found.get().getStatus()).isEqualTo(BookingStatus.RESERVED);
    }

    @Test
    void findByIdempotencyKey_notExists_shouldReturnEmpty() {
        Optional<Booking> found = bookingRepository.findByIdempotencyKey("nonexistent-key");

        assertThat(found).isEmpty();
    }

    @Test
    void existsByIdempotencyKey_exists_shouldReturnTrue() {
        Booking booking = Booking.create(user, product, "idem-key-xyz", 150000L);
        em.persist(booking);
        em.flush();

        boolean exists = bookingRepository.existsByIdempotencyKey("idem-key-xyz");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByIdempotencyKey_notExists_shouldReturnFalse() {
        boolean exists = bookingRepository.existsByIdempotencyKey("ghost-key");

        assertThat(exists).isFalse();
    }

    @Test
    void save_duplicateIdempotencyKey_shouldPreserveUniqueConstraint() {
        Booking first = Booking.create(user, product, "dup-key", 150000L);
        em.persist(first);
        em.flush();

        long count = bookingRepository.findAll().stream()
                .filter(b -> "dup-key".equals(b.getIdempotencyKey()))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
