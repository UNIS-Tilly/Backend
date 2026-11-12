package com.unis.tilly.record.dto;

import com.unis.tilly.record.service.RecordService;

import java.util.List;

// 요청 DTO → 서비스 입력 변환 (임시저장·완료 공통)
final class RecordContents {

    private RecordContents() {
    }

    static RecordService.RecordContent of(List<TaskMemoRequest> taskMemos, List<AnswerRequest> answers) {
        List<RecordService.MemoInput> memos = taskMemos == null ? List.of() : taskMemos.stream()
                .map(m -> new RecordService.MemoInput(m.taskId(), m.memo()))
                .toList();
        List<RecordService.AnswerInput> answerInputs = answers == null ? List.of() : answers.stream()
                .map(a -> new RecordService.AnswerInput(a.questionId(), a.answer(), Boolean.TRUE.equals(a.skipped())))
                .toList();
        return new RecordService.RecordContent(memos, answerInputs);
    }
}
