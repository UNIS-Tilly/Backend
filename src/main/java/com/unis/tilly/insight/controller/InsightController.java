package com.unis.tilly.insight.controller;

import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import com.unis.tilly.global.security.AuthenticatedUser;
import com.unis.tilly.insight.dto.InsightLatestResponse;
import com.unis.tilly.insight.dto.InsightListResponse;
import com.unis.tilly.insight.dto.InsightRequest;
import com.unis.tilly.insight.dto.InsightRequestResponse;
import com.unis.tilly.insight.dto.InsightWeekResponse;
import com.unis.tilly.insight.service.InsightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights/weekly")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    // 9.1 IN-01, IN-02, IN-04~08: date가 속한 주의 인사이트 (생략하면 오늘)
    @GetMapping
    public ApiResponse<InsightWeekResponse> getWeek(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestParam(required = false) String date) {
        return ApiResponse.success(SuccessMessage.OK.message(), insightService.getWeek(user.userId(), date));
    }

    // 9.2 IN-03: 분석 요청 (202, 프론트가 GET으로 폴링)
    @PostMapping
    public ResponseEntity<ApiResponse<InsightRequestResponse>> request(@AuthenticationPrincipal AuthenticatedUser user,
                                                                       @RequestBody @Valid InsightRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                SuccessMessage.INSIGHT_ANALYSIS_REQUESTED.message(),
                insightService.request(user.userId(), request.date())));
    }

    // 9.4 HM-12: 홈 카드 — 가장 최근에 발행된 인사이트
    @GetMapping("/latest")
    public ApiResponse<InsightLatestResponse> getLatest(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(SuccessMessage.OK.message(), insightService.getLatest(user.userId()));
    }

    // 9.5: 인사이트 목록 (발행된 주만, 최신순/오래된순, 페이지)
    @GetMapping("/list")
    public ApiResponse<InsightListResponse> getList(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestParam(required = false) String sort,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.success(SuccessMessage.OK.message(), insightService.getList(user.userId(), sort, page, size));
    }
}
