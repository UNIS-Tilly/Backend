package com.unis.tilly.ai.client;

import com.unis.tilly.ai.dto.QuestionGenerationInput;

import java.util.List;

// 확장 질문 생성 포트 (API_SPEC 6.5, 6.6). 구현은 GeminiQuestionGenerator. 다른 도메인은 이 인터페이스만 쓴다.
// 실패 시 CustomException(AI_GENERATION_FAILED / AI_TIMEOUT)
public interface QuestionGenerator {

    // 1~3개의 질문 문장
    List<String> generate(QuestionGenerationInput input);
}
