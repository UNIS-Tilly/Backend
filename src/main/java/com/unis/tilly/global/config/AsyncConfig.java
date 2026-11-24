package com.unis.tilly.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

// 인사이트 생성용 스레드풀 (사용자 결정 2026-11-24: 서버 내 스레드풀). 무료 모델의 호출 한도를 고려해 동시 실행은 2개로 제한한다.
// 대기열이 가득 차면 요청을 거절하고(InsightGenerationService가 실패 처리) 잠금을 남기지 않는다.
@Configuration
public class AsyncConfig {

    @Bean(name = "insightExecutor")
    public ThreadPoolTaskExecutor insightExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("insight-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
