package com.unis.tilly.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

// BaseEntity의 @CreatedDate/@LastModifiedDate가 동작하려면 Auditing이 켜져 있어야 한다.
// 생성·수정 시각도 서버 기본 타임존이 아니라 Asia/Seoul Clock(D-19)을 기준으로 기록한다.
// (배정일 없는 할 일은 생성일부터 노출되므로 날짜 경계가 서버 타임존에 따라 달라지면 안 된다)
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
