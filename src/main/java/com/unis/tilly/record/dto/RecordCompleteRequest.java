package com.unis.tilly.record.dto;

import com.unis.tilly.record.service.RecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

// 6.4 POST /records/{date}/complete — 임시저장과 같은 본문에서 manual만 없음 (reflection 포함)
public record RecordCompleteRequest(
        List<@Valid TaskMemoRequest> taskMemos,
        List<@Valid AnswerRequest> answers,
        @Size(max = 2000, message = "하루 회고는 2,000자 이하여야 합니다.") String reflection) {

    public RecordService.RecordContent toContent() {
        return RecordContents.of(taskMemos, answers, reflection);
    }
}
