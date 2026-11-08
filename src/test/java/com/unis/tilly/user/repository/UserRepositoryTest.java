package com.unis.tilly.user.repository;

import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Flyway V1__create_users_table.sql의 유니크 제약(provider, provider_user_id)을 실제 MySQL로 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("tilly")
            .withUsername("tilly")
            .withPassword("tilly");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void provider와_providerUserId로_조회할_수_있다() {
        userRepository.save(new User("user@example.com", "틸리", SocialProvider.GOOGLE, "sub-100"));

        Optional<User> found = userRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "sub-100");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void 존재하지_않는_provider_조합은_빈값이다() {
        Optional<User> found = userRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "no-such-sub");

        assertThat(found).isEmpty();
    }

    @Test
    void 같은_provider_providerUserId는_중복_저장할_수_없다() {
        userRepository.saveAndFlush(new User("a@example.com", "가가", SocialProvider.GOOGLE, "dup-sub"));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(new User("b@example.com", "나나", SocialProvider.GOOGLE, "dup-sub")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
