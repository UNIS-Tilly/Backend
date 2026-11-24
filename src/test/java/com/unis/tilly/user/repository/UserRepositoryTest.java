package com.unis.tilly.user.repository;

import com.unis.tilly.global.config.ClockConfig;
import com.unis.tilly.global.config.JpaAuditingConfig;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Flyway V1__create_users_table.sql의 유니크 제약(provider, provider_user_id)을 실제 MySQL로 확인한다.
// 슬라이스 테스트는 @Configuration을 읽지 않으므로 생성·수정 시각(Auditing)에 필요한 설정을 직접 불러온다.
// (없으면 단독 실행 시 created_at이 null로 들어가 실패한다. 전체 실행에서는 다른 테스트가 남긴 상태에 가려져 있었다.)
@DataJpaTest
@Import({ClockConfig.class, JpaAuditingConfig.class})
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

    // 6.6 자동 질문: 퇴근 시간이 정확히 일치하는 사용자만, 미설정(null)은 제외
    @Test
    void 퇴근_시간이_정확히_일치하는_사용자_ID만_조회한다() {
        User at18 = withWorkEndTime("a@example.com", "s-a", LocalTime.of(18, 0));
        User also18 = withWorkEndTime("b@example.com", "s-b", LocalTime.of(18, 0));
        withWorkEndTime("c@example.com", "s-c", LocalTime.of(18, 1));
        withWorkEndTime("d@example.com", "s-d", null);

        List<Long> ids = userRepository.findIdsByWorkEndTime(LocalTime.of(18, 0));

        assertThat(ids).containsExactly(at18.getId(), also18.getId());
        assertThat(userRepository.findIdsByWorkEndTime(LocalTime.of(3, 0))).isEmpty();
    }

    private User withWorkEndTime(String email, String sub, LocalTime workEndTime) {
        User user = new User(email, "틸리", SocialProvider.GOOGLE, sub);
        user.changeWorkEndTime(workEndTime);
        return userRepository.saveAndFlush(user);
    }
}
