package com.unis.tilly.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

// 스케줄러 + 다중 인스턴스 중복 실행 방지 (D-138, API_SPEC 6.6). 락은 MySQL의 shedlock 테이블(V10)에 둔다.
// 락 시간은 1분 미만: 한 번의 실행이 최대 55초 동안만 락을 쥐고, 스케줄은 매분이다.
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT55S")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // 인스턴스 간 시계 차이에 영향받지 않도록 DB 시각을 쓴다
                .build());
    }
}
