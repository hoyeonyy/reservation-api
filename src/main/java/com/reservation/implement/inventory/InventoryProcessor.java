package com.reservation.implement.inventory;

import com.reservation.api.exception.EntityNotFoundException;
import com.reservation.api.exception.SoldOutException;
import com.reservation.domain.product.Product;
import com.reservation.event.StockDecrementedEvent;
import com.reservation.infrastructure.persistence.ProductRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryProcessor {

    private static final String STOCK_KEY_PREFIX = "inventory:stock:";

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;
    private final RedisScript<Long> decrementStockScript;
    private final ApplicationEventPublisher eventPublisher;

    @CircuitBreaker(name = "redisInventory", fallbackMethod = "decrementStockFallback")
    public void decrementStock(Long productId) {
        redisDecrement(productId);
        dbDecrement(productId);
    }

    private void redisDecrement(Long productId) {
        Long result = redisTemplate.execute(decrementStockScript, Collections.singletonList(stockKey(productId)));

        if (result == null || result == -2L) {
            int dbStock = productRepository.findById(productId)
                    .map(Product::getRemainingStock)
                    .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다: " + productId));
            redisTemplate.opsForValue().setIfAbsent(stockKey(productId), String.valueOf(dbStock));
            result = redisTemplate.execute(decrementStockScript, Collections.singletonList(stockKey(productId)));
        }

        if (result == null || result == -1L) {
            throw new SoldOutException(productId);
        }

        eventPublisher.publishEvent(new StockDecrementedEvent(productId));
    }

    private void dbDecrement(Long productId) {
        int affected = productRepository.decrementStock(productId);
        if (affected == 0) {
            throw new SoldOutException(productId);
        }
    }

    @SuppressWarnings("unused")
    private void decrementStockFallback(Long productId, Exception e) {
        log.warn("Redis 장애 감지, DB fallback: productId={}, error={}", productId, e.getMessage());
        dbDecrement(productId);
    }

    @Transactional
    public void incrementStock(Long productId) {
        try {
            redisTemplate.opsForValue().increment(stockKey(productId));
        } catch (Exception e) {
            log.warn("Redis 재고 복구 실패 (무시): productId={}, error={}", productId, e.getMessage());
        }
        productRepository.incrementStock(productId);
    }

    private String stockKey(Long productId) {
        return STOCK_KEY_PREFIX + productId;
    }
}
