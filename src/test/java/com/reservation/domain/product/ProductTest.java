package com.reservation.domain.product;

import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    void product_shouldReturnCorrectFields() {
        Product product = TestFixtures.createProduct(1L, "호텔", 150000L, 5);

        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("호텔");
        assertThat(product.getPrice()).isEqualTo(150000L);
        assertThat(product.getRemainingStock()).isEqualTo(5);
    }
}
