package com.unis.tilly.ai.prompt;

import com.fasterxml.jackson.databind.JsonNode;

// AI 호출 한 번에 필요한 지시문(system) · 사용자 데이터(user) · 응답 스키마
public record Prompt(String systemInstruction, String userContent, JsonNode responseSchema) {
}
