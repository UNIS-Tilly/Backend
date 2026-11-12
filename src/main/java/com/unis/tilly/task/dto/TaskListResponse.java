package com.unis.tilly.task.dto;

import com.unis.tilly.task.service.TaskService;

import java.util.List;

// 5.1, HM-02, HM-03, RC-02 (D-105: 개수는 별도 필드, "2/5" 표기는 프론트)
public record TaskListResponse(String date, int completedCount, int totalCount,
                               List<TaskItemResponse> completed, List<TaskItemResponse> incomplete) {

    public static TaskListResponse from(TaskService.DailyTasks dailyTasks) {
        List<TaskItemResponse> completed = dailyTasks.completed().stream()
                .map(TaskItemResponse::completedOn)
                .toList();
        List<TaskItemResponse> incomplete = dailyTasks.incomplete().stream()
                .map(task -> TaskItemResponse.incompleteOn(task, dailyTasks.day()))
                .toList();
        return new TaskListResponse(dailyTasks.day().toString(), completed.size(),
                completed.size() + incomplete.size(), completed, incomplete);
    }
}
