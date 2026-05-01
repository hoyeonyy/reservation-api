package com.reservation.implement.payment;

import com.reservation.domain.payment.Payment;
import com.reservation.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentWriter {

    private final PaymentRepository paymentRepository;

    public void saveAll(List<Payment> payments) {
        paymentRepository.saveAll(payments);
    }
}
