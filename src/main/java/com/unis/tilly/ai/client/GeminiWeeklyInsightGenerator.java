package com.unis.tilly.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.ai.dto.WeeklyInsightInput;
import com.unis.tilly.ai.dto.WeeklyInsightResult;
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

// 주간 인사이트 생성 (9.2). 수치는 서버가 계산해 주고, 응답은 AiResponseValidator로 검증한다 (D-112)
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiWeeklyInsightGenerator implements WeeklyInsightGenerator {

    private final GeminiClient geminiClient;
    private final PromptTemplates promptTemplates;
    private final AiResponseValidator validator;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WeeklyInsightResult generate(WeeklyInsightInput input) {
        Prompt prompt = promptTemplates.render(PromptType.WEEKLY_INSIGHT, Map.of("records", toJson(data(input))));

        for (int attempt = 0; ; attempt++) {
            try {
                return validator.weeklyInsight(geminiClient.generate(prompt, GeminiClient.CallKind.WEEKLY_INSIGHT), input);
            } catch (AiResponseInvalidException e) {
                log.warn("주간 인사이트 응답 검증 실패: {} (시도 {}/{})", e.getMessage(), attempt + 1,
                        properties.getValidationRetries() + 1);
                if (attempt >= properties.getValidationRetries()) {
                    throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
                }
            }
        }
    }

    private Map<String, Object> data(WeeklyInsightInput input) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekStart", input.weekStart().toString());
        data.put("weekEnd", input.weekEnd().toString());
        data.put("weekLabel", input.weekLabel());
        data.put("stats", stats(input.stats()));
        data.put("previousWeekStats", input.previousWeekStats() == null ? null : stats(input.previousWeekStats()));
        data.put("recordedDays", input.days().size());
        data.put("insufficientContent", input.insufficientContent());
        List<Map<String, Object>> days = input.days().stream().map(day -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("date", day.date().toString());
            map.put("tasks", day.tasks());
            map.put("answers", day.answers());
            return map;
        }).toList();
        data.put("days", days);
        return data;
    }

    private Map<String, Object> stats(WeeklyInsightInput.WeekStats stats) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalCount", stats.totalCount());
        map.put("completedCount", stats.completedCount());
        map.put("completionRate", stats.completionRate());
        return map;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
        }
    }
}
