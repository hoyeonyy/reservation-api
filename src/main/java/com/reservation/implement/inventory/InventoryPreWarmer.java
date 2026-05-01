package com.reservation.implement.inventory;

import com.reservation.infrastructure.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryPreWarmer implements ApplicationRunner {

    private static final String STOCK_KEY_PREFIX = "inventory:stock:";

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        productRepository.findAll().forEach(product -> {
            String key = STOCK_KEY_PREFIX + product.getId();
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(product.getRemainingStock()));
            log.debug("재고 Pre-warm: productId={}, stock={}", product.getId(), product.getRemainingStock());
        });
        log.info("재고 Pre-warm 완료 ({}개 상품)", productRepository.count());
    }
}
