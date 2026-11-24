package com.unis.tilly.record.dto;

import com.unis.tilly.record.service.RecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

// 6.5 POST /records/{date}/questions — 임시저장(manual 제외)과 같은 본문: 현재 입력 전체 (먼저 manual:false로 저장, D-46·D-95)
public record RecordQuestionRequest(
        List<@Valid TaskMemoRequest> taskMemos,
        List<@Valid AnswerRequest> answers,
        @Size(max = 2000, message = "하루 회고는 2,000자 이하여야 합니다.") String reflection) {

    public RecordService.RecordContent toContent() {
        return RecordContents.of(taskMemos, answers, reflection);
    }
}
