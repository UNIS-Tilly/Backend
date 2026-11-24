package com.unis.tilly.global.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// CORS 허용 출처 목록 (D-73, NOTICE-018). 값은 application.yml의 app.cors.allowed-origins(환경변수 CORS_ALLOWED_ORIGINS)로 받는다.
// 목록이 비어 있으면 어떤 출처도 허용하지 않는다. `*`나 빈 값은 쿠키(allowCredentials)와 함께 쓸 수 없으므로 기동 시 거부한다.
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();

    @PostConstruct
    void validate() {
        for (String origin : allowedOrigins) {
            if (origin == null || origin.isBlank() || origin.contains("*")) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins에는 와일드카드(*)나 빈 값을 쓸 수 없습니다. 출처를 명시하세요.");
            }
        }
    }
}
