package com.unis.tilly.task.dto;

import com.unis.tilly.task.entity.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 5.3, 5.4 응답 (현재 상태 그대로). dueDate는 "2026-10-23T18:00:00" (초까지, 오프셋 없음, 서울 시간)
public record TaskResponse(Long id, String title, String date, String dueDate,
                           boolean completed, String completedDate, String memo) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), format(task.getDate()), format(task.getDueDate()),
                task.isCompleted(), format(task.getCompletedDate()), task.getMemo());
    }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    static String format(LocalDate date) {
        return date == null ? null : date.toString();
    }

    // LocalDateTime.toString()은 초가 0이면 생략하므로 형식을 고정한다 (기록 API 시각 형식과 동일)
    static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME.format(dateTime);
    }
}
