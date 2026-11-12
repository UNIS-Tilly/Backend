package com.unis.tilly.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.ai.dto.QuestionGenerationInput;
import com.unis.tilly.ai.prompt.Prompt;
import com.unis.tilly.ai.prompt.PromptTemplates;
import com.unis.tilly.ai.prompt.PromptType;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 확장 질문 생성 (6.5, 6.6). 사용자 기록은 JSON 데이터로만 프롬프트에 넣는다 (프롬프트 인젝션 방어)
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiQuestionGenerator implements QuestionGenerator {

    private final GeminiClient geminiClient;
    private final PromptTemplates promptTemplates;
    private final AiResponseValidator validator;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> generate(QuestionGenerationInput input) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", input.date().toString());
        data.put("tasks", input.tasks());
        Prompt prompt = promptTemplates.render(PromptType.QUESTION, Map.of("records", toJson(data)));

        for (int attempt = 0; ; attempt++) {
            try {
                return validator.questions(geminiClient.generate(prompt, GeminiClient.CallKind.QUESTION));
            } catch (AiResponseInvalidException e) {
                log.warn("확장 질문 응답 검증 실패: {} (시도 {}/{})", e.getMessage(), attempt + 1,
                        properties.getValidationRetries() + 1);
                if (attempt >= properties.getValidationRetries()) {
                    throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
                }
            }
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
        }
    }
}
