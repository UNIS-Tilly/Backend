package com.unis.tilly.task.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.task.dto.TaskUpdateRequest;
import com.unis.tilly.task.entity.Task;
import com.unis.tilly.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

// 5.1~5.5, HM-02~08, RC-03 (D-102~D-106)
// 기록 잠금(API_SPEC 1.5, 409 RECORD_LOCKED)은 insight 도메인이 아직 없어 이번 단위에는 적용하지 않는다.
// insight 도메인 구현 시 insight 서비스의 잠금 조회로 추가한다:
// 배정일 또는 완료일이 발행된 주에 속하면 잠금, 배정일 없는 미완료는 잠그지 않음 (Q-12 ① 승인).
@Service
@RequiredArgsConstructor
public class TaskService {

    // 하루에 보이는 할 일(완료 + 미완료) 최대 개수 (사용자 결정 2026-11-12)
    static final int DAILY_VISIBLE_LIMIT = 20;
    private static final int TITLE_MAX_LENGTH = 20;

    private final TaskRepository taskRepository;
    private final Clock clock;

    // HM-02, HM-03: date를 생략하면 오늘
    public DailyTasks getTasks(Long userId, String dateText) {
        LocalDate day = dateText == null ? today() : parseDate(dateText);
        List<Task> completed = taskRepository.findAllByUserIdAndCompletedDateOrderByIdAsc(userId, day);
        List<Task> incomplete = taskRepository.findAllIncompleteVisibleOn(userId, day, day.plusDays(1).atStartOfDay());
        return new DailyTasks(day, completed, incomplete);
    }

    // HM-05
    @Transactional
    public Task createTask(Long userId, String rawTitle, String dateText, String dueDateText) {
        String title = validateTitle(rawTitle);
        LocalDate date = parseNullableDate(dateText);
        LocalDate dueDate = parseNullableDate(dueDateText);

        // 새 할 일이 처음 보이는 날(배정일, 없으면 오늘)의 목록이 이미 가득 찼으면 거절한다.
        if (countVisibleOn(userId, firstVisibleDay(date)) >= DAILY_VISIBLE_LIMIT) {
            throw new CustomException(ErrorCode.TASK_LIMIT_EXCEEDED);
        }

        return taskRepository.save(new Task(userId, title, date, dueDate));
    }

    // HM-04, HM-07, RC-03
    @Transactional
    public Task updateTask(Long userId, Long taskId, TaskUpdateRequest request) {
        Task task = findOwnedTask(userId, taskId);

        if (request.title() != null) {
            task.changeTitle(validateTitle(request.title()));
        }
        if (request.dueDateSent()) {
            task.changeDueDate(parseNullableDate(request.dueDate()));
        }
        if (request.completed() != null) {
            if (request.completed()) {
                task.complete(today());
            } else {
                task.uncomplete();
            }
        }
        if (request.dateSent()) {
            LocalDate newDate = parseNullableDate(request.date());
            if (!Objects.equals(newDate, task.getDate())) {
                checkLimitForDateChange(userId, task, newDate);
                task.changeDate(newDate);
            }
        }
        return task;
    }

    // HM-08: 메모는 할 일의 컬럼이라 함께 삭제된다.
    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        Task task = findOwnedTask(userId, taskId);
        taskRepository.delete(task);
    }

    // 배정일을 옮기면 옮긴 날의 목록에 새로 보이게 되므로 같은 제한을 적용한다.
    // 완료된 할 일은 완료일 기준으로만 보이므로 배정일을 바꿔도 목록 개수가 변하지 않는다.
    private void checkLimitForDateChange(Long userId, Task task, LocalDate newDate) {
        if (task.isCompleted()) {
            return;
        }
        LocalDate targetDay = firstVisibleDay(newDate);
        long visible = countVisibleOn(userId, targetDay);
        if (task.isVisibleOn(targetDay)) {
            visible--; // 이미 그날 보이던 할 일이면 자기 자신은 빼고 센다
        }
        if (visible >= DAILY_VISIBLE_LIMIT) {
            throw new CustomException(ErrorCode.TASK_LIMIT_EXCEEDED);
        }
    }

    private long countVisibleOn(Long userId, LocalDate day) {
        return taskRepository.countByUserIdAndCompletedDate(userId, day)
                + taskRepository.countIncompleteVisibleOn(userId, day, day.plusDays(1).atStartOfDay());
    }

    private LocalDate firstVisibleDay(LocalDate date) {
        return date != null ? date : today();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private Task findOwnedTask(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
    }

    private LocalDate parseNullableDate(String dateText) {
        return dateText == null ? null : parseDate(dateText);
    }

    private LocalDate parseDate(String dateText) {
        try {
            return LocalDate.parse(dateText);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)");
        }
    }

    private String validateTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty() || title.length() > TITLE_MAX_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "할 일 제목은 1~20자여야 합니다.");
        }
        return title;
    }

    // 조회일(day) 기준 완료 목록·미완료 목록
    public record DailyTasks(LocalDate day, List<Task> completed, List<Task> incomplete) {
    }
}
