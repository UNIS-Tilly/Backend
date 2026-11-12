package com.unis.tilly.record.controller;

import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import com.unis.tilly.global.security.AuthenticatedUser;
import com.unis.tilly.record.dto.ArchiveCalendarResponse;
import com.unis.tilly.record.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    // 7.1 AR-01, AR-02
    @GetMapping("/calendar")
    public ApiResponse<ArchiveCalendarResponse> getCalendar(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @RequestParam int year,
                                                            @RequestParam int month) {
        return ApiResponse.success(SuccessMessage.OK.message(),
                ArchiveCalendarResponse.from(archiveService.getCalendar(user.userId(), year, month)));
    }
}
