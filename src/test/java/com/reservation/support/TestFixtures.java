package com.reservation.support;

import com.reservation.domain.product.Product;
import com.reservation.domain.user.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;

public class TestFixtures {

    public static User createUser(String username, String email) {
        return createUser(null, username, email);
    }

    public static User createUser(Long id, String username, String email) {
        try {
            Constructor<User> ctor = User.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            User user = ctor.newInstance();
            if (id != null) ReflectionTestUtils.setField(user, "id", id);
            ReflectionTestUtils.setField(user, "username", username);
            ReflectionTestUtils.setField(user, "email", email);
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Product createProduct(String name, long price, int remainingStock) {
        return createProduct(null, name, price, remainingStock);
    }

    public static Product createProduct(Long id, String name, long price, int remainingStock) {
        try {
            Constructor<Product> ctor = Product.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            Product product = ctor.newInstance();
            if (id != null) ReflectionTestUtils.setField(product, "id", id);
            ReflectionTestUtils.setField(product, "name", name);
            ReflectionTestUtils.setField(product, "price", price);
            ReflectionTestUtils.setField(product, "checkInDate", LocalDate.of(2026, 5, 1));
            ReflectionTestUtils.setField(product, "checkOutDate", LocalDate.of(2026, 5, 2));
            ReflectionTestUtils.setField(product, "totalInventory", remainingStock);
            ReflectionTestUtils.setField(product, "remainingStock", remainingStock);
            return product;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
