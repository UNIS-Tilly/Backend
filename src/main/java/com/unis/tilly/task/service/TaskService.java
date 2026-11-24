package com.unis.tilly.task.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.insight.service.InsightLockService;
import com.unis.tilly.task.dto.TaskUpdateRequest;
import com.unis.tilly.task.entity.Task;
import com.unis.tilly.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 5.1~5.5, HM-02~08, RC-03 (D-102~D-106). 2026-11-24 변경: 할 일은 배정일 하루에만 노출(이월 없음),
// 마감일은 "오늘 기준 남은 일수" 표시용이고 노출에는 영향이 없다. 배정일은 필수(생략 시 오늘).
// 기록 잠금(API_SPEC 1.5, D-130, D-137): 할 일의 배정일이 속한 주의 인사이트가 GENERATING·PUBLISHED이면
// 수정·삭제·완료 토글·추가·배정일 이동이 409 RECORD_LOCKED. 배정일을 옮길 때는 옮기기 전·후 날짜 모두 검사한다.
@Service
@RequiredArgsConstructor
public class TaskService {

    // 배정일 하루에 둘 수 있는 할 일(완료 + 미완료) 최대 개수 (사용자 결정 2026-11-12)
    static final int DAILY_LIMIT = 20;
    private static final int TITLE_MAX_LENGTH = 20;
    // 마감 일시 입력: 시간 필수, 초는 생략 가능(받으면 분 단위로 버림). 오프셋 없이 Asia/Seoul 기준
    private static final DateTimeFormatter DUE_DATE_INPUT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm[:ss]").withResolverStyle(ResolverStyle.STRICT);

    private final TaskRepository taskRepository;
    private final InsightLockService insightLockService;
    private final Clock clock;

    // HM-02, HM-03: date를 생략하면 오늘. 그날 추가한 할 일만 돌려주고 완료 여부로 나눈다
    public DailyTasks getTasks(Long userId, String dateText) {
        LocalDate day = dateText == null ? today() : parseDate(dateText);
        List<Task> tasks = getTasksOn(userId, day);
        List<Task> completed = tasks.stream().filter(Task::isCompleted).toList();
        List<Task> incomplete = tasks.stream().filter(task -> !task.isCompleted()).toList();
        return new DailyTasks(day, today(), completed, incomplete);
    }

    // 다른 도메인(insight)용: 기간(배정일) 안의 할 일 전체
    public List<Task> getTasksBetween(Long userId, LocalDate from, LocalDate to) {
        return taskRepository.findAllByUserIdAndDateBetweenOrderByDateAscIdAsc(userId, from, to);
    }

    // 다른 도메인(record)용: 그날(배정일) 추가한 할 일 전체 (완료 + 미완료, 5.1 규칙)
    public List<Task> getTasksOn(Long userId, LocalDate day) {
        return taskRepository.findAllByUserIdAndDateOrderByIdAsc(userId, day);
    }

    // 6.1 memoCount: 그날 할 일 중 메모가 있는 개수
    public int countMemosOn(Long userId, LocalDate day) {
        return (int) getTasksOn(userId, day).stream().filter(task -> task.getMemo() != null).count();
    }

    // 6.3, 6.4 메모 전체 교체 (D-20, Q-12 ⑤): 범위는 그날 추가한 할 일.
    // memos에 없는 그날 할 일의 메모는 지우고, 다른 날 할 일의 메모는 건드리지 않는다.
    // 그날 할 일이 아닌(다른 날짜·다른 사용자 포함) ID가 오면 404 TASK_NOT_FOUND
    @Transactional
    public void replaceMemosOn(Long userId, LocalDate day, Map<Long, String> memos) {
        List<Task> tasks = getTasksOn(userId, day);
        Set<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
        if (!taskIds.containsAll(memos.keySet())) {
            throw new CustomException(ErrorCode.TASK_NOT_FOUND);
        }
        for (Task task : tasks) {
            String memo = memos.get(task.getId());
            task.changeMemo(memo == null || memo.isBlank() ? null : memo);
        }
    }

    // HM-05
    @Transactional
    public Task createTask(Long userId, String rawTitle, String dateText, String dueDateText) {
        String title = validateTitle(rawTitle);
        // 배정일을 생략하면 오늘
        LocalDate date = dateText == null ? today() : parseDate(dateText);
        LocalDateTime dueDate = parseNullableDueDate(dueDateText);
        checkNotLocked(userId, date);

        // 그 배정일의 목록(완료 포함)이 이미 가득 찼으면 거절한다.
        if (taskRepository.countByUserIdAndDate(userId, date) >= DAILY_LIMIT) {
            throw new CustomException(ErrorCode.TASK_LIMIT_EXCEEDED);
        }

        return taskRepository.save(new Task(userId, title, date, dueDate));
    }

    // HM-04, HM-07, RC-03
    @Transactional
    public Task updateTask(Long userId, Long taskId, TaskUpdateRequest request) {
        Task task = findOwnedTask(userId, taskId);
        checkNotLocked(userId, task.getDate());

        if (request.title() != null) {
            task.changeTitle(validateTitle(request.title()));
        }
        if (request.dueDateSent()) {
            task.changeDueDate(parseNullableDueDate(request.dueDate()));
        }
        if (request.completed() != null) {
            if (request.completed()) {
                task.complete(today());
            } else {
                task.uncomplete();
            }
        }
        if (request.dateSent()) {
            // 배정일은 필수라 해제(null)할 수 없다
            if (request.date() == null) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "배정일은 비울 수 없습니다.");
            }
            LocalDate newDate = parseDate(request.date());
            if (!newDate.equals(task.getDate())) {
                checkNotLocked(userId, newDate);
                // 옮긴 날의 목록(완료 포함)이 가득 찼으면 거절한다.
                if (taskRepository.countByUserIdAndDate(userId, newDate) >= DAILY_LIMIT) {
                    throw new CustomException(ErrorCode.TASK_LIMIT_EXCEEDED);
                }
                task.changeDate(newDate);
            }
        }
        return task;
    }

    // HM-08: 메모는 할 일의 컬럼이라 함께 삭제된다.
    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        Task task = findOwnedTask(userId, taskId);
        checkNotLocked(userId, task.getDate());
        taskRepository.delete(task);
    }

    private void checkNotLocked(Long userId, LocalDate date) {
        if (insightLockService.isLocked(userId, date)) {
            throw new CustomException(ErrorCode.RECORD_LOCKED);
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private Task findOwnedTask(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
    }

    private LocalDate parseDate(String dateText) {
        try {
            return LocalDate.parse(dateText);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)");
        }
    }

    private LocalDateTime parseNullableDueDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DUE_DATE_INPUT).truncatedTo(ChronoUnit.MINUTES);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "마감 일시 형식이 올바르지 않습니다. (YYYY-MM-DDTHH:mm)");
        }
    }

    private String validateTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty() || title.length() > TITLE_MAX_LENGTH) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "할 일 제목은 1~20자여야 합니다.");
        }
        return title;
    }

    // 조회일(day)에 추가한 완료 목록·미완료 목록. today는 마감 남은 일수 계산 기준(서버 오늘)
    public record DailyTasks(LocalDate day, LocalDate today, List<Task> completed, List<Task> incomplete) {
    }
}
