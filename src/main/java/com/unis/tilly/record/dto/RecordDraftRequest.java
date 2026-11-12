package com.unis.tilly.record.dto;

import com.unis.tilly.record.service.RecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// 6.3 PUT /records/{date}/draft — 현재 입력 전체 (전체 교체, D-20). manual: 버튼(true) / 자동저장(false) (D-95)
public record RecordDraftRequest(
        @NotNull(message = "manual 값이 필요합니다.") Boolean manual,
        List<@Valid TaskMemoRequest> taskMemos,
        List<@Valid AnswerRequest> answers) {

    public RecordService.RecordContent toContent() {
        return RecordContents.of(taskMemos, answers);
    }
}
