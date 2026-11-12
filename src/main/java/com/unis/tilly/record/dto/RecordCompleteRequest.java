package com.unis.tilly.record.dto;

import com.unis.tilly.record.service.RecordService;
import jakarta.validation.Valid;

import java.util.List;

// 6.4 POST /records/{date}/complete — 임시저장과 같은 본문에서 manual만 없음
public record RecordCompleteRequest(
        List<@Valid TaskMemoRequest> taskMemos,
        List<@Valid AnswerRequest> answers) {

    public RecordService.RecordContent toContent() {
        return RecordContents.of(taskMemos, answers);
    }
}
