package com.unis.tilly.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Refresh Token 쿠키 속성. local 프로파일에서는 http로도 테스트할 수 있게 완화한다 (application.yml 참고).
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cookie")
public class CookieProperties {

    private boolean secure;
    private String sameSite;
}
