package com.reservation.api.exception;

public class SoldOutException extends RuntimeException {

    private final Long productId;

    public SoldOutException(Long productId) {
        super("재고 없음: productId=" + productId);
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
