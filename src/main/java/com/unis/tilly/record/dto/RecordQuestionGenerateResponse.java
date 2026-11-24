package com.unis.tilly.record.dto;

import com.unis.tilly.record.service.RecordQuestionService;

import java.util.List;

// 6.5 질문 받기 응답: { questions: [...] } (항목 형식은 6.2와 같다)
public record RecordQuestionGenerateResponse(List<RecordQuestionsResponse.QuestionResponse> questions) {

    public static RecordQuestionGenerateResponse from(RecordQuestionService.QuestionResult result) {
        return new RecordQuestionGenerateResponse(result.questions().stream()
                .map(RecordQuestionsResponse.QuestionResponse::from)
                .toList());
    }
}
