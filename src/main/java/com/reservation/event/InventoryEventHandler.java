package com.reservation.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventHandler {

    private static final String STOCK_KEY_PREFIX = "inventory:stock:";

    private final StringRedisTemplate redisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleRollback(StockDecrementedEvent event) {
        try {
            redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + event.productId());
            log.warn("트랜잭션 롤백으로 Redis 재고 복구: productId={}", event.productId());
        } catch (Exception e) {
            log.error("Redis 재고 복구 실패 (운영 모니터링 필요): productId={}, error={}",
                    event.productId(), e.getMessage());
        }
    }
}
