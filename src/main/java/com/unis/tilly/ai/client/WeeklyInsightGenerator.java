package com.unis.tilly.ai.client;

import com.unis.tilly.ai.dto.WeeklyInsightInput;
import com.unis.tilly.ai.dto.WeeklyInsightResult;

// 주간 인사이트 생성 포트 (API_SPEC 9.2). 구현은 GeminiWeeklyInsightGenerator.
// 서버 검증(개수·20자·근거·제목 숫자)을 통과한 결과만 돌려준다. 실패 시 CustomException(AI_GENERATION_FAILED / AI_TIMEOUT)
public interface WeeklyInsightGenerator {

    WeeklyInsightResult generate(WeeklyInsightInput input);
}
