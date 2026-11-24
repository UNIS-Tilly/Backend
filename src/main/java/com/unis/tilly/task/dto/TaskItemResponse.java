package com.unis.tilly.task.dto;

import com.unis.tilly.task.entity.Task;

import java.time.LocalDate;

import static com.unis.tilly.task.dto.TaskResponse.format;

// 5.1 목록 항목. dueInDays는 마감까지 남은 일수(오늘 기준, 마감 없거나 완료면 null)
public record TaskItemResponse(Long id, String title, String date, String dueDate,
                               boolean completed, String completedDate, String memo, Integer dueInDays) {

    public static TaskItemResponse from(Task task, LocalDate today) {
        return new TaskItemResponse(task.getId(), task.getTitle(), format(task.getDate()), format(task.getDueDate()),
                task.isCompleted(), format(task.getCompletedDate()), task.getMemo(), task.dueInDays(today));
    }
}
