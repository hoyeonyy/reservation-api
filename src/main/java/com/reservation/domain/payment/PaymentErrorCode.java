package com.reservation.domain.payment;

public enum PaymentErrorCode {

    CREDIT_LIMIT_EXCEEDED(false, "한도 초과"),
    CARD_SUSPENDED(false, "카드 정지"),
    INVALID_CARD_INFO(false, "잘못된 카드 정보"),
    INSUFFICIENT_POINTS(false, "포인트 잔액 부족"),

    NETWORK_TIMEOUT(true, "네트워크 타임아웃"),
    PG_SERVER_ERROR(true, "PG 서버 오류"),
    UNKNOWN_ERROR(true, "알 수 없는 오류");

    private final boolean retryable;
    private final String description;

    PaymentErrorCode(boolean retryable, String description) {
        this.retryable = retryable;
        this.description = description;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getDescription() {
        return description;
    }
}
