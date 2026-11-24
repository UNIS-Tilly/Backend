package com.unis.tilly.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// 프론트(별도 레포)와 CORS로 연결한다 (D-70, D-73). SecurityConfig의 .cors()가 이 빈(corsConfigurationSource)을 사용한다.
// - 출처: 명시한 목록만 (CorsProperties). `*` 금지.
// - 쿠키: Refresh Token이 HttpOnly 쿠키라서 allowCredentials=true (D-89, 사용자 확정 2026-11-24).
// - 메서드·헤더: 문서 기준으로 필요한 것만 연다. X-Tilly-Client는 reissue의 CSRF 방어용 커스텀 헤더다.
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600L;

    private final CorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(corsProperties.getAllowedOrigins()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tilly-Client"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
