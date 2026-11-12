package com.unis.tilly.record.controller;

import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import com.unis.tilly.global.security.AuthenticatedUser;
import com.unis.tilly.record.dto.RecordCompleteRequest;
import com.unis.tilly.record.dto.RecordCompleteResponse;
import com.unis.tilly.record.dto.RecordDraftRequest;
import com.unis.tilly.record.dto.RecordDraftResponse;
import com.unis.tilly.record.dto.RecordQuestionsResponse;
import com.unis.tilly.record.dto.RecordSummaryResponse;
import com.unis.tilly.record.service.RecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    // 6.1 HM-10, HM-11
    @GetMapping("/{date}/summary")
    public ApiResponse<RecordSummaryResponse> getSummary(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @PathVariable String date) {
        return ApiResponse.success(SuccessMessage.OK.message(),
                RecordSummaryResponse.from(recordService.getSummary(user.userId(), date)));
    }

    // 6.2 RC-05, RC-09, AR-04 (폴링 대상)
    @GetMapping("/{date}/questions")
    public ApiResponse<RecordQuestionsResponse> getQuestions(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PathVariable String date) {
        return ApiResponse.success(SuccessMessage.OK.message(),
                RecordQuestionsResponse.from(recordService.getQuestions(user.userId(), date)));
    }

    // 6.3 RC-12
    @PutMapping("/{date}/draft")
    public ApiResponse<RecordDraftResponse> saveDraft(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable String date,
                                                      @RequestBody @Valid RecordDraftRequest request) {
        RecordService.DraftResult result = recordService.saveDraft(
                user.userId(), date, request.manual(), request.toContent());
        return ApiResponse.success(SuccessMessage.RECORD_DRAFT_SAVED.message(), RecordDraftResponse.from(result));
    }

    // 6.4 RC-13
    @PostMapping("/{date}/complete")
    public ApiResponse<RecordCompleteResponse> complete(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @PathVariable String date,
                                                        @RequestBody @Valid RecordCompleteRequest request) {
        RecordService.CompleteResult result = recordService.complete(user.userId(), date, request.toContent());
        return ApiResponse.success(SuccessMessage.RECORD_COMPLETED.message(), RecordCompleteResponse.from(result));
    }
}
