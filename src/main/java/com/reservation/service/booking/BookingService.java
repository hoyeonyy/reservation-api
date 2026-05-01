package com.reservation.service.booking;

import com.reservation.api.exception.DuplicateRequestException;
import com.reservation.api.exception.EntityNotFoundException;
import com.reservation.api.exception.LockInterruptedException;
import org.springframework.dao.DataIntegrityViolationException;
import com.reservation.domain.booking.Booking;
import com.reservation.domain.payment.Payment;
import com.reservation.implement.booking.BookingFinder;
import com.reservation.implement.booking.BookingWriter;
import com.reservation.implement.inventory.InventoryProcessor;
import com.reservation.implement.payment.PaymentFinder;
import com.reservation.implement.payment.PaymentWriter;
import com.reservation.service.payment.dto.PaymentRequest;
import com.reservation.service.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingProcessor bookingProcessor;
    private final BookingFinder bookingFinder;
    private final BookingWriter bookingWriter;
    private final PaymentFinder paymentFinder;
    private final PaymentWriter paymentWriter;
    private final InventoryProcessor inventoryProcessor;
    private final RedissonClient redissonClient;

    public Booking book(Long userId, Long productId, String idempotencyKey, List<PaymentRequest> paymentRequests) {
        RLock lock = redissonClient.getLock("booking:idempotency:" + idempotencyKey);
        boolean lockAcquired = tryAcquireLock(lock);
        try {
            return bookingProcessor.process(userId, productId, idempotencyKey, paymentRequests);
        } catch (DataIntegrityViolationException e) {
            log.warn("동시 중복 요청 감지 (UNIQUE 충돌), 기존 booking 반환: idempotencyKey={}", idempotencyKey);
            return bookingFinder.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new EntityNotFoundException("Booking not found after duplicate key: " + idempotencyKey));
        } finally {
            if (lockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean tryAcquireLock(RLock lock) {
        boolean acquired;
        try {
            acquired = lock.tryLock(0, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockInterruptedException("락 획득 중 인터럽트 발생");
        } catch (Exception e) {
            log.warn("Redis 분산락 장애 {}", e.getMessage());
            return false;
        }
        if (!acquired) {
            throw new DuplicateRequestException("요청이 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }
        return true;
    }

    @Transactional
    public void confirmPayment(Long bookingId, List<PaymentResult> results) {
        Booking booking = bookingFinder.getBooking(bookingId);
        booking.confirm();
        bookingWriter.updateBooking(booking);

        List<Payment> pendingPayments = paymentFinder.getPendingPayments(bookingId);
        results.forEach(result ->
                pendingPayments.stream()
                        .filter(p -> p.getPaymentMethod() == result.method())
                        .findFirst()
                        .orElseThrow(() -> new EntityNotFoundException("Payment not found for method: " + result.method()))
                        .confirm(result.pgTransactionId()));
        paymentWriter.saveAll(pendingPayments);
    }

    @Transactional
    public void incrementRetryCount(Long bookingId) {
        Booking booking = bookingFinder.getBooking(bookingId);
        booking.incrementRetryCount();
        bookingWriter.updateBooking(booking);
    }

    @Transactional
    public void failPayment(Long bookingId, Long productId) {
        Booking booking = bookingFinder.getBooking(bookingId);
        booking.fail();
        bookingWriter.updateBooking(booking);

        List<Payment> pendingPayments = paymentFinder.getPendingPayments(bookingId);
        pendingPayments.forEach(Payment::fail);
        paymentWriter.saveAll(pendingPayments);

        inventoryProcessor.incrementStock(productId);
    }
}
