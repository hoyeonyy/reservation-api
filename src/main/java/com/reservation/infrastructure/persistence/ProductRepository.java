package com.reservation.infrastructure.persistence;

import com.reservation.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.remainingStock = p.remainingStock - 1 WHERE p.id = :id AND p.remainingStock > 0")
    int decrementStock(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.remainingStock = p.remainingStock + 1 WHERE p.id = :id")
    int incrementStock(@Param("id") Long id);
}
