package com.unis.tilly.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.ai.dto.AiTaskInput;
import com.unis.tilly.ai.dto.WeeklyInsightInput;
import com.unis.tilly.ai.dto.WeeklyInsightResult;
import com.unis.tilly.ai.enums.AiEvidenceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// AI 응답 검증 (API_SPEC 8장, D-57, D-60, D-112)
class AiResponseValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AiResponseValidator validator = new AiResponseValidator();

    // 이번 주 12개 중 9개(75%), 지난 주 10개 중 6개(60%) → 차이 15%p, 증감률 25%
    private final WeeklyInsightInput input = new WeeklyInsightInput(
            LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), "10월 3주차",
            new WeeklyInsightInput.WeekStats(12, 9), new WeeklyInsightInput.WeekStats(10, 6), false,
            List.of(
                    new WeeklyInsightInput.DayInput(LocalDate.of(2026, 10, 21),
                            List.of(new AiTaskInput("보고서", true, "구조를 바꿨다")), List.of()),
                    new WeeklyInsightInput.DayInput(LocalDate.of(2026, 10, 22),
                            List.of(new AiTaskInput("회의", false, null)),
                            List.of(new WeeklyInsightInput.AnswerInput("질문", "답변")))));

    // ---- 질문 (6.5) ----

    @Test
    void RC06_질문은_1에서_3개만_통과한다() {
        assertThat(validator.questions(json("{\"questions\":[\" 질문1 \",\"질문2\"]}"))).containsExactly("질문1", "질문2");
        assertInvalid(() -> validator.questions(json("{\"questions\":[]}")));
        assertInvalid(() -> validator.questions(json("{\"questions\":[\"1\",\"2\",\"3\",\"4\"]}")));
        assertInvalid(() -> validator.questions(json("{\"questions\":[\" \"]}")));
        assertInvalid(() -> validator.questions(json("{\"other\":1}")));
    }

    // ---- 주간 인사이트 (9.2) ----

    @Test
    void IN03_규칙을_지키면_결과로_변환한다() {
        WeeklyInsightResult result = validator.weeklyInsight(json(insight(
                "이번주는 12개 중 9개를 끝냈어요", "지난 주보다 완수율이 15%p 올랐어요",
                "{\"date\":\"2026-10-21\",\"type\":\"MEMO\"}", "회의 후 결정사항 한 줄 메모")), input);

        assertThat(result.strengths()).hasSize(1);
        assertThat(result.strengths().get(0).evidences().get(0).type()).isEqualTo(AiEvidenceType.MEMO);
        assertThat(result.improvements().get(0).action()).isEqualTo("회의 후 결정사항 한 줄 메모");
    }

    @Test
    void IN03_제목_숫자가_서버_계산값과_다르면_실패() {
        assertInvalid(() -> validator.weeklyInsight(json(insight(
                "이번주는 13개를 끝냈어요", "소제목", "{\"date\":\"2026-10-21\",\"type\":\"MEMO\"}", "행동")), input));
    }

    @Test
    void IN03_지난_주가_없으면_비교_숫자를_허용하지_않는다() {
        WeeklyInsightInput firstWeek = new WeeklyInsightInput(input.weekStart(), input.weekEnd(), input.weekLabel(),
                input.stats(), null, false, input.days());

        assertInvalid(() -> validator.weeklyInsight(json(insight(
                "완수율이 15%p 올랐어요", "소제목", "{\"date\":\"2026-10-21\",\"type\":\"MEMO\"}", "행동")), firstWeek));
    }

    @Test
    void IN09_추천_행동이_20자를_넘으면_실패() {
        assertInvalid(() -> validator.weeklyInsight(json(insight(
                "제목", "소제목", "{\"date\":\"2026-10-21\",\"type\":\"MEMO\"}", "가".repeat(21))), input));
    }

    @Test
    void IN10_근거가_실제_기록에_없으면_실패() {
        // 기록에 없는 날짜
        assertInvalid(() -> validator.weeklyInsight(json(insight(
                "제목", "소제목", "{\"date\":\"2026-10-23\",\"type\":\"TASK\"}", "행동")), input));
        // 그날 메모가 없는데 MEMO
        assertInvalid(() -> validator.weeklyInsight(json(insight(
                "제목", "소제목", "{\"date\":\"2026-10-22\",\"type\":\"MEMO\"}", "행동")), input));
        // 그날 답변이 없는데 QUESTION_ANSWER
        assertInvalid(() -> validator.weeklyInsight(json(insight(
                "제목", "소제목", "{\"date\":\"2026-10-21\",\"type\":\"QUESTION_ANSWER\"}", "행동")), input));
        // 알 수 없는 종류(회고 등)
        assertInvalid(() -> validator.weeklyInsight(json(insight(
                "제목", "소제목", "{\"date\":\"2026-10-21\",\"type\":\"REFLECTION\"}", "행동")), input));
    }

    @Test
    void IN05_강점이나_보완이_0개거나_3개면_실패() {
        assertInvalid(() -> validator.weeklyInsight(json("""
                {"title":"제목","subtitle":"소제목","strengths":[],"improvements":[]}"""), input));
    }

    @Test
    void 허용_숫자에는_서버가_준_값과_차이가_들어간다() {
        assertThat(AiResponseValidator.allowedNumbers(input)).contains(12, 9, 75, 10, 6, 60, 2, 3, 15, 25);
    }

    private String insight(String title, String subtitle, String evidence, String action) {
        return """
                {"title":"%s","subtitle":"%s",
                 "strengths":[{"title":"강점","description":"설명","tags":["태그"],"evidences":[%s]}],
                 "improvements":[{"title":"보완","description":"설명","evidences":[{"date":"2026-10-22","type":"TASK"}],"action":"%s"}]}
                """.formatted(title, subtitle, evidence, action);
    }

    private JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(AiResponseInvalidException.class);
    }
}
