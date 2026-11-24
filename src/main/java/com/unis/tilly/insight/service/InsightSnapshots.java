package com.unis.tilly.insight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.insight.dto.InsightSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;

// 스냅샷·태그를 JSON 문자열로 저장·복원한다. 사용자 기록 원문은 들어 있지 않고 수치와 짧은 키워드뿐이다.
@Component
public class InsightSnapshots {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷을 직렬화하지 못했습니다.", e);
        }
    }

    public InsightSnapshot.Achievement readAchievement(String json) {
        return read(json, new TypeReference<>() {
        });
    }

    public List<InsightSnapshot.FlowWeek> readFlow(String json) {
        return read(json, new TypeReference<>() {
        });
    }

    public List<String> readTags(String json) {
        return read(json, new TypeReference<>() {
        });
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷을 읽지 못했습니다.", e);
        }
    }
}
