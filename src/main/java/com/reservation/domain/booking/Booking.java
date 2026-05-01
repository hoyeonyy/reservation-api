package com.reservation.domain.booking;

import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(nullable = false, length = 64, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private int paymentRetryCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Booking create(User user, Product product, String idempotencyKey, long totalAmount) {
        Booking b = new Booking();
        b.user = user;
        b.product = product;
        b.idempotencyKey = idempotencyKey;
        b.totalAmount = totalAmount;
        b.status = BookingStatus.RESERVED;
        return b;
    }

    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
    }

    public void fail() {
        this.status = BookingStatus.FAILED;
    }

    public void incrementRetryCount() {
        this.paymentRetryCount++;
    }
}
