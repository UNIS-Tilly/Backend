package com.unis.tilly.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// 보안 판단(만료 등)을 포함한 모든 시각 계산은 LocalDateTime.now() 대신 이 Clock을 주입받아 쓴다.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
