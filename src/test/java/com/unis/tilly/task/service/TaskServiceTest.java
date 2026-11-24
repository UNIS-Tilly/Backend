package com.unis.tilly.task.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.insight.service.InsightLockService;
import com.unis.tilly.task.dto.TaskUpdateRequest;
import com.unis.tilly.task.entity.Task;
import com.unis.tilly.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 5.1~5.5, HM-02~08, RC-03 (D-102~D-106, 2026-11-24 변경: 그날 추가한 할 일만 노출·배정일 필수)
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private InsightLockService insightLockService;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(SEOUL).toInstant(), SEOUL);
        taskService = new TaskService(taskRepository, insightLockService, clock);
    }

    // ---- 목록 (HM-02, HM-03) ----

    @Test
    void HM02_날짜를_생략하면_오늘로_조회한다() {
        given(taskRepository.findAllByUserIdAndDateOrderByIdAsc(1L, TODAY)).willReturn(List.of());

        TaskService.DailyTasks result = taskService.getTasks(1L, null);

        assertThat(result.day()).isEqualTo(TODAY);
        assertThat(result.today()).isEqualTo(TODAY);
    }

    @Test
    void HM02_그날_추가한_할_일을_완료_여부로_나눈다() {
        Task done = visibleTask(10L, null);
        done.complete(TODAY);
        Task open = visibleTask(11L, null);
        LocalDate past = LocalDate.of(2026, 10, 18);
        given(taskRepository.findAllByUserIdAndDateOrderByIdAsc(1L, past)).willReturn(List.of(done, open));

        TaskService.DailyTasks result = taskService.getTasks(1L, "2026-10-18");

        assertThat(result.day()).isEqualTo(past);
        assertThat(result.today()).isEqualTo(TODAY);   // 마감 남은 일수는 조회일이 아니라 서버 오늘 기준
        assertThat(result.completed()).containsExactly(done);
        assertThat(result.incomplete()).containsExactly(open);
    }

    @Test
    void HM02_조회_날짜_형식이_잘못되면_400() {
        assertBadRequest(() -> taskService.getTasks(1L, "2026/10/21"));
    }

    // ---- 추가 (HM-05) ----

    @Test
    void HM05_배정일과_마감일을_넣어_추가한다() {
        stubCount(LocalDate.of(2026, 10, 22), 0);
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        Task task = taskService.createTask(1L, "  고객 문의 정리  ", "2026-10-22", "2026-10-25T18:30");

        assertThat(task.getTitle()).isEqualTo("고객 문의 정리");
        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(task.getDueDate()).isEqualTo(LocalDateTime.of(2026, 10, 25, 18, 30));
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedDate()).isNull();
    }

    @Test
    void HM05_배정일을_생략하면_오늘로_저장하고_마감일_없이_추가할_수_있다() {
        stubCount(TODAY, 0);
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        Task task = taskService.createTask(1L, "제목", null, null);

        assertThat(task.getDate()).isEqualTo(TODAY);
        assertThat(task.getDueDate()).isNull();
    }

    @Test
    void HM05_마감일이_배정일보다_앞서도_저장한다() {
        stubCount(TODAY, 0);
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        Task task = taskService.createTask(1L, "제목", "2026-10-21", "2026-10-19T09:00");

        assertThat(task.getDueDate()).isEqualTo(LocalDateTime.of(2026, 10, 19, 9, 0));
    }

    @Test
    void HM05_마감_일시에_초를_보내면_분_단위로_버려_저장한다() {
        stubCount(TODAY, 0);
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        Task task = taskService.createTask(1L, "제목", null, "2026-10-23T18:00:45");

        assertThat(task.getDueDate()).isEqualTo(LocalDateTime.of(2026, 10, 23, 18, 0));
    }

    @Test
    void HM05_마감은_시간까지_필수이고_날짜만_보내면_400() {
        assertBadRequest(() -> taskService.createTask(1L, "제목", null, "2026-10-23"));
        assertBadRequest(() -> taskService.createTask(1L, "제목", null, "2026-10-23T24:00"));
        assertBadRequest(() -> taskService.createTask(1L, "제목", null, "2026-02-30T18:00"));
        assertBadRequest(() -> taskService.createTask(1L, "제목", null, "2026-10-23T18:00+09:00"));
        assertBadRequest(() -> taskService.createTask(1L, "제목", null, "2026-10-23 18:00"));
    }

    @Test
    void HM07_마감을_날짜만으로_수정하면_400이고_값은_그대로다() {
        Task task = ownedTask(10L, TODAY, LocalDateTime.of(2026, 10, 23, 18, 0));

        assertBadRequest(() -> taskService.updateTask(1L, 10L, request(r -> r.setDueDate("2026-10-24"))));
        assertThat(task.getDueDate()).isEqualTo(LocalDateTime.of(2026, 10, 23, 18, 0));
    }

    @Test
    void HM05_배정일의_목록이_완료_포함_20개면_TASK_LIMIT_EXCEEDED() {
        stubCount(LocalDate.of(2026, 10, 22), 20);

        assertThatThrownBy(() -> taskService.createTask(1L, "제목", "2026-10-22", null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void HM05_배정일이_없으면_오늘_목록으로_20개를_센다() {
        stubCount(TODAY, 20);

        assertThatThrownBy(() -> taskService.createTask(1L, "제목", null, null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED));
    }

    @Test
    void HM05_19개면_추가된다() {
        stubCount(TODAY, 19);
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        Task task = taskService.createTask(1L, "제목", null, null);

        assertThat(task.getTitle()).isEqualTo("제목");
    }

    @Test
    void HM05_제목이_공백뿐이거나_20자를_넘으면_400() {
        assertBadRequest(() -> taskService.createTask(1L, "   ", null, null));
        assertBadRequest(() -> taskService.createTask(1L, "가".repeat(21), null, null));
    }

    @Test
    void HM05_배정일이나_마감일_형식이_잘못되면_400() {
        assertBadRequest(() -> taskService.createTask(1L, "제목", "2026/10/21", null));
        assertBadRequest(() -> taskService.createTask(1L, "제목", null, "10-21"));
    }

    // ---- 수정 / 완료 토글 (HM-04, HM-07, RC-03) ----

    @Test
    void HM04_완료하면_완료일이_오늘이_되고_해제하면_비워진다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), null);

        taskService.updateTask(1L, 10L, request(r -> r.setCompleted(true)));
        assertThat(task.isCompleted()).isTrue();
        assertThat(task.getCompletedDate()).isEqualTo(TODAY);

        taskService.updateTask(1L, 10L, request(r -> r.setCompleted(false)));
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedDate()).isNull();
    }

    @Test
    void HM07_필드를_생략하면_유지되고_제목만_바뀐다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), LocalDateTime.of(2026, 10, 20, 18, 0));

        taskService.updateTask(1L, 10L, request(r -> r.setTitle("새제목")));

        assertThat(task.getTitle()).isEqualTo("새제목");
        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(task.getDueDate()).isEqualTo(LocalDateTime.of(2026, 10, 20, 18, 0));
        assertThat(task.isCompleted()).isFalse();
    }

    @Test
    void HM07_마감일에_null을_보내면_해제되고_배정일은_유지된다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), LocalDateTime.of(2026, 10, 20, 18, 0));

        taskService.updateTask(1L, 10L, request(r -> r.setDueDate(null)));

        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(task.getDueDate()).isNull();
    }

    @Test
    void HM07_배정일은_필수라_null을_보내면_400이고_값은_그대로다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), null);

        assertBadRequest(() -> taskService.updateTask(1L, 10L, request(r -> r.setDate(null))));
        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 18));
    }

    @Test
    void HM07_배정일을_옮길_날의_목록이_20개면_TASK_LIMIT_EXCEEDED() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 25), null);
        stubCount(LocalDate.of(2026, 10, 22), 20);

        assertThatThrownBy(() -> taskService.updateTask(1L, 10L, request(r -> r.setDate("2026-10-22"))))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED));
        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void HM07_배정일을_옮기면_옮긴_날로_바뀐다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 25), null);
        stubCount(LocalDate.of(2026, 10, 22), 19);

        taskService.updateTask(1L, 10L, request(r -> r.setDate("2026-10-22")));

        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    void HM07_같은_배정일을_보내면_개수를_검사하지_않는다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), null);

        taskService.updateTask(1L, 10L, request(r -> r.setDate("2026-10-18")));

        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        verify(taskRepository, never()).countByUserIdAndDate(any(), any());
    }

    @Test
    void HM07_완료된_할_일도_옮긴_날_목록에_들어가므로_개수를_검사한다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), null);
        task.complete(TODAY);
        stubCount(LocalDate.of(2026, 10, 22), 20);

        assertThatThrownBy(() -> taskService.updateTask(1L, 10L, request(r -> r.setDate("2026-10-22"))))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED));
    }

    @Test
    void HM07_수정할_제목이_비어있으면_400() {
        ownedTask(10L, TODAY, null);

        assertBadRequest(() -> taskService.updateTask(1L, 10L, request(r -> r.setTitle(" "))));
    }

    @Test
    void HM07_다른_사용자의_할_일을_수정하면_TASK_NOT_FOUND() {
        given(taskRepository.findByIdAndUserId(10L, 2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.updateTask(2L, 10L, request(r -> r.setTitle("새제목"))))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }

    // ---- 잠금 (API_SPEC 1.5, D-130, D-137) ----

    @Test
    void IN03_배정일이_잠긴_주면_할_일을_추가할_수_없다() {
        given(insightLockService.isLocked(1L, LocalDate.of(2026, 10, 22))).willReturn(true);

        assertLocked(() -> taskService.createTask(1L, "제목", "2026-10-22", null));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void IN03_잠긴_주의_할_일은_수정_완료_삭제할_수_없다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 20), null);
        given(insightLockService.isLocked(1L, LocalDate.of(2026, 10, 20))).willReturn(true);

        assertLocked(() -> taskService.updateTask(1L, 10L, request(r -> r.setTitle("새제목"))));
        assertLocked(() -> taskService.updateTask(1L, 10L, request(r -> r.setCompleted(true))));
        assertLocked(() -> taskService.deleteTask(1L, 10L));
        assertThat(task.getTitle()).isEqualTo("원래제목");
        assertThat(task.isCompleted()).isFalse();
        verify(taskRepository, never()).delete(any());
    }

    @Test
    void IN03_잠긴_주로_배정일을_옮길_수_없다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 25), null);
        given(insightLockService.isLocked(1L, LocalDate.of(2026, 10, 25))).willReturn(false);   // 원래 배정일의 주는 잠기지 않음
        given(insightLockService.isLocked(1L, LocalDate.of(2026, 10, 20))).willReturn(true);    // 옮길 주가 잠김

        assertLocked(() -> taskService.updateTask(1L, 10L, request(r -> r.setDate("2026-10-20"))));
        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    private void assertLocked(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.RECORD_LOCKED));
    }

    // ---- 삭제 (HM-08) ----

    @Test
    void HM08_할_일을_삭제한다() {
        Task task = ownedTask(10L, TODAY, null);

        taskService.deleteTask(1L, 10L);

        verify(taskRepository).delete(task);
    }

    @Test
    void HM08_다른_사용자의_할_일을_삭제하면_TASK_NOT_FOUND() {
        given(taskRepository.findByIdAndUserId(10L, 2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteTask(2L, 10L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        verify(taskRepository, never()).delete(any());
    }

    // ---- 메모 (D-106, record 도메인에서 호출) ----

    @Test
    void RC12_그날_추가한_할_일의_메모를_전체_교체한다() {
        Task withMemo = visibleTask(10L, "예전 메모");
        Task other = visibleTask(11L, "지워질 메모");
        Task blank = visibleTask(12L, null);
        given(taskRepository.findAllByUserIdAndDateOrderByIdAsc(1L, TODAY)).willReturn(List.of(withMemo, other, blank));

        taskService.replaceMemosOn(1L, TODAY, java.util.Map.of(10L, "새 메모", 12L, "   "));

        assertThat(withMemo.getMemo()).isEqualTo("새 메모");
        assertThat(other.getMemo()).isNull();   // 보냈던 목록에 없으면 삭제
        assertThat(blank.getMemo()).isNull();   // 공백은 메모 없음
    }

    @Test
    void RC12_그날_할_일이_아닌_ID의_메모는_TASK_NOT_FOUND이고_아무것도_바꾸지_않는다() {
        Task visible = visibleTask(10L, "그대로");
        given(taskRepository.findAllByUserIdAndDateOrderByIdAsc(1L, TODAY)).willReturn(List.of(visible));

        assertThatThrownBy(() -> taskService.replaceMemosOn(1L, TODAY, java.util.Map.of(99L, "메모")))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
        assertThat(visible.getMemo()).isEqualTo("그대로");
    }

    @Test
    void HM10_메모가_있는_할_일_수를_센다() {
        given(taskRepository.findAllByUserIdAndDateOrderByIdAsc(1L, TODAY))
                .willReturn(List.of(visibleTask(10L, "메모"), visibleTask(11L, null), visibleTask(12L, "메모")));

        assertThat(taskService.countMemosOn(1L, TODAY)).isEqualTo(2);
    }

    private Task visibleTask(Long id, String memo) {
        Task task = new Task(1L, "제목", TODAY, null);
        ReflectionTestUtils.setField(task, "id", id);
        task.changeMemo(memo);
        return task;
    }

    private void stubCount(LocalDate day, long count) {
        given(taskRepository.countByUserIdAndDate(1L, day)).willReturn(count);
    }

    private Task ownedTask(Long id, LocalDate date, LocalDateTime dueDate) {
        Task task = new Task(1L, "원래제목", date, dueDate);
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "createdAt", LocalDateTime.of(2026, 10, 15, 9, 0));
        given(taskRepository.findByIdAndUserId(id, 1L)).willReturn(Optional.of(task));
        return task;
    }

    private TaskUpdateRequest request(java.util.function.Consumer<TaskUpdateRequest> customizer) {
        TaskUpdateRequest request = new TaskUpdateRequest();
        customizer.accept(request);
        return request;
    }

    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }
}
