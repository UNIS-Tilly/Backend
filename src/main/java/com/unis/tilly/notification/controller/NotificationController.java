package com.unis.tilly.notification.controller;

import com.unis.tilly.global.security.AuthenticatedUser;
import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import com.unis.tilly.notification.dto.NotificationResponse;
import com.unis.tilly.notification.dto.UnreadCountResponse;
import com.unis.tilly.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 13. 알림 (헤더 종 아이콘, D-152)
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 13.1
    @GetMapping
    public ApiResponse<NotificationResponse> getList(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.success(SuccessMessage.OK.message(), notificationService.getList(user.userId(), page, size));
    }

    // 13.2
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(SuccessMessage.OK.message(), notificationService.getUnreadCount(user.userId()));
    }

    // 13.3
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<UnreadCountResponse> markRead(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable Long notificationId) {
        return ApiResponse.success(SuccessMessage.OK.message(), notificationService.markRead(user.userId(), notificationId));
    }

    // 13.4
    @PostMapping("/read-all")
    public ApiResponse<UnreadCountResponse> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(SuccessMessage.OK.message(), notificationService.markAllRead(user.userId()));
    }
}
