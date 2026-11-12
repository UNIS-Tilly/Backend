package com.unis.tilly.task.dto;

import com.unis.tilly.task.entity.Task;

import java.time.LocalDate;

// 5.3, 5.4 응답 (현재 상태 그대로)
public record TaskResponse(Long id, String title, String date, String dueDate,
                           boolean completed, String completedDate, String memo) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), format(task.getDate()), format(task.getDueDate()),
                task.isCompleted(), format(task.getCompletedDate()), task.getMemo());
    }

    static String format(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
