package com.reservation.infrastructure.persistence;

import com.reservation.domain.product.Product;
import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProductRepository productRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        product = em.persist(TestFixtures.createProduct("호텔", 150000L, 10));
        em.flush();
        em.clear();
    }

    @Test
    void decrementStock_withAvailableStock_shouldReturnAffectedRows1() {
        int affected = productRepository.decrementStock(product.getId());
        em.clear();

        assertThat(affected).isEqualTo(1);
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getRemainingStock()).isEqualTo(9);
    }

    @Test
    void decrementStock_calledMultipleTimes_shouldDecrementCorrectly() {
        productRepository.decrementStock(product.getId());
        productRepository.decrementStock(product.getId());
        productRepository.decrementStock(product.getId());
        em.clear();

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getRemainingStock()).isEqualTo(7);
    }

    @Test
    void decrementStock_withNoStock_shouldReturnAffectedRows0() {
        for (int i = 0; i < 10; i++) {
            productRepository.decrementStock(product.getId());
        }
        em.clear();

        int affected = productRepository.decrementStock(product.getId());

        assertThat(affected).isEqualTo(0);
    }

    @Test
    void decrementStock_whereClause_preventsNegativeStock() {
        for (int i = 0; i < 10; i++) {
            productRepository.decrementStock(product.getId());
        }
        em.clear();

        productRepository.decrementStock(product.getId());
        productRepository.decrementStock(product.getId());
        em.clear();

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getRemainingStock()).isEqualTo(0);
    }

    @Test
    void incrementStock_shouldIncreaseRemainingStock() {
        productRepository.decrementStock(product.getId());
        em.clear();

        int affected = productRepository.incrementStock(product.getId());
        em.clear();

        assertThat(affected).isEqualTo(1);
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getRemainingStock()).isEqualTo(10);
    }
}
