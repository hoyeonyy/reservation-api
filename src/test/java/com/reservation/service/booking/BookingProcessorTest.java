package com.reservation.service.booking;

import com.reservation.domain.booking.Booking;
import com.reservation.domain.payment.PaymentMethod;
import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import com.reservation.event.PaymentRequestEvent;
import com.reservation.implement.booking.BookingFinder;
import com.reservation.implement.booking.BookingValidator;
import com.reservation.implement.booking.BookingWriter;
import com.reservation.implement.inventory.InventoryProcessor;
import com.reservation.implement.payment.PaymentValidator;
import com.reservation.service.payment.dto.PaymentRequest;
import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import com.reservation.api.exception.PaymentAmountMismatchException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingProcessorTest {

    private BookingFinder bookingFinder;
    private BookingWriter bookingWriter;
    private BookingValidator bookingValidator;
    private InventoryProcessor inventoryProcessor;
    private ApplicationEventPublisher eventPublisher;
    private BookingProcessor bookingProcessor;
    private PaymentValidator paymentValidator;

    private User user;
    private Product product;
    private Booking booking;

    @BeforeEach
    void setUp() {
        bookingFinder = mock(BookingFinder.class);
        bookingWriter = mock(BookingWriter.class);
        bookingValidator = mock(BookingValidator.class);
        inventoryProcessor = mock(InventoryProcessor.class);
        paymentValidator = new PaymentValidator();
        eventPublisher = mock(ApplicationEventPublisher.class);

        bookingProcessor = new BookingProcessor(
                bookingFinder, bookingWriter, bookingValidator,
                inventoryProcessor, paymentValidator, eventPublisher);

        user = TestFixtures.createUser(1L, "user1", "user1@test.com");
        product = TestFixtures.createProduct(1L, "호텔", 150000L, 10);
        booking = Booking.create(user, product, "idem-key-001", 150000L);
        ReflectionTestUtils.setField(booking, "id", 100L);
    }

    @Test
    void process_withNewIdempotencyKey_shouldCreateBookingAndPublishEvent() {
        when(bookingValidator.checkIdempotency("idem-key-001")).thenReturn(Optional.empty());
        when(bookingFinder.getUser(1L)).thenReturn(user);
        when(bookingFinder.getProduct(1L)).thenReturn(product);
        when(bookingWriter.saveBooking(any(), any(), any(), anyLong())).thenReturn(booking);

        List<PaymentRequest> paymentRequests = List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 150000L));
        Booking result = bookingProcessor.process(1L, 1L, "idem-key-001", paymentRequests);

        assertThat(result).isEqualTo(booking);
        verify(inventoryProcessor).decrementStock(1L);
        verify(bookingWriter).savePayments(booking, paymentRequests);
        ArgumentCaptor<PaymentRequestEvent> captor = ArgumentCaptor.forClass(PaymentRequestEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo(100L);
    }

    @Test
    void process_withWrongTotalAmount_shouldThrowBeforeDecrementingStock() {
        when(bookingValidator.checkIdempotency("idem-key-001")).thenReturn(Optional.empty());
        when(bookingFinder.getUser(1L)).thenReturn(user);
        when(bookingFinder.getProduct(1L)).thenReturn(product);

        List<PaymentRequest> wrongRequests = List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000L));

        assertThatThrownBy(() -> bookingProcessor.process(1L, 1L, "idem-key-001", wrongRequests))
                .isInstanceOf(PaymentAmountMismatchException.class)
                .hasMessageContaining("상품 가격");

        verify(inventoryProcessor, never()).decrementStock(any());
    }

    @Test
    void process_withExistingIdempotencyKey_shouldReturnExistingBookingWithoutProcessing() {
        when(bookingValidator.checkIdempotency("idem-key-001")).thenReturn(Optional.of(booking));

        List<PaymentRequest> paymentRequests = List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 150000L));
        Booking result = bookingProcessor.process(1L, 1L, "idem-key-001", paymentRequests);

        assertThat(result).isEqualTo(booking);
        verify(inventoryProcessor, never()).decrementStock(any());
        verify(bookingWriter, never()).saveBooking(any(), any(), any(), anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
