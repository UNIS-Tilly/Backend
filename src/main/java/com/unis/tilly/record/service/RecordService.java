package com.unis.tilly.record.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.record.entity.DailyRecord;
import com.unis.tilly.record.entity.RecordQuestion;
import com.unis.tilly.record.enums.RecordStatus;
import com.unis.tilly.record.repository.DailyRecordRepository;
import com.unis.tilly.record.repository.RecordQuestionRepository;
import com.unis.tilly.task.service.TaskService;
import com.unis.tilly.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// 6.1~6.4 (D-95, D-106~D-108). 할 일 메모는 TaskService, 성장 레벨은 UserService를 통해서만 다룬다.
@Service
@RequiredArgsConstructor
public class RecordService {

    private final DailyRecordRepository recordRepository;
    private final RecordQuestionRepository questionRepository;
    private final TaskService taskService;
    private final UserService userService;
    private final Clock clock;

    // 6.1 HM-10, HM-11
    public Summary getSummary(Long userId, String dateText) {
        LocalDate date = parseDate(dateText);
        Optional<DailyRecord> record = recordRepository.findByUserIdAndDate(userId, date);
        return new Summary(date,
                record.map(DailyRecord::getStatus).orElse(RecordStatus.NOT_STARTED),
                isLocked(userId, date),
                taskService.countMemosOn(userId, date),
                record.map(DailyRecord::getCompletedAt).orElse(null));
    }

    // 6.2 RC-05, RC-09, AR-04
    public DailyQuestions getQuestions(Long userId, String dateText) {
        LocalDate date = parseDate(dateText);
        List<RecordQuestion> questions = recordRepository.findByUserIdAndDate(userId, date)
                .map(record -> questionRepository.findAllByRecordIdOrderByOrderAsc(record.getId()))
                .orElse(List.of());
        return new DailyQuestions(date, questions);
    }

    // 6.3 RC-12 임시저장 (D-95)
    @Transactional
    public DraftResult saveDraft(Long userId, String dateText, boolean manual, RecordContent content) {
        LocalDate date = parseDate(dateText);
        checkNotLocked(userId, date);

        Optional<DailyRecord> found = recordRepository.findByUserIdAndDate(userId, date);
        boolean hasContent = applyContent(userId, date, found, content);

        DailyRecord record = found.orElse(null);
        if (record == null) {
            // 저장된 적 없는 날: 수동 저장이면서 내용이 있을 때만 기록을 만든다 (빈 내용이면 행 생성 안 함)
            if (manual && hasContent) {
                record = recordRepository.save(new DailyRecord(userId, date, RecordStatus.DRAFT));
            }
        } else if (manual) {
            record.markDraft();
        }

        RecordStatus status = record == null ? RecordStatus.NOT_STARTED : record.getStatus();
        return new DraftResult(date, status, now());
    }

    // 6.4 RC-13 기록 완료(퇴근). 빈 기록도 완료 가능, 재호출 시 최초 완료 시각 유지
    @Transactional
    public CompleteResult complete(Long userId, String dateText, RecordContent content) {
        LocalDate date = parseDate(dateText);
        checkNotLocked(userId, date);

        Optional<DailyRecord> found = recordRepository.findByUserIdAndDate(userId, date);
        applyContent(userId, date, found, content);

        DailyRecord record = found.orElseGet(() ->
                recordRepository.save(new DailyRecord(userId, date, RecordStatus.NOT_STARTED)));
        boolean firstCompletion = record.complete(now());

        // D-101: 날짜가 처음 COMPLETED가 될 때만 누적 일수로 레벨을 다시 계산한다
        int level = firstCompletion
                ? userService.raiseLevel(userId, recordRepository.countByUserIdAndStatus(userId, RecordStatus.COMPLETED))
                : userService.getLevel(userId);

        return new CompleteResult(date, record.getStatus(), record.getCompletedAt(), level);
    }

    // 메모·답변 전체 교체. 실패하면 트랜잭션 전체가 롤백된다. 저장할 내용이 하나라도 있으면 true
    private boolean applyContent(Long userId, LocalDate date, Optional<DailyRecord> record, RecordContent content) {
        Map<Long, String> memos = toMemoMap(content.memos());
        Map<Long, AnswerInput> answers = toAnswerMap(content.answers());

        // 답변: 그날 기록의 질문만 허용. 빠진 질문은 비운다 (사용자 결정 2026-11-12)
        List<RecordQuestion> questions = record
                .map(r -> questionRepository.findAllByRecordIdOrderByOrderAsc(r.getId()))
                .orElse(List.of());
        Map<Long, RecordQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(RecordQuestion::getId, Function.identity()));
        if (!questionById.keySet().containsAll(answers.keySet())) {
            throw new CustomException(ErrorCode.QUESTION_NOT_FOUND);
        }

        // 메모: 그날 보이는 할 일 범위에서 전체 교체 (TaskService가 범위 검증)
        taskService.replaceMemosOn(userId, date, memos);

        for (RecordQuestion question : questions) {
            AnswerInput input = answers.get(question.getId());
            if (input == null) {
                question.clearAnswer();
            } else {
                question.changeAnswer(blankToNull(input.answer()), input.skipped());
            }
        }

        return memos.values().stream().anyMatch(this::hasText)
                || answers.values().stream().anyMatch(a -> hasText(a.answer()));
    }

    // 인사이트 발행 여부에 따른 잠금 (API_SPEC 1.5). insight 도메인이 아직 없어 항상 false.
    // insight 단위에서 insight 서비스의 잠금 조회로 교체한다 (GENERATING 중 잠금은 NOTICE-023).
    boolean isLocked(Long userId, LocalDate date) {
        return false;
    }

    private void checkNotLocked(Long userId, LocalDate date) {
        if (isLocked(userId, date)) {
            throw new CustomException(ErrorCode.RECORD_LOCKED);
        }
    }

    private Map<Long, String> toMemoMap(List<MemoInput> memos) {
        Map<Long, String> map = new HashMap<>();
        for (MemoInput memo : memos) {
            if (memo.taskId() == null || map.containsKey(memo.taskId())) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "할 일 메모의 taskId가 비어 있거나 중복되었습니다.");
            }
            map.put(memo.taskId(), memo.memo());
        }
        return map;
    }

    private Map<Long, AnswerInput> toAnswerMap(List<AnswerInput> answers) {
        Map<Long, AnswerInput> map = new HashMap<>();
        for (AnswerInput answer : answers) {
            if (answer.questionId() == null || map.containsKey(answer.questionId())) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "답변의 questionId가 비어 있거나 중복되었습니다.");
            }
            map.put(answer.questionId(), answer);
        }
        return map;
    }

    // 응답 형식(예: 2026-10-21T18:05:00)에 맞춰 초 단위로 기록한다
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private LocalDate parseDate(String dateText) {
        try {
            return LocalDate.parse(dateText);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)");
        }
    }

    public record MemoInput(Long taskId, String memo) {
    }

    public record AnswerInput(Long questionId, String answer, boolean skipped) {
    }

    // 임시저장·완료 공통 입력 (null 목록은 빈 목록)
    public record RecordContent(List<MemoInput> memos, List<AnswerInput> answers) {
        public RecordContent {
            memos = memos == null ? List.of() : memos;
            answers = answers == null ? List.of() : answers;
        }
    }

    public record Summary(LocalDate date, RecordStatus status, boolean locked, int memoCount,
                          LocalDateTime completedAt) {
    }

    public record DailyQuestions(LocalDate date, List<RecordQuestion> questions) {
    }

    public record DraftResult(LocalDate date, RecordStatus status, LocalDateTime savedAt) {
    }

    public record CompleteResult(LocalDate date, RecordStatus status, LocalDateTime completedAt, int level) {
    }
}
