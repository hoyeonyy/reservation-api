-- =============================================================
-- V1__init.sql  :  전체 스키마 초기화 + 시드 데이터
-- =============================================================

-- 1. users
-- =============================================================
CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. products
-- =============================================================
CREATE TABLE products (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(255) NOT NULL,
    price            BIGINT       NOT NULL,
    check_in_date    DATE         NOT NULL,
    check_out_date   DATE         NOT NULL,
    total_inventory  INT          NOT NULL DEFAULT 0,
    remaining_stock  INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_stock_non_negative  CHECK (remaining_stock >= 0),
    CONSTRAINT chk_stock_not_exceed    CHECK (remaining_stock <= total_inventory)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. bookings  (users, products FK)
-- =============================================================
CREATE TABLE bookings (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    product_id      BIGINT      NOT NULL,
    status               VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    idempotency_key      VARCHAR(64) NOT NULL,
    total_amount         BIGINT      NOT NULL,
    payment_retry_count  INT         NOT NULL DEFAULT 0,
    created_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bookings_idempotency_key (idempotency_key),
    CONSTRAINT fk_bookings_user    FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT fk_bookings_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. payments  (bookings FK)
-- =============================================================
CREATE TABLE payments (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    booking_id        BIGINT       NOT NULL,
    payment_method    VARCHAR(30)  NOT NULL,
    amount            BIGINT       NOT NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    pg_transaction_id VARCHAR(255),
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. shedlock (분산 스케줄러 락)
-- =============================================================
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. indexes
-- =============================================================
CREATE INDEX idx_bookings_user_id    ON bookings (user_id);
CREATE INDEX idx_bookings_product_id ON bookings (product_id);
CREATE INDEX idx_bookings_status     ON bookings (status);
CREATE INDEX idx_payments_booking_id ON payments (booking_id);

-- 6. seed data
-- =============================================================
INSERT INTO users (username, email) VALUES
    ('user1', 'user1@example.com'),
    ('user2', 'user2@example.com'),
    ('user3', 'user3@example.com');

INSERT INTO products (name, price, check_in_date, check_out_date, total_inventory, remaining_stock) VALUES
    ('서울 프리미엄 호텔 디럭스룸', 150000, '2026-05-01', '2026-05-02', 10, 10),
    ('제주 오션뷰 리조트',          200000, '2026-05-10', '2026-05-12',  5,  5),
    ('부산 해운대 비치호텔',         120000, '2026-06-01', '2026-06-02',  3,  3);
