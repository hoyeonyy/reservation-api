package com.reservation.domain.user;

import com.reservation.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void createUser_shouldHaveCorrectFields() {
        User user = TestFixtures.createUser(1L, "user1", "user1@test.com");

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getUsername()).isEqualTo("user1");
        assertThat(user.getEmail()).isEqualTo("user1@test.com");
    }
}
