package com.reservation.service.booking;

import com.reservation.domain.booking.Booking;
import com.reservation.domain.booking.BookingStatus;
import com.reservation.domain.payment.Payment;
import com.reservation.domain.payment.PaymentMethod;
import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import com.reservation.implement.booking.BookingFinder;
import com.reservation.implement.inventory.InventoryProcessor;
import com.reservation.implement.payment.PaymentFinder;
import com.reservation.service.payment.dto.PaymentRequest;
import com.reservation.service.payment.dto.PaymentResult;
import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.reservation.api.exception.DuplicateRequestException;
import com.reservation.implement.booking.BookingWriter;
import com.reservation.implement.payment.PaymentWriter;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Optional;

class BookingServiceTest {

    private BookingProcessor bookingProcessor;
    private BookingFinder bookingFinder;
    private BookingWriter bookingWriter;
    private PaymentFinder paymentFinder;
    private PaymentWriter paymentWriter;
    private InventoryProcessor inventoryProcessor;
    private RedissonClient redissonClient;
    private RLock rLock;
    private BookingService bookingService;

    private User user;
    private Product product;
    private Booking booking;

    @BeforeEach
    void setUp() throws InterruptedException {
        bookingProcessor = mock(BookingProcessor.class);
        bookingFinder = mock(BookingFinder.class);
        bookingWriter = mock(BookingWriter.class);
        paymentFinder = mock(PaymentFinder.class);
        paymentWriter = mock(PaymentWriter.class);
        inventoryProcessor = mock(InventoryProcessor.class);
        redissonClient = mock(RedissonClient.class);
        rLock = mock(RLock.class);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        bookingService = new BookingService(
                bookingProcessor, bookingFinder, bookingWriter,
                paymentFinder, paymentWriter, inventoryProcessor, redissonClient);

        user = TestFixtures.createUser(1L, "user1", "user1@test.com");
        product = TestFixtures.createProduct(1L, "호텔", 150000L, 10);
        booking = Booking.create(user, product, "idem-key-001", 150000L);
        ReflectionTestUtils.setField(booking, "id", 100L);
    }

    @Test
    void book_lockAcquired_shouldDelegateToProcessorAndReleaseLock() {
        List<PaymentRequest> paymentRequests = List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 150000L));
        when(bookingProcessor.process(1L, 1L, "idem-key-001", paymentRequests)).thenReturn(booking);

        Booking result = bookingService.book(1L, 1L, "idem-key-001", paymentRequests);

        assertThat(result).isEqualTo(booking);
        verify(redissonClient).getLock("booking:idempotency:idem-key-001");
        verify(bookingProcessor).process(1L, 1L, "idem-key-001", paymentRequests);
        verify(rLock).unlock();
    }

    @Test
    void book_lockNotAcquired_shouldThrowDuplicateRequestException() throws InterruptedException {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        List<PaymentRequest> paymentRequests = List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 150000L));

        assertThatThrownBy(() -> bookingService.book(1L, 1L, "idem-key-001", paymentRequests))
                .isInstanceOf(DuplicateRequestException.class);

        verify(bookingProcessor, never()).process(any(), any(), any(), any());
        verify(rLock, never()).unlock();
    }

    @Test
    void book_redisFailure_shouldFallbackToProcessWithoutLock() throws InterruptedException {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis 연결 불가"));

        List<PaymentRequest> paymentRequests = List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 150000L));
        when(bookingProcessor.process(1L, 1L, "idem-key-001", paymentRequests)).thenReturn(booking);

        Booking result = bookingService.book(1L, 1L, "idem-key-001", paymentRequests);

        assertThat(result).isEqualTo(booking);
        verify(bookingProcessor).process(1L, 1L, "idem-key-001", paymentRequests);
        verify(rLock, never()).unlock();
    }

    @Test
    void book_duplicateKeyViolation_shouldReturnExistingBooking() {
        List<PaymentRequest> paymentRequests = List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 150000L));
        when(bookingProcessor.process(1L, 1L, "idem-key-001", paymentRequests))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));
        when(bookingFinder.findByIdempotencyKey("idem-key-001")).thenReturn(Optional.of(booking));

        Booking result = bookingService.book(1L, 1L, "idem-key-001", paymentRequests);

        assertThat(result).isEqualTo(booking);
        verify(bookingFinder).findByIdempotencyKey("idem-key-001");
    }

    @Test
    void confirmPayment_shouldConfirmBookingAndPayments() {
        Payment pending = Payment.createPending(booking, PaymentMethod.CREDIT_CARD, 150000L);
        when(bookingFinder.getBooking(100L)).thenReturn(booking);
        when(paymentFinder.getPendingPayments(100L)).thenReturn(List.of(pending));

        bookingService.confirmPayment(100L,
                List.of(new PaymentResult(PaymentMethod.CREDIT_CARD, 150000L, "CC-txid")));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(pending.getPgTransactionId()).isEqualTo("CC-txid");
    }

    @Test
    void failPayment_shouldFailBookingAndIncrementStock() {
        Payment pending = Payment.createPending(booking, PaymentMethod.CREDIT_CARD, 150000L);
        when(bookingFinder.getBooking(100L)).thenReturn(booking);
        when(paymentFinder.getPendingPayments(100L)).thenReturn(List.of(pending));

        bookingService.failPayment(100L, 1L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        verify(inventoryProcessor).incrementStock(1L);
    }
}
