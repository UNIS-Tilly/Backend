package com.unis.tilly.record.dto;

import com.unis.tilly.record.entity.RecordQuestion;
import com.unis.tilly.record.service.RecordService;

import java.time.LocalDate;
import java.util.List;

// 6.2 하루 질문 (질문이 없으면 빈 배열)
public record RecordQuestionsResponse(LocalDate date, List<QuestionResponse> questions) {

    public static RecordQuestionsResponse from(RecordService.DailyQuestions dailyQuestions) {
        return new RecordQuestionsResponse(dailyQuestions.date(),
                dailyQuestions.questions().stream().map(QuestionResponse::from).toList());
    }

    public record QuestionResponse(Long id, int order, String text, String answer, boolean skipped) {

        static QuestionResponse from(RecordQuestion question) {
            return new QuestionResponse(question.getId(), question.getOrder(), question.getText(),
                    question.getAnswer(), question.isSkipped());
        }
    }
}
