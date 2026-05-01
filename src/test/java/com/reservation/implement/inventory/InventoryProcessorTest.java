package com.reservation.implement.inventory;

import com.reservation.api.exception.SoldOutException;
import com.reservation.domain.product.Product;
import com.reservation.infrastructure.persistence.ProductRepository;
import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryProcessorTest {

    private StringRedisTemplate redisTemplate;
    private ProductRepository productRepository;
    private RedisScript<Long> decrementStockScript;
    private ApplicationEventPublisher eventPublisher;
    private InventoryProcessor inventoryProcessor;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        productRepository = mock(ProductRepository.class);
        decrementStockScript = mock(RedisScript.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        valueOps = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        inventoryProcessor = new InventoryProcessor(redisTemplate, productRepository, decrementStockScript, eventPublisher);
    }

    @Test
    void decrementStock_luaReturnsPositive_shouldProceedToDbDecrement() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(5L);
        when(productRepository.decrementStock(1L)).thenReturn(1);

        inventoryProcessor.decrementStock(1L);

        verify(productRepository).decrementStock(1L);
    }

    @Test
    void decrementStock_luaReturnsSoldOut_shouldThrowSoldOutException() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(-1L);

        assertThatThrownBy(() -> inventoryProcessor.decrementStock(1L))
                .isInstanceOf(SoldOutException.class);

        verify(productRepository, never()).decrementStock(any());
    }

    @Test
    void decrementStock_luaReturnsUninitialized_shouldInitRedisAndRetry() {
        Product product = TestFixtures.createProduct(1L, "호텔", 150000L, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(-2L)
                .thenReturn(9L);
        when(productRepository.decrementStock(1L)).thenReturn(1);

        inventoryProcessor.decrementStock(1L);

        verify(valueOps).setIfAbsent(eq("inventory:stock:1"), eq("10"));
        verify(productRepository).decrementStock(1L);
    }

    @Test
    void decrementStock_luaReturnsUninitializedThenSoldOut_shouldThrowSoldOutException() {
        Product product = TestFixtures.createProduct(1L, "호텔", 150000L, 0);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenReturn(-2L)
                .thenReturn(-1L);

        assertThatThrownBy(() -> inventoryProcessor.decrementStock(1L))
                .isInstanceOf(SoldOutException.class);

        verify(productRepository, never()).decrementStock(any());
    }

    @Test
    void decrementStock_dbReturnsZero_shouldThrowSoldOutException() {
        when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(1L);
        when(productRepository.decrementStock(1L)).thenReturn(0);

        assertThatThrownBy(() -> inventoryProcessor.decrementStock(1L))
                .isInstanceOf(SoldOutException.class);
    }

    @Test
    void decrementStockFallback_shouldCallDbDecrement() {
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenThrow(new RuntimeException("Redis 연결 불가"));
        when(productRepository.decrementStock(1L)).thenReturn(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> inventoryProcessor.decrementStock(1L)
        ).isNotInstanceOf(SoldOutException.class);
    }

    @Test
    void incrementStock_shouldCallBothRedisAndDb() {
        inventoryProcessor.incrementStock(1L);

        verify(valueOps).increment("inventory:stock:1");
        verify(productRepository).incrementStock(1L);
    }

    @Test
    void incrementStock_redisFailure_shouldStillCallDb() {
        doThrow(new RuntimeException("Redis 연결 불가")).when(valueOps).increment(anyString());

        inventoryProcessor.incrementStock(1L);

        verify(productRepository).incrementStock(1L);
    }
}
