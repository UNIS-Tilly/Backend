package com.unis.tilly.record.service;

import com.unis.tilly.ai.client.QuestionGenerator;
import com.unis.tilly.ai.dto.QuestionGenerationInput;
import com.unis.tilly.record.entity.RecordQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// 6.5 질문 받기, 6.6 자동 질문 생성 (RC-06, D-46, D-47, D-127).
// 외부 AI 호출은 길 수 있으므로(최대 15초) DB 트랜잭션 밖에서 한다:
// 저장·조회는 RecordService의 짧은 트랜잭션 두 번(AI 호출 전 / 후)으로 나눈다. 이 클래스는 트랜잭션을 걸지 않는다.
@Service
@RequiredArgsConstructor
public class RecordQuestionService {

    private final RecordService recordService;
    private final QuestionGenerator questionGenerator;

    // 6.5: 현재 입력을 먼저 저장하고, 질문이 이미 있으면 그대로(재생성 없음), 없으면 AI로 0~3개를 만든다(질문할 게 없으면 빈 목록)
    public QuestionResult getOrGenerate(Long userId, String dateText, RecordService.RecordContent content) {
        RecordService.QuestionPreparation prepared = recordService.prepareQuestions(userId, dateText, content);
        if (!prepared.existing().isEmpty()) {
            return new QuestionResult(prepared.existing());
        }
        List<String> texts = questionGenerator.generate(prepared.input());
        if (texts.isEmpty()) {
            return new QuestionResult(List.of());   // AI가 질문할 게 없다고 판단: 아무것도 저장하지 않는다
        }
        return new QuestionResult(store(userId, prepared.date(), texts));
    }

    // 6.6: 스케줄러가 호출. 건너뛸 조건(질문 있음·잠김·COMPLETED·완료한 할 일도 회고도 없음)이면 아무것도 하지 않는다.
    // 질문을 저장하면 true (AI가 질문할 게 없다고 하면 false). AI 실패는 예외로 올라가고 호출한 스케줄러가 건너뛴다(보충 실행 없음, 사용자 결정 2026-11-24)
    public boolean generateAutomatically(Long userId, LocalDate date) {
        Optional<QuestionGenerationInput> input = recordService.prepareAutoQuestions(userId, date);
        if (input.isEmpty()) {
            return false;
        }
        List<String> texts = questionGenerator.generate(input.get());
        if (texts.isEmpty()) {
            return false;   // AI가 질문할 게 없다고 판단: 저장하지 않는다
        }
        store(userId, date, texts);
        return true;
    }

    // 같은 날 요청이 동시에 겹치면 두 번째 저장이 유니크 제약에 걸린다. 먼저 저장된 질문을 돌려준다 (D-47)
    private List<RecordQuestion> store(Long userId, LocalDate date, List<String> texts) {
        try {
            return recordService.storeQuestions(userId, date, texts);
        } catch (DataIntegrityViolationException e) {
            List<RecordQuestion> existing = recordService.findQuestions(userId, date);
            if (existing.isEmpty()) {
                throw e;
            }
            return existing;
        }
    }

    public record QuestionResult(List<RecordQuestion> questions) {
    }
}
