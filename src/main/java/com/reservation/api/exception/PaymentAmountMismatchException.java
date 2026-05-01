package com.reservation.api.exception;

public class PaymentAmountMismatchException extends RuntimeException {

    public PaymentAmountMismatchException(long requestedAmount, long productPrice) {
        super(String.format("결제 금액 합계(%d)가 상품 가격(%d)과 일치하지 않습니다.", requestedAmount, productPrice));
    }
}
