package com.unis.tilly.record.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.record.entity.DailyRecord;
import com.unis.tilly.record.entity.DailyReflection;
import com.unis.tilly.record.entity.RecordQuestion;
import com.unis.tilly.record.enums.RecordStatus;
import com.unis.tilly.record.repository.DailyRecordRepository;
import com.unis.tilly.record.repository.DailyReflectionRepository;
import com.unis.tilly.record.repository.RecordQuestionRepository;
import com.unis.tilly.task.service.TaskService;
import com.unis.tilly.user.service.UserService;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 6.1~6.4, 6.7 (D-95, D-101, D-106~D-108, 하루 회고 복원 2026-11-24)
@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 21);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 18, 5, 0);

    @Mock
    private DailyRecordRepository recordRepository;
    @Mock
    private RecordQuestionRepository questionRepository;
    @Mock
    private DailyReflectionRepository reflectionRepository;
    @Mock
    private TaskService taskService;
    @Mock
    private UserService userService;

    private RecordService recordService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        recordService = new RecordService(recordRepository, questionRepository, reflectionRepository, taskService, userService, clock);
    }

    // ---- 6.1 요약 ----

    @Test
    void HM10_기록이_없으면_NOT_STARTED이고_잠기지_않았다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(taskService.countMemosOn(1L, DATE)).willReturn(2);
        given(reflectionRepository.existsByUserIdAndDate(1L, DATE)).willReturn(true);

        RecordService.Summary summary = recordService.getSummary(1L, "2026-10-21");

        assertThat(summary.status()).isEqualTo(RecordStatus.NOT_STARTED);
        assertThat(summary.reflectionWritten()).isTrue();
        assertThat(summary.locked()).isFalse();
        assertThat(summary.memoCount()).isEqualTo(2);
        assertThat(summary.completedAt()).isNull();
    }

    @Test
    void HM10_날짜_형식이_잘못되면_400() {
        assertBadRequest(() -> recordService.getSummary(1L, "2026/10/21"));
    }

    // ---- 6.3 임시저장 ----

    @Test
    void RC12_저장된_적_없는_날에_빈_내용이면_기록을_만들지_않는다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());

        RecordService.DraftResult result = recordService.saveDraft(1L, "2026-10-21", true, emptyContent());

        assertThat(result.status()).isEqualTo(RecordStatus.NOT_STARTED);
        verify(recordRepository, never()).save(any());
    }

    @Test
    void RC12_수동_저장이고_메모가_있으면_DRAFT_기록을_만든다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(recordRepository.save(any(DailyRecord.class))).willAnswer(invocation -> invocation.getArgument(0));

        RecordService.DraftResult result = recordService.saveDraft(1L, "2026-10-21", true, memo(10L, "메모"));

        assertThat(result.status()).isEqualTo(RecordStatus.DRAFT);
        assertThat(result.savedAt()).isEqualTo(NOW);
        verify(taskService).replaceMemosOn(1L, DATE, Map.of(10L, "메모"));
    }

    @Test
    void RC12_자동저장은_메모만_저장하고_기록_상태를_만들지_않는다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());

        RecordService.DraftResult result = recordService.saveDraft(1L, "2026-10-21", false, memo(10L, "메모"));

        assertThat(result.status()).isEqualTo(RecordStatus.NOT_STARTED);
        verify(taskService).replaceMemosOn(1L, DATE, Map.of(10L, "메모"));
        verify(recordRepository, never()).save(any());
    }

    @Test
    void RC12_자동저장은_DRAFT를_되돌리지_않는다() {
        DailyRecord record = record(RecordStatus.DRAFT);

        RecordService.DraftResult result = recordService.saveDraft(1L, "2026-10-21", false, emptyContent());

        assertThat(result.status()).isEqualTo(RecordStatus.DRAFT);
        assertThat(record.getStatus()).isEqualTo(RecordStatus.DRAFT);
    }

    @Test
    void RC12_완료된_기록을_수동_저장해도_COMPLETED를_유지한다() {
        DailyRecord record = record(RecordStatus.NOT_STARTED);
        record.complete(NOW.minusHours(1));

        RecordService.DraftResult result = recordService.saveDraft(1L, "2026-10-21", true, emptyContent());

        assertThat(result.status()).isEqualTo(RecordStatus.COMPLETED);
    }

    @Test
    void RC10_답변을_저장하고_빠진_질문은_비운다() {
        DailyRecord record = record(RecordStatus.DRAFT);
        RecordQuestion q1 = question(501L, 1, null);
        RecordQuestion q2 = question(502L, 2, "예전 답");
        RecordQuestion q3 = question(503L, 3, null);
        given(questionRepository.findAllByRecordIdOrderByOrderAsc(record.getId())).willReturn(List.of(q1, q2, q3));

        recordService.saveDraft(1L, "2026-10-21", true, new RecordService.RecordContent(List.of(), List.of(
                new RecordService.AnswerInput(501L, "새 답", false),
                new RecordService.AnswerInput(503L, "  ", true)), null));

        assertThat(q1.getAnswer()).isEqualTo("새 답");
        assertThat(q2.getAnswer()).isNull();
        assertThat(q2.isSkipped()).isFalse();
        assertThat(q3.getAnswer()).isNull();
        assertThat(q3.isSkipped()).isTrue();
    }

    @Test
    void RC12_그날_기록에_없는_질문에_답하면_QUESTION_NOT_FOUND이고_메모도_저장하지_않는다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recordService.saveDraft(1L, "2026-10-21", true, new RecordService.RecordContent(
                List.of(new RecordService.MemoInput(10L, "메모")),
                List.of(new RecordService.AnswerInput(999L, "답", false)), null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.QUESTION_NOT_FOUND));
        verify(taskService, never()).replaceMemosOn(anyLong(), any(), any());
    }

    @Test
    void RC12_같은_할_일이나_질문을_두_번_보내면_400() {
        assertBadRequest(() -> recordService.saveDraft(1L, "2026-10-21", true, new RecordService.RecordContent(
                List.of(new RecordService.MemoInput(10L, "a"), new RecordService.MemoInput(10L, "b")), List.of(), null)));
        assertBadRequest(() -> recordService.saveDraft(1L, "2026-10-21", true, new RecordService.RecordContent(
                List.of(), List.of(new RecordService.AnswerInput(1L, "a", false), new RecordService.AnswerInput(1L, "b", false)), null)));
    }

    // ---- 하루 회고 (6.3, 6.4, 6.7) ----

    @Test
    void RC12_회고만_있어도_수동_저장하면_DRAFT_기록을_만들고_회고를_저장한다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(recordRepository.save(any(DailyRecord.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(reflectionRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());

        RecordService.DraftResult result = recordService.saveDraft(1L, "2026-10-21", true, reflection("오늘 배운 점"));

        assertThat(result.status()).isEqualTo(RecordStatus.DRAFT);
        ArgumentCaptor<DailyReflection> captor = ArgumentCaptor.forClass(DailyReflection.class);
        verify(reflectionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getDate()).isEqualTo(DATE);
        assertThat(captor.getValue().getContent()).isEqualTo("오늘 배운 점");
    }

    @Test
    void RC12_회고_자동저장은_저장만_하고_기록_상태를_만들지_않는다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(reflectionRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());

        RecordService.DraftResult result = recordService.saveDraft(1L, "2026-10-21", false, reflection("자동저장 회고"));

        assertThat(result.status()).isEqualTo(RecordStatus.NOT_STARTED);
        verify(reflectionRepository).save(any(DailyReflection.class));
        verify(recordRepository, never()).save(any());
    }

    @Test
    void RC12_이미_저장된_회고는_덮어쓴다() {
        DailyReflection saved = new DailyReflection(1L, DATE, "예전 회고");
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(reflectionRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.of(saved));

        recordService.saveDraft(1L, "2026-10-21", false, reflection("새 회고"));

        assertThat(saved.getContent()).isEqualTo("새 회고");
        verify(reflectionRepository, never()).save(any());
        verify(reflectionRepository, never()).delete(any());
    }

    @Test
    void RC12_회고를_비우거나_생략하면_저장된_회고를_지운다() {
        DailyReflection saved = new DailyReflection(1L, DATE, "예전 회고");
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(reflectionRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.of(saved));

        recordService.saveDraft(1L, "2026-10-21", false, reflection("   "));

        verify(reflectionRepository).delete(saved);
    }

    @Test
    void RC12_저장된_회고가_없고_비어_있으면_아무것도_만들지_않는다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(reflectionRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());

        RecordService.DraftResult result = recordService.saveDraft(1L, "2026-10-21", true, reflection(null));

        assertThat(result.status()).isEqualTo(RecordStatus.NOT_STARTED);
        verify(reflectionRepository, never()).save(any());
        verify(reflectionRepository, never()).delete(any());
        verify(recordRepository, never()).save(any());
    }

    @Test
    void RC13_완료할_때_보낸_회고도_저장한다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(recordRepository.save(any(DailyRecord.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(reflectionRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(userService.updateLevel(anyLong(), anyLong())).willReturn(2);

        recordService.complete(1L, "2026-10-21", reflection("퇴근 회고"));

        verify(reflectionRepository).save(any(DailyReflection.class));
    }

    @Test
    void RC12_메모가_잘못되면_회고도_저장하지_않는다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new CustomException(ErrorCode.TASK_NOT_FOUND))
                .when(taskService).replaceMemosOn(anyLong(), any(), any());

        assertThatThrownBy(() -> recordService.saveDraft(1L, "2026-10-21", true, new RecordService.RecordContent(
                List.of(new RecordService.MemoInput(99L, "메모")), List.of(), "회고")))
                .isInstanceOf(CustomException.class);
        verify(reflectionRepository, never()).save(any());
    }

    @Test
    void RC12_회고_조회는_저장된_내용을_돌려주고_없으면_null이다() {
        given(reflectionRepository.findByUserIdAndDate(1L, DATE))
                .willReturn(Optional.of(new DailyReflection(1L, DATE, "회고")));
        given(reflectionRepository.findByUserIdAndDate(1L, DATE.plusDays(1))).willReturn(Optional.empty());

        assertThat(recordService.getReflection(1L, "2026-10-21").content()).isEqualTo("회고");
        assertThat(recordService.getReflection(1L, "2026-10-22").content()).isNull();
        assertBadRequest(() -> recordService.getReflection(1L, "2026/10/21"));
    }

    // ---- 6.4 완료 ----

    @Test
    void RC13_빈_기록도_완료되고_처음_완료되면_누적_일수로_레벨을_계산한다() {
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.empty());
        given(recordRepository.save(any(DailyRecord.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(recordRepository.countByUserIdAndStatus(1L, RecordStatus.COMPLETED)).willReturn(3L);
        given(userService.updateLevel(1L, 3L)).willReturn(3);

        RecordService.CompleteResult result = recordService.complete(1L, "2026-10-21", emptyContent());

        assertThat(result.status()).isEqualTo(RecordStatus.COMPLETED);
        assertThat(result.completedAt()).isEqualTo(NOW);
        assertThat(result.level()).isEqualTo(3);
    }

    @Test
    void RC13_이미_완료된_날을_다시_완료하면_완료_시각과_레벨을_유지한다() {
        DailyRecord record = record(RecordStatus.NOT_STARTED);
        record.complete(LocalDateTime.of(2026, 10, 21, 17, 0));
        given(userService.getLevel(1L)).willReturn(2);

        RecordService.CompleteResult result = recordService.complete(1L, "2026-10-21", emptyContent());

        assertThat(result.completedAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 17, 0));
        assertThat(result.level()).isEqualTo(2);
        verify(userService, never()).updateLevel(anyLong(), anyLong());
    }

    private DailyRecord record(RecordStatus status) {
        DailyRecord record = new DailyRecord(1L, DATE, status);
        ReflectionTestUtils.setField(record, "id", 100L);
        given(recordRepository.findByUserIdAndDate(1L, DATE)).willReturn(Optional.of(record));
        return record;
    }

    private RecordQuestion question(Long id, int order, String answer) {
        RecordQuestion question = new RecordQuestion(100L, order, "질문" + order);
        ReflectionTestUtils.setField(question, "id", id);
        question.changeAnswer(answer, false);
        return question;
    }

    private RecordService.RecordContent memo(Long taskId, String memo) {
        return new RecordService.RecordContent(List.of(new RecordService.MemoInput(taskId, memo)), List.of(), null);
    }

    private RecordService.RecordContent reflection(String reflection) {
        return new RecordService.RecordContent(null, null, reflection);
    }

    private RecordService.RecordContent emptyContent() {
        return new RecordService.RecordContent(null, null, null);
    }

    private void assertBadRequest(ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }
}
