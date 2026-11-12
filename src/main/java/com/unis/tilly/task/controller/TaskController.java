package com.unis.tilly.task.controller;

import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import com.unis.tilly.global.security.AuthenticatedUser;
import com.unis.tilly.task.dto.TaskCreateRequest;
import com.unis.tilly.task.dto.TaskListResponse;
import com.unis.tilly.task.dto.TaskResponse;
import com.unis.tilly.task.dto.TaskUpdateRequest;
import com.unis.tilly.task.entity.Task;
import com.unis.tilly.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // HM-02, HM-03, RC-02: date 생략 시 오늘
    @GetMapping
    public ApiResponse<TaskListResponse> getTasks(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @RequestParam(required = false) String date) {
        TaskService.DailyTasks dailyTasks = taskService.getTasks(user.userId(), date);
        return ApiResponse.success(SuccessMessage.OK.message(), TaskListResponse.from(dailyTasks));
    }

    // HM-05
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@AuthenticationPrincipal AuthenticatedUser user,
                                                                @RequestBody @Valid TaskCreateRequest request) {
        Task task = taskService.createTask(user.userId(), request.title(), request.date(), request.dueDate());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessMessage.TASK_CREATED.message(), TaskResponse.from(task)));
    }

    // HM-04, HM-07, RC-03
    @PatchMapping("/{taskId}")
    public ApiResponse<TaskResponse> updateTask(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable Long taskId,
                                                @RequestBody TaskUpdateRequest request) {
        Task task = taskService.updateTask(user.userId(), taskId, request);
        return ApiResponse.success(SuccessMessage.TASK_UPDATED.message(), TaskResponse.from(task));
    }

    // HM-08
    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> deleteTask(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable Long taskId) {
        taskService.deleteTask(user.userId(), taskId);
        return ApiResponse.success(SuccessMessage.TASK_DELETED.message());
    }
}
