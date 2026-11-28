package com.unis.tilly.record.service;

import com.unis.tilly.ai.client.QuestionGenerator;
import com.unis.tilly.ai.dto.AiTaskInput;
import com.unis.tilly.ai.dto.QuestionGenerationInput;
import com.unis.tilly.ai.dto.WeeklyInsightInput;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.record.entity.RecordQuestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 6.5 질문 받기, 6.6 자동 질문 생성의 흐름 (RC-06, D-46, D-47, D-127): AI 호출은 저장 트랜잭션 밖
@ExtendWith(MockitoExtension.class)
class RecordQuestionServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 21);
    private static final QuestionGenerationInput INPUT =
            new QuestionGenerationInput(DATE, List.of(new AiTaskInput("보고서", true, null)), null, null,
                    new WeeklyInsightInput.Profile(null, null, List.of()));

    @Mock
    private RecordService recordService;
    @Mock
    private QuestionGenerator questionGenerator;
    @InjectMocks
    private RecordQuestionService service;

    private final RecordService.RecordContent content = new RecordService.RecordContent(null, null, null);

    @Test
    void RC06_질문이_이미_있으면_AI를_호출하지_않고_기존_질문을_돌려준다() {
        RecordQuestion existing = new RecordQuestion(100L, 1, "기존 질문");
        given(recordService.prepareQuestions(1L, "2026-10-21", content))
                .willReturn(new RecordService.QuestionPreparation(DATE, List.of(existing), null));

        RecordQuestionService.QuestionResult result = service.getOrGenerate(1L, "2026-10-21", content);

        assertThat(result.questions()).containsExactly(existing);
        verify(questionGenerator, never()).generate(any());
        verify(recordService, never()).storeQuestions(any(), any(), any());
    }

    @Test
    void RC06_질문이_없으면_AI로_만들어_저장한_질문을_돌려준다() {
        RecordQuestion saved = new RecordQuestion(100L, 1, "새 질문");
        given(recordService.prepareQuestions(1L, "2026-10-21", content))
                .willReturn(new RecordService.QuestionPreparation(DATE, List.of(), INPUT));
        given(questionGenerator.generate(INPUT)).willReturn(List.of("새 질문"));
        given(recordService.storeQuestions(1L, DATE, List.of("새 질문"))).willReturn(List.of(saved));

        assertThat(service.getOrGenerate(1L, "2026-10-21", content).questions()).containsExactly(saved);
    }

    @Test
    void RC06_AI가_실패하면_질문을_저장하지_않고_오류를_그대로_올린다() {
        given(recordService.prepareQuestions(1L, "2026-10-21", content))
                .willReturn(new RecordService.QuestionPreparation(DATE, List.of(), INPUT));
        given(questionGenerator.generate(INPUT)).willThrow(new CustomException(ErrorCode.AI_TIMEOUT));

        assertThatThrownBy(() -> service.getOrGenerate(1L, "2026-10-21", content))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.AI_TIMEOUT));
        verify(recordService, never()).storeQuestions(any(), any(), any());
    }

    @Test
    void RC06_할_일도_회고도_없으면_AI를_호출하지_않고_RECORD_EMPTY() {
        given(recordService.prepareQuestions(1L, "2026-10-21", content))
                .willThrow(new CustomException(ErrorCode.RECORD_EMPTY));

        assertThatThrownBy(() -> service.getOrGenerate(1L, "2026-10-21", content))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.RECORD_EMPTY));
        verify(questionGenerator, never()).generate(any());
    }

    @Test
    void RC06_동시_요청으로_저장이_겹치면_먼저_저장된_질문을_돌려준다() {
        RecordQuestion first = new RecordQuestion(100L, 1, "먼저 저장된 질문");
        given(recordService.prepareQuestions(1L, "2026-10-21", content))
                .willReturn(new RecordService.QuestionPreparation(DATE, List.of(), INPUT));
        given(questionGenerator.generate(INPUT)).willReturn(List.of("내 질문"));
        given(recordService.storeQuestions(1L, DATE, List.of("내 질문")))
                .willThrow(new DataIntegrityViolationException("duplicate"));
        given(recordService.findQuestions(1L, DATE)).willReturn(List.of(first));

        assertThat(service.getOrGenerate(1L, "2026-10-21", content).questions()).containsExactly(first);
    }

    @Test
    void RC06_저장_실패인데_저장된_질문도_없으면_원래_오류를_올린다() {
        given(recordService.prepareQuestions(1L, "2026-10-21", content))
                .willReturn(new RecordService.QuestionPreparation(DATE, List.of(), INPUT));
        given(questionGenerator.generate(INPUT)).willReturn(List.of("질문"));
        given(recordService.storeQuestions(1L, DATE, List.of("질문")))
                .willThrow(new DataIntegrityViolationException("other"));
        given(recordService.findQuestions(1L, DATE)).willReturn(List.of());

        assertThatThrownBy(() -> service.getOrGenerate(1L, "2026-10-21", content))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- 자동 (6.6) ----

    @Test
    void RC06_자동_질문은_건너뛸_조건이면_AI를_호출하지_않는다() {
        given(recordService.prepareAutoQuestions(1L, DATE)).willReturn(Optional.empty());

        assertThat(service.generateAutomatically(1L, DATE)).isFalse();
        verify(questionGenerator, never()).generate(any());
    }

    @Test
    void RC06_자동_질문은_AI로_만들어_저장한다() {
        given(recordService.prepareAutoQuestions(1L, DATE)).willReturn(Optional.of(INPUT));
        given(questionGenerator.generate(INPUT)).willReturn(List.of("질문1", "질문2"));

        assertThat(service.generateAutomatically(1L, DATE)).isTrue();
        verify(recordService).storeQuestions(1L, DATE, List.of("질문1", "질문2"));
    }

    @Test
    void RC06_AI가_질문할_게_없다고_하면_저장하지_않고_빈_목록을_돌려준다() {
        given(recordService.prepareQuestions(1L, "2026-10-21", content))
                .willReturn(new RecordService.QuestionPreparation(DATE, List.of(), INPUT));
        given(questionGenerator.generate(INPUT)).willReturn(List.of());

        assertThat(service.getOrGenerate(1L, "2026-10-21", content).questions()).isEmpty();
        verify(recordService, never()).storeQuestions(any(), any(), any());
    }

    @Test
    void RC06_자동_질문은_AI가_질문할_게_없다고_하면_저장하지_않고_false() {
        given(recordService.prepareAutoQuestions(1L, DATE)).willReturn(Optional.of(INPUT));
        given(questionGenerator.generate(INPUT)).willReturn(List.of());

        assertThat(service.generateAutomatically(1L, DATE)).isFalse();
        verify(recordService, never()).storeQuestions(any(), any(), any());
    }

    @Test
    void RC06_자동_질문의_AI_실패는_호출한_쪽으로_올라가고_저장하지_않는다() {
        given(recordService.prepareAutoQuestions(1L, DATE)).willReturn(Optional.of(INPUT));
        given(questionGenerator.generate(INPUT)).willThrow(new CustomException(ErrorCode.AI_GENERATION_FAILED));

        assertThatThrownBy(() -> service.generateAutomatically(1L, DATE)).isInstanceOf(CustomException.class);
        verify(recordService, never()).storeQuestions(any(), any(), any());
    }
}
