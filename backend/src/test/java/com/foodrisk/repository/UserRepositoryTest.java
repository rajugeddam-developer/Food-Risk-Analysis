package com.foodrisk.repository;

import com.foodrisk.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Rollback
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Should persist and retrieve User entity with auto-populated timestamps")
    void testSaveAndFindUser() {
        User user = new User("Jane Doe", "jane.doe@example.com", "dummy_hash_for_m2");
        User savedUser = userRepository.saveAndFlush(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getName()).isEqualTo("Jane Doe");
        assertThat(savedUser.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("dummy_hash_for_m2");
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();

        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("jane.doe@example.com");
    }

    @Test
    @DisplayName("Should find user by email and verify existsByEmail")
    void testFindByEmailAndExistsByEmail() {
        User user = new User("Lookup User", "lookup@example.com", "hash_lookup");
        userRepository.saveAndFlush(user);

        Optional<User> found = userRepository.findByEmail("lookup@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Lookup User");

        assertThat(userRepository.existsByEmail("lookup@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nonexistent@example.com")).isFalse();
    }

    @Test
    @DisplayName("Should enforce email uniqueness at the database level")
    void testEmailUniquenessConstraint() {
        User user1 = new User("User One", "duplicate@example.com", "hash_one");
        userRepository.saveAndFlush(user1);

        User user2 = new User("User Two", "duplicate@example.com", "hash_two");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
