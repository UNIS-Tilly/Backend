package com.unis.tilly.ai.client;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// Gemini 호출 설정 (application.yml의 ai.gemini, 값은 .env). 키·모델·URL은 코드에 쓰지 않는다.
// "무료 모델만 호출" 보장: 사용할 모델(model)이 허용 목록(allowed-models)에 없으면 기동하지 않는다 (사용자 결정 2026-11-12).
// 허용 목록에는 무료로 쓸 수 있음을 사용자가 직접 확인한 모델만 넣는다 (NOTICE-016).
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.gemini")
public class GeminiProperties {

    private String apiKey;
    private String baseUrl;
    private String model;
    private List<String> allowedModels = new ArrayList<>();
    private Duration questionTimeout;
    private Duration insightTimeout;
    // 호출 한도 초과(429)·일시 오류(5xx) 재시도 횟수와 첫 대기 시간(재시도마다 2배)
    private int maxRetries;
    private Duration retryBackoff;
    // 응답이 검증(개수·20자·근거·숫자)을 통과하지 못했을 때 다시 생성하는 횟수 (D-57)
    private int validationRetries;

    @PostConstruct
    void validate() {
        require(apiKey, "ai.gemini.api-key(GEMINI_API_KEY)");
        require(baseUrl, "ai.gemini.base-url(GEMINI_BASE_URL)");
        require(model, "ai.gemini.model(GEMINI_MODEL)");
        List<String> allowed = allowedModels.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (!allowed.contains(model.strip())) {
            // 키는 메시지에 넣지 않는다
            throw new IllegalStateException("ai.gemini.model이 허용 목록(GEMINI_ALLOWED_MODELS)에 없습니다. "
                    + "무료로 쓸 수 있는 모델인지 확인하고 허용 목록에 추가하세요: " + model);
        }
        if (questionTimeout == null || insightTimeout == null || retryBackoff == null
                || maxRetries < 0 || validationRetries < 0) {
            throw new IllegalStateException("ai.gemini의 시간 초과·재시도 설정이 올바르지 않습니다.");
        }
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 값이 필요합니다.");
        }
    }
}
