package com.unis.tilly.ai.dto;

import java.time.LocalDate;
import java.util.List;

// 6.5 확장 질문 생성 입력: 그날 보이는 할 일 제목·완료 여부·메모 (API_SPEC 6.5 [제안], 질문 생성 단위에서 확정)
// reflection: 그날 하루 회고 (없으면 null, D-126). 할 일이 없고 회고만 있는 날도 입력이 될 수 있다 (D-127)
// currentJob: 재직 직군·세부 직군 (재직 직군이 없으면 null). 직군만으로는 입력이 되지 않는다
// profile: 희망 직군(없으면 null)과 선택 역량. 어떤 경험을 구체화하면 좋을지 고르는 참고용이다 (D-133 보완).
//          희망 직군·역량은 설정 여부와 관계없이 항상 들어 있고(역량은 빈 목록일 수 있음), 질문 생성 조건은 되지 않는다
public record QuestionGenerationInput(LocalDate date, List<AiTaskInput> tasks, String reflection,
                                      AiCurrentJob currentJob, WeeklyInsightInput.Profile profile) {
}
