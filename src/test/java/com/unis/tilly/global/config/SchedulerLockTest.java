package com.unis.tilly.global.config;

import com.unis.tilly.auth.client.GoogleOidcClient;
import com.unis.tilly.record.scheduler.AutoQuestionScheduler;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// 6.6 / D-138: ShedLock이 MySQL(V10 shedlock 테이블)에서 같은 이름의 락을 한 번에 하나만 내준다
// 스케줄러를 켠 상태(record.auto-question.enabled=true)로 컨텍스트를 띄워 @Scheduled + ShedLock 프록시가 Boot 4에서 동작하는지도 확인한다
@SpringBootTest
@TestPropertySource(properties = "record.auto-question.enabled=true")
@Testcontainers
class SchedulerLockTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("tilly")
            .withUsername("tilly")
            .withPassword("tilly");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @MockitoBean
    private GoogleOidcClient googleOidcClient;

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private Optional<AutoQuestionScheduler> autoQuestionScheduler;

    @Test
    void RC06_같은_이름의_락은_한_번에_하나만_얻고_풀면_다시_얻는다() {
        LockConfiguration config = new LockConfiguration(Instant.now(), "auto-question-test", Duration.ofSeconds(30), Duration.ZERO);

        Optional<SimpleLock> first = lockProvider.lock(config);
        Optional<SimpleLock> second = lockProvider.lock(config);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();   // 다른 인스턴스가 같은 시각에 실행해도 건너뛴다

        first.get().unlock();
        assertThat(lockProvider.lock(config)).isPresent();
    }

    @Test
    void RC06_설정으로_켜면_자동_질문_스케줄러_빈이_등록된다() {
        assertThat(autoQuestionScheduler).isPresent();
    }

    @Test
    void RC06_다른_이름의_락은_서로_막지_않는다() {
        Optional<SimpleLock> a = lockProvider.lock(
                new LockConfiguration(Instant.now(), "lock-a", Duration.ofSeconds(30), Duration.ZERO));
        Optional<SimpleLock> b = lockProvider.lock(
                new LockConfiguration(Instant.now(), "lock-b", Duration.ofSeconds(30), Duration.ZERO));

        assertThat(a).isPresent();
        assertThat(b).isPresent();
    }
}
