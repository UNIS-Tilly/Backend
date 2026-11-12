package com.unis.tilly.ai.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 무료 모델만 호출: 허용 목록에 없는 모델이면 기동하지 않는다 (사용자 결정 2026-11-12)
class GeminiPropertiesTest {

    @Test
    void 허용_목록에_있는_모델이면_통과한다() {
        GeminiProperties properties = properties("free-model", List.of("other", " free-model "));

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void 허용_목록에_없는_모델이면_기동_실패하고_키는_메시지에_없다() {
        GeminiProperties properties = properties("paid-model", List.of("free-model"));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("허용 목록")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret-key"));
    }

    @Test
    void 허용_목록이_비어_있거나_키가_없으면_기동_실패() {
        assertThatThrownBy(properties("free-model", List.of())::validate).isInstanceOf(IllegalStateException.class);

        GeminiProperties noKey = properties("free-model", List.of("free-model"));
        noKey.setApiKey(" ");
        assertThatThrownBy(noKey::validate).isInstanceOf(IllegalStateException.class);
    }

    static GeminiProperties properties(String model, List<String> allowed) {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("secret-key");
        properties.setBaseUrl("http://localhost:1");
        properties.setModel(model);
        properties.setAllowedModels(allowed);
        properties.setQuestionTimeout(Duration.ofSeconds(1));
        properties.setInsightTimeout(Duration.ofSeconds(1));
        properties.setMaxRetries(2);
        properties.setRetryBackoff(Duration.ofMillis(1));
        properties.setValidationRetries(1);
        return properties;
    }
}
