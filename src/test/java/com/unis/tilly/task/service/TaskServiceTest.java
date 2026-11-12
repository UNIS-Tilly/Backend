package com.unis.tilly.task.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 5.1~5.5, HM-02~08, RC-03 (D-102~D-106)
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Mock
    private TaskRepository taskRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(SEOUL).toInstant(), SEOUL);
        taskService = new TaskService(taskRepository, clock);
    }

    // ---- 목록 (HM-02, HM-03) ----

    @Test
    void HM02_날짜를_생략하면_오늘로_조회한다() {
        given(taskRepository.findAllByUserIdAndCompletedDateOrderByIdAsc(1L, TODAY)).willReturn(List.of());
        given(taskRepository.findAllIncompleteVisibleOn(1L, TODAY, TODAY.plusDays(1).atStartOfDay())).willReturn(List.of());

        TaskService.DailyTasks result = taskService.getTasks(1L, null);

        assertThat(result.day()).isEqualTo(TODAY);
    }

    @Test
    void HM02_조회_날짜_형식이_잘못되면_400() {
        assertBadRequest(() -> taskService.getTasks(1L, "2026/10/21"));
    }

    // ---- 추가 (HM-05) ----

    @Test
    void HM05_배정일과_마감일을_넣어_추가한다() {
        stubVisibleCount(LocalDate.of(2026, 10, 22), 0, 0);
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        Task task = taskService.createTask(1L, "  고객 문의 정리  ", "2026-10-22", "2026-10-25");

        assertThat(task.getTitle()).isEqualTo("고객 문의 정리");
        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedDate()).isNull();
    }

    @Test
    void HM05_배정일과_마감일_없이_추가할_수_있다() {
        stubVisibleCount(TODAY, 0, 0);
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        Task task = taskService.createTask(1L, "제목", null, null);

        assertThat(task.getDate()).isNull();
        assertThat(task.getDueDate()).isNull();
    }

    @Test
    void HM05_마감일이_배정일보다_앞서도_저장한다() {
        stubVisibleCount(TODAY, 0, 0);
        given(taskRepository.save(any(Task.class))).willAnswer(invocation -> invocation.getArgument(0));

        Task task = taskService.createTask(1L, "제목", "2026-10-21", "2026-10-19");

        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void HM05_배정일의_목록이_완료_포함_20개면_TASK_LIMIT_EXCEEDED() {
        stubVisibleCount(LocalDate.of(2026, 10, 22), 5, 15);

        assertThatThrownBy(() -> taskService.createTask(1L, "제목", "2026-10-22", null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void HM05_배정일이_없으면_오늘_목록으로_20개를_센다() {
        stubVisibleCount(TODAY, 0, 20);

        assertThatThrownBy(() -> taskService.createTask(1L, "제목", null, null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED));
    }

    @Test
    void HM05_19개면_추가된다() {
        stubVisibleCount(TODAY, 4, 15);
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
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 20));

        taskService.updateTask(1L, 10L, request(r -> r.setTitle("새제목")));

        assertThat(task.getTitle()).isEqualTo("새제목");
        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(task.isCompleted()).isFalse();
    }

    @Test
    void HM07_배정일과_마감일에_null을_보내면_해제된다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 20));
        stubVisibleCount(TODAY, 0, 3);

        taskService.updateTask(1L, 10L, request(r -> {
            r.setDate(null);
            r.setDueDate(null);
        }));

        assertThat(task.getDate()).isNull();
        assertThat(task.getDueDate()).isNull();
    }

    @Test
    void HM07_배정일을_옮길_날의_목록이_20개면_TASK_LIMIT_EXCEEDED() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 25), null);
        stubVisibleCount(LocalDate.of(2026, 10, 22), 10, 10);

        assertThatThrownBy(() -> taskService.updateTask(1L, 10L, request(r -> r.setDate("2026-10-22"))))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.TASK_LIMIT_EXCEEDED));
        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void HM07_이미_그날_보이던_할_일은_자기_자신을_빼고_센다() {
        // 10/18 배정 → 10/22에도 이월되어 보이는 중. 10/22로 옮겨도 그날 개수는 늘지 않는다.
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), null);
        stubVisibleCount(LocalDate.of(2026, 10, 22), 0, 20);

        taskService.updateTask(1L, 10L, request(r -> r.setDate("2026-10-22")));

        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    void HM07_완료된_할_일은_배정일을_옮겨도_개수를_검사하지_않는다() {
        Task task = ownedTask(10L, LocalDate.of(2026, 10, 18), null);
        task.complete(TODAY);

        taskService.updateTask(1L, 10L, request(r -> r.setDate("2026-10-22")));

        assertThat(task.getDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        verify(taskRepository, never()).countByUserIdAndCompletedDate(any(), any());
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

    private void stubVisibleCount(LocalDate day, long completed, long incomplete) {
        given(taskRepository.countByUserIdAndCompletedDate(1L, day)).willReturn(completed);
        given(taskRepository.countIncompleteVisibleOn(eq(1L), eq(day), eq(day.plusDays(1).atStartOfDay())))
                .willReturn(incomplete);
    }

    private Task ownedTask(Long id, LocalDate date, LocalDate dueDate) {
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
