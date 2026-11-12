package com.unis.tilly.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.unis.tilly.ai.dto.AiTaskInput;
import com.unis.tilly.ai.dto.WeeklyInsightInput;
import com.unis.tilly.ai.dto.WeeklyInsightResult;
import com.unis.tilly.ai.enums.AiEvidenceType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// AI 응답은 신뢰하지 않는 입력이다 (RULES 7.7.7). 스키마·개수·길이·근거·숫자를 서버에서 검증한다 (API_SPEC 8장).
// 실패하면 AiResponseInvalidException → 다시 생성. 메시지에 사용자 기록·AI 원문을 넣지 않는다.
@Component
public class AiResponseValidator {

    static final int MAX_QUESTIONS = 3;
    static final int MIN_ITEMS = 1;
    static final int MAX_ITEMS = 2;          // 강점·보완 각 1~2개 (#18)
    static final int ACTION_MAX_LENGTH = 20; // 추천 행동 = 다음 할 일 제목 (D-57)
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    // 6.5: 질문 1~3개, 빈 문장 불가
    public List<String> questions(JsonNode root) {
        JsonNode array = root.path("questions");
        if (!array.isArray() || array.size() < 1 || array.size() > MAX_QUESTIONS) {
            throw new AiResponseInvalidException("질문 개수가 1~3개가 아닙니다.");
        }
        List<String> questions = new ArrayList<>();
        for (JsonNode node : array) {
            questions.add(requireText(node, "질문 문장이 비어 있습니다."));
        }
        return questions;
    }

    // 9.2: 강점·보완 1~2개, 보완마다 추천 행동 1개(20자 이내), 모든 항목에 실제 존재하는 근거, 제목·소제목 숫자 = 서버 계산값
    public WeeklyInsightResult weeklyInsight(JsonNode root, WeeklyInsightInput input) {
        String title = requireText(root.path("title"), "제목이 비어 있습니다.");
        String subtitle = requireText(root.path("subtitle"), "소제목이 비어 있습니다.");
        checkNumbers(title + " " + subtitle, allowedNumbers(input));

        Map<LocalDate, WeeklyInsightInput.DayInput> days = input.days().stream()
                .collect(Collectors.toMap(WeeklyInsightInput.DayInput::date, Function.identity()));

        List<WeeklyInsightResult.Strength> strengths = new ArrayList<>();
        for (JsonNode node : items(root.path("strengths"), "강점")) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tag : node.path("tags")) {
                tags.add(requireText(tag, "태그가 비어 있습니다."));
            }
            strengths.add(new WeeklyInsightResult.Strength(
                    requireText(node.path("title"), "강점 제목이 비어 있습니다."),
                    requireText(node.path("description"), "강점 설명이 비어 있습니다."),
                    tags,
                    evidences(node.path("evidences"), days)));
        }

        List<WeeklyInsightResult.Improvement> improvements = new ArrayList<>();
        for (JsonNode node : items(root.path("improvements"), "보완")) {
            String action = requireText(node.path("action"), "추천 행동이 비어 있습니다.");
            if (action.length() > ACTION_MAX_LENGTH) {
                throw new AiResponseInvalidException("추천 행동이 20자를 넘습니다.");
            }
            improvements.add(new WeeklyInsightResult.Improvement(
                    requireText(node.path("title"), "보완 제목이 비어 있습니다."),
                    requireText(node.path("description"), "보완 설명이 비어 있습니다."),
                    evidences(node.path("evidences"), days),
                    action));
        }
        return new WeeklyInsightResult(title, subtitle, strengths, improvements);
    }

    private JsonNode items(JsonNode array, String name) {
        if (!array.isArray() || array.size() < MIN_ITEMS || array.size() > MAX_ITEMS) {
            throw new AiResponseInvalidException(name + " 개수가 1~2개가 아닙니다.");
        }
        return array;
    }

    // 근거는 1개 이상, 날짜가 그 주의 기록에 있고 종류에 맞는 내용이 실제로 있어야 한다 (D-60, IN-10)
    private List<WeeklyInsightResult.Evidence> evidences(JsonNode array,
                                                          Map<LocalDate, WeeklyInsightInput.DayInput> days) {
        if (!array.isArray() || array.isEmpty()) {
            throw new AiResponseInvalidException("근거 기록이 없습니다.");
        }
        List<WeeklyInsightResult.Evidence> evidences = new ArrayList<>();
        for (JsonNode node : array) {
            LocalDate date = parseDate(node.path("date").asText(""));
            AiEvidenceType type = parseType(node.path("type").asText(""));
            WeeklyInsightInput.DayInput day = days.get(date);
            if (day == null || !hasContent(day, type)) {
                throw new AiResponseInvalidException("근거 기록이 실제 기록에 없습니다.");
            }
            evidences.add(new WeeklyInsightResult.Evidence(date, type));
        }
        return evidences;
    }

    private boolean hasContent(WeeklyInsightInput.DayInput day, AiEvidenceType type) {
        return switch (type) {
            case TASK -> !day.tasks().isEmpty();
            case MEMO -> day.tasks().stream().map(AiTaskInput::memo).anyMatch(this::hasText);
            case QUESTION_ANSWER -> day.answers().stream()
                    .map(WeeklyInsightInput.AnswerInput::answer).anyMatch(this::hasText);
        };
    }

    // 제목·소제목에 나온 숫자는 모두 서버가 준 값(또는 그 차이)이어야 한다 (D-112)
    private void checkNumbers(String text, Set<Integer> allowed) {
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            int number;
            try {
                number = Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                throw new AiResponseInvalidException("제목·소제목의 숫자가 올바르지 않습니다.");
            }
            if (!allowed.contains(number)) {
                throw new AiResponseInvalidException("제목·소제목의 숫자가 서버 계산값과 다릅니다.");
            }
        }
    }

    static Set<Integer> allowedNumbers(WeeklyInsightInput input) {
        Set<Integer> allowed = new HashSet<>();
        WeeklyInsightInput.WeekStats now = input.stats();
        allowed.add(now.totalCount());
        allowed.add(now.completedCount());
        allowed.add(now.completionRate());
        allowed.add(input.days().size()); // 기록한(완료한) 날 수
        WeeklyInsightInput.WeekStats prev = input.previousWeekStats();
        if (prev != null) {
            allowed.add(prev.totalCount());
            allowed.add(prev.completedCount());
            allowed.add(prev.completionRate());
            allowed.add(Math.abs(now.totalCount() - prev.totalCount()));
            allowed.add(Math.abs(now.completedCount() - prev.completedCount()));
            int rateDiff = Math.abs(now.completionRate() - prev.completionRate());
            allowed.add(rateDiff); // 완수율 차이(%p)
            if (prev.completionRate() > 0) {
                allowed.add(Math.round(rateDiff * 100f / prev.completionRate())); // 완수율 증감률(%)
            }
        }
        return allowed;
    }

    private String requireText(JsonNode node, String reason) {
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new AiResponseInvalidException(reason);
        }
        return node.asText().strip();
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new AiResponseInvalidException("근거 날짜 형식이 올바르지 않습니다.");
        }
    }

    private AiEvidenceType parseType(String text) {
        try {
            return AiEvidenceType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new AiResponseInvalidException("근거 종류가 올바르지 않습니다.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
