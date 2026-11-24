package com.unis.tilly.record.dto;

import com.unis.tilly.record.service.RecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// 6.3 PUT /records/{date}/draft — 현재 입력 전체 (전체 교체, D-20). manual: 버튼(true) / 자동저장(false) (D-95)
// reflection: 하루 회고(2,000자 이하). 비우거나 생략하면 저장된 회고가 지워진다 (전체 교체)
public record RecordDraftRequest(
        @NotNull(message = "manual 값이 필요합니다.") Boolean manual,
        List<@Valid TaskMemoRequest> taskMemos,
        List<@Valid AnswerRequest> answers,
        @Size(max = 2000, message = "하루 회고는 2,000자 이하여야 합니다.") String reflection) {

    public RecordService.RecordContent toContent() {
        return RecordContents.of(taskMemos, answers, reflection);
    }
}
