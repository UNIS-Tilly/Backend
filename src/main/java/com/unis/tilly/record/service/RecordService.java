package com.unis.tilly.record.service;

import com.unis.tilly.ai.dto.AiCurrentJob;
import com.unis.tilly.ai.dto.AiTaskInput;
import com.unis.tilly.ai.dto.QuestionGenerationInput;
import com.unis.tilly.ai.dto.WeeklyInsightInput;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.insight.service.InsightLockService;
import com.unis.tilly.record.entity.DailyRecord;
import com.unis.tilly.record.entity.DailyReflection;
import com.unis.tilly.record.entity.RecordQuestion;
import com.unis.tilly.record.enums.RecordStatus;
import com.unis.tilly.record.repository.DailyRecordRepository;
import com.unis.tilly.record.repository.DailyReflectionRepository;
import com.unis.tilly.record.repository.RecordQuestionRepository;
import com.unis.tilly.task.service.TaskService;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.JobGroup;
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

// 6.1~6.4, 6.7 (D-95, D-106~D-108, 하루 회고 복원 2026-11-24). 할 일 메모는 TaskService, 성장 레벨은 UserService를 통해서만 다룬다.
@Service
@RequiredArgsConstructor
public class RecordService {

    static final int MAX_QUESTIONS = 3;

    private final DailyRecordRepository recordRepository;
    private final RecordQuestionRepository questionRepository;
    private final DailyReflectionRepository reflectionRepository;
    private final TaskService taskService;
    private final UserService userService;
    private final InsightLockService insightLockService;
    private final Clock clock;

    // 6.1 HM-10, HM-11
    public Summary getSummary(Long userId, String dateText) {
        LocalDate date = parseDate(dateText);
        Optional<DailyRecord> record = recordRepository.findByUserIdAndDate(userId, date);
        return new Summary(date,
                record.map(DailyRecord::getStatus).orElse(RecordStatus.NOT_STARTED),
                isLocked(userId, date),
                taskService.countMemosOn(userId, date),
                reflectionRepository.existsByUserIdAndDate(userId, date),
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

    // 6.7 하루 회고 조회. 저장된 회고가 없으면 content = null
    public DailyReflectionView getReflection(Long userId, String dateText) {
        LocalDate date = parseDate(dateText);
        return new DailyReflectionView(date, reflectionRepository.findByUserIdAndDate(userId, date)
                .map(DailyReflection::getContent)
                .orElse(null));
    }

    // 6.5 질문 받기, 트랜잭션 1 (AI 호출 전): 입력을 먼저 저장(manual:false 규칙, D-46·D-95)하고
    // 이미 질문이 있으면 그대로, 없으면 AI 입력을 만든다. 완료한 할 일도 회고도 없으면 422 RECORD_EMPTY (D-127).
    // AI 호출은 DB 트랜잭션 밖에서 하므로 여기서 끝낸다. 호출이 실패해도 저장한 입력은 남는다.
    @Transactional
    public QuestionPreparation prepareQuestions(Long userId, String dateText, RecordContent content) {
        LocalDate date = parseDate(dateText);
        checkNotLocked(userId, date);

        Optional<DailyRecord> found = recordRepository.findByUserIdAndDate(userId, date);
        applyContent(userId, date, found, content);

        List<RecordQuestion> existing = questionsOf(found);
        if (!existing.isEmpty()) {
            return new QuestionPreparation(date, existing, null);
        }
        QuestionGenerationInput input = buildQuestionInput(userId, date)
                .orElseThrow(() -> new CustomException(ErrorCode.RECORD_EMPTY));
        return new QuestionPreparation(date, List.of(), input);
    }

    // 6.6 자동 질문, AI 호출 전: 건너뛸 조건이면 빈 값 (D-138)
    // 이미 질문이 있는 날, 잠긴 날, 이미 COMPLETED인 날, 완료한 할 일도 회고도 없는 날
    @Transactional(readOnly = true)
    public Optional<QuestionGenerationInput> prepareAutoQuestions(Long userId, LocalDate date) {
        if (isLocked(userId, date)) {
            return Optional.empty();
        }
        Optional<DailyRecord> found = recordRepository.findByUserIdAndDate(userId, date);
        if (found.map(r -> r.getStatus() == RecordStatus.COMPLETED).orElse(false)
                || !questionsOf(found).isEmpty()) {
            return Optional.empty();
        }
        return buildQuestionInput(userId, date);
    }

    // 질문 저장, 트랜잭션 2 (AI 호출 후): 기록 행이 없으면 NOT_STARTED로 만들고(상태는 올리지 않음)
    // 그 사이 질문이 생겼으면 새로 만들지 않고 그것을 돌려준다 (D-47). 질문은 최대 3개, 순서는 1부터
    @Transactional
    public List<RecordQuestion> storeQuestions(Long userId, LocalDate date, List<String> texts) {
        Optional<DailyRecord> found = recordRepository.findByUserIdAndDate(userId, date);
        List<RecordQuestion> existing = questionsOf(found);
        if (!existing.isEmpty()) {
            return existing;
        }
        DailyRecord record = found.orElseGet(() ->
                recordRepository.save(new DailyRecord(userId, date, RecordStatus.NOT_STARTED)));
        List<RecordQuestion> saved = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(texts.size(), MAX_QUESTIONS); i++) {
            saved.add(questionRepository.save(new RecordQuestion(record.getId(), i + 1, texts.get(i))));
        }
        return saved;
    }

    // 동시 요청으로 저장이 겹쳤을 때 이미 저장된 질문을 다시 읽는다
    @Transactional(readOnly = true)
    public List<RecordQuestion> findQuestions(Long userId, LocalDate date) {
        return questionsOf(recordRepository.findByUserIdAndDate(userId, date));
    }

    // 9.2 D-139: 그날 퇴근 완료(COMPLETED)했는지
    public boolean isCompleted(Long userId, LocalDate date) {
        return recordRepository.findByUserIdAndDate(userId, date)
                .map(record -> record.getStatus() == RecordStatus.COMPLETED)
                .orElse(false);
    }

    // 9.2 인사이트 입력: 기간 안에서 퇴근 완료한 날의 회고·답변한 질문 (COMPLETED 기록만 반영, API_SPEC 8장 4번).
    // 회고는 공백뿐이면 없는 것으로, 답변은 비어 있거나 건너뛴 질문을 뺀다
    @Transactional(readOnly = true)
    public List<CompletedDay> getCompletedDays(Long userId, LocalDate from, LocalDate to) {
        List<DailyRecord> records = recordRepository.findAllByUserIdAndDateBetweenAndStatusIn(
                userId, from, to, java.util.Set.of(RecordStatus.COMPLETED));
        if (records.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, String> reflections = new HashMap<>();
        for (DailyReflection reflection : reflectionRepository.findAllByUserIdAndDateBetween(userId, from, to)) {
            if (hasText(reflection.getContent())) {
                reflections.put(reflection.getDate(), reflection.getContent());
            }
        }
        Map<Long, List<RecordQuestion>> questionsByRecord = questionRepository
                .findAllByRecordIdInOrderByRecordIdAscOrderAsc(records.stream().map(DailyRecord::getId).toList())
                .stream().collect(Collectors.groupingBy(RecordQuestion::getRecordId));

        return records.stream()
                .sorted(java.util.Comparator.comparing(DailyRecord::getDate))
                .map(record -> new CompletedDay(record.getDate(), reflections.get(record.getDate()),
                        questionsByRecord.getOrDefault(record.getId(), List.of()).stream()
                                .filter(q -> hasText(q.getAnswer()))
                                .map(q -> new AnsweredQuestion(q.getText(), q.getAnswer()))
                                .toList()))
                .toList();
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
                ? userService.updateLevel(userId, recordRepository.countByUserIdAndStatus(userId, RecordStatus.COMPLETED))
                : userService.getLevel(userId);

        return new CompleteResult(date, record.getStatus(), record.getCompletedAt(), level);
    }

    private List<RecordQuestion> questionsOf(Optional<DailyRecord> record) {
        return record.map(r -> questionRepository.findAllByRecordIdOrderByOrderAsc(r.getId())).orElse(List.of());
    }

    // AI 입력(D-126): 그날 할 일(제목·완료 여부·메모)과 하루 회고, 재직 직군, 희망 직군·선택 역량.
    // 완료한 할 일이 하나도 없고 회고도 없으면 만들 수 없다 (D-127 변경 제안: 미완료 할 일만 있는 날은 질문할 대상이 없다)
    private Optional<QuestionGenerationInput> buildQuestionInput(Long userId, LocalDate date) {
        List<AiTaskInput> tasks = taskService.getTasksOn(userId, date).stream()
                .map(task -> new AiTaskInput(task.getTitle(), task.isCompleted(), task.getMemo()))
                .toList();
        String reflection = reflectionRepository.findByUserIdAndDate(userId, date)
                .map(DailyReflection::getContent)
                .orElse(null);
        if (tasks.stream().noneMatch(AiTaskInput::completed) && reflection == null) {
            return Optional.empty();
        }
        UserService.UserProfile profile = userService.getProfile(userId);
        return Optional.of(new QuestionGenerationInput(date, tasks, reflection, currentJob(profile.user()),
                WeeklyInsightInput.Profile.of(profile.user().getTargetJobGroup(), profile.competencies())));
    }

    // 재직 직군·세부 직군 (질문을 그 업무에 맞추기 위한 맥락, 재직 직군이 없으면 null)
    private AiCurrentJob currentJob(User user) {
        JobGroup group = user.getCurrentJobGroup();
        return AiCurrentJob.of(group == null ? null : group.getLabel(), user.getCurrentJobDetail());
    }

    // 메모·답변·회고 전체 교체. 실패하면 트랜잭션 전체가 롤백된다. 저장할 내용이 하나라도 있으면 true
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

        // 메모: 그날 추가한 할 일 범위에서 전체 교체 (TaskService가 범위 검증)
        taskService.replaceMemosOn(userId, date, memos);

        for (RecordQuestion question : questions) {
            AnswerInput input = answers.get(question.getId());
            if (input == null) {
                question.clearAnswer();
            } else {
                question.changeAnswer(blankToNull(input.answer()), input.skipped());
            }
        }

        String reflection = blankToNull(content.reflection());
        replaceReflection(userId, date, reflection);

        return memos.values().stream().anyMatch(this::hasText)
                || answers.values().stream().anyMatch(a -> hasText(a.answer()))
                || reflection != null;
    }

    // 회고 전체 교체: 내용이 있으면 저장(있으면 덮어씀), 비어 있으면 저장된 회고를 지운다
    private void replaceReflection(Long userId, LocalDate date, String reflection) {
        Optional<DailyReflection> existing = reflectionRepository.findByUserIdAndDate(userId, date);
        if (reflection == null) {
            existing.ifPresent(reflectionRepository::delete);
        } else if (existing.isPresent()) {
            existing.get().changeContent(reflection);
        } else {
            reflectionRepository.save(new DailyReflection(userId, date, reflection));
        }
    }

    // 인사이트가 GENERATING·PUBLISHED인 주의 기록·회고는 잠긴다 (API_SPEC 1.5, D-130, D-137)
    boolean isLocked(Long userId, LocalDate date) {
        return insightLockService.isLocked(userId, date);
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

    // 임시저장·완료 공통 입력 (null 목록은 빈 목록, null·공백 회고는 "회고 없음")
    public record RecordContent(List<MemoInput> memos, List<AnswerInput> answers, String reflection) {
        public RecordContent {
            memos = memos == null ? List.of() : memos;
            answers = answers == null ? List.of() : answers;
        }
    }

    public record Summary(LocalDate date, RecordStatus status, boolean locked, int memoCount,
                          boolean reflectionWritten, LocalDateTime completedAt) {
    }

    public record DailyQuestions(LocalDate date, List<RecordQuestion> questions) {
    }

    // 질문 받기 준비 결과: existing이 비어 있지 않으면 그대로 돌려주고, 아니면 input으로 AI를 호출한다
    public record QuestionPreparation(LocalDate date, List<RecordQuestion> existing, QuestionGenerationInput input) {
    }

    public record AnsweredQuestion(String question, String answer) {
    }

    // 퇴근 완료한 날의 인사이트 입력용 기록 (회고는 없으면 null)
    public record CompletedDay(LocalDate date, String reflection, List<AnsweredQuestion> answers) {
    }

    public record DailyReflectionView(LocalDate date, String content) {
    }

    public record DraftResult(LocalDate date, RecordStatus status, LocalDateTime savedAt) {
    }

    public record CompleteResult(LocalDate date, RecordStatus status, LocalDateTime completedAt, int level) {
    }
}
