package com.unis.tilly.task.dto;

import com.unis.tilly.task.entity.Task;

import java.time.LocalDate;

import static com.unis.tilly.task.dto.TaskResponse.format;

// 5.1 목록 항목. 조회일(day) 시점의 상태로 내려준다.
public record TaskItemResponse(Long id, String title, String date, String dueDate,
                               boolean completed, String completedDate, String memo, Integer overdueDays) {

    public static TaskItemResponse completedOn(Task task) {
        return new TaskItemResponse(task.getId(), task.getTitle(), format(task.getDate()), format(task.getDueDate()),
                true, format(task.getCompletedDate()), task.getMemo(), null);
    }

    // 과거 날짜 재구성: 조회일 뒤에 완료한 할 일도 그날엔 미완료였으므로 completed=false, completedDate=null
    public static TaskItemResponse incompleteOn(Task task, LocalDate day) {
        return new TaskItemResponse(task.getId(), task.getTitle(), format(task.getDate()), format(task.getDueDate()),
                false, null, task.getMemo(), task.overdueDaysOn(day));
    }
}
