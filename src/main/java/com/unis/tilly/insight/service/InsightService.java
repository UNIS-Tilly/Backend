package com.unis.tilly.insight.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.insight.dto.InsightLatestResponse;
import com.unis.tilly.insight.dto.InsightListResponse;
import com.unis.tilly.insight.dto.InsightRequestResponse;
import com.unis.tilly.insight.dto.InsightWeekResponse;
import com.unis.tilly.insight.entity.InsightEvidence;
import com.unis.tilly.insight.entity.WeeklyInsight;
import com.unis.tilly.insight.enums.InsightStatus;
import com.unis.tilly.insight.repository.WeeklyInsightRepository;
import com.unis.tilly.record.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// 9장 주간 인사이트: 조회(9.1), 분석 요청(9.2), 홈 카드(9.4), 목록(9.5). 추천 행동 반영 API는 두지 않는다 (D-147).
// 이 클래스는 트랜잭션을 걸지 않는다: 저장은 InsightStore, AI 호출은 InsightGenerationService가 각각 짧게 한다.
@Service
@RequiredArgsConstructor
public class InsightService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;   // [제안] 한 번에 가져올 수 있는 최대 개수

    private final InsightStore store;
    private final InsightGenerationService generationService;
    private final InsightInputAssembler assembler;
    private final RecordService recordService;
    private final WeeklyInsightRepository insightRepository;
    private final InsightSnapshots snapshots;
    private final Clock clock;

    // 9.1: date가 속한 주(월~일). date를 생략하면 오늘
    public InsightWeekResponse getWeek(Long userId, String dateText) {
        LocalDate weekStart = WeekRules.weekStart(dateText == null ? today() : parseDate(dateText));
        Optional<WeeklyInsight> found = store.findExpiringStale(userId, weekStart);

        if (found.isPresent() && found.get().getStatus() == InsightStatus.PUBLISHED) {
            return week(weekStart, InsightStatus.PUBLISHED, null, report(found.get()));
        }
        if (found.isPresent() && found.get().getStatus() == InsightStatus.GENERATING) {
            return week(weekStart, InsightStatus.GENERATING, null, null);
        }
        InsightStatus status = found.isPresent() ? InsightStatus.FAILED : InsightStatus.NOT_PUBLISHED;
        return week(weekStart, status, eligibility(userId, weekStart), null);
    }

    // 9.2: 금요일(퇴근 후)·토요일·일요일에 이번 주를 분석 요청한다 (D-139). 비동기로 생성하고 곧바로 GENERATING을 돌려준다
    public InsightRequestResponse request(Long userId, String dateText) {
        LocalDate weekStart = WeekRules.weekStart(parseDate(dateText));
        if (!isRequestable(userId, weekStart)) {
            throw new CustomException(ErrorCode.INSIGHT_NOT_REQUESTABLE);
        }

        Optional<WeeklyInsight> existing = store.findExpiringStale(userId, weekStart);
        if (existing.isPresent() && existing.get().getStatus() == InsightStatus.PUBLISHED) {
            throw new CustomException(ErrorCode.INSIGHT_ALREADY_PUBLISHED);
        }
        if (existing.isPresent() && existing.get().getStatus() == InsightStatus.GENERATING) {
            return requested(existing.get());   // 이미 생성 중이면 기존 건을 그대로 돌려준다 (중복 AI 호출 방지)
        }

        InsightInputAssembler.WeekData data = assembler.collect(userId, weekStart);
        if (!data.eligible()) {
            throw new CustomException(ErrorCode.INSUFFICIENT_RECORDS);
        }

        InsightStore.Begun begun = begin(userId, weekStart);
        if (!begun.started()) {
            return requested(begun.insight());
        }
        Long insightId = begun.insight().getId();
        InsightInputAssembler.Assembled assembled;
        try {
            // 이 시점부터 그 주가 잠기므로 입력은 요청 때 한 번만 만든다 (발행 결과와 스냅샷이 같은 데이터에서 나온다)
            assembled = assembler.assemble(userId, data);
        } catch (RuntimeException e) {
            store.markFailed(insightId);
            throw e;
        }
        generationService.submit(insightId, assembled);
        return requested(begun.insight());
    }

    // 9.4: 가장 최근에 발행된 인사이트 (홈 카드)
    public InsightLatestResponse getLatest(Long userId) {
        return insightRepository.findFirstByUserIdAndStatusOrderByWeekStartDesc(userId, InsightStatus.PUBLISHED)
                .map(insight -> new InsightLatestResponse(insight.getId(), insight.getWeekStart(),
                        WeekRules.weekEnd(insight.getWeekStart()), WeekRules.label(insight.getWeekStart()),
                        InsightStatus.PUBLISHED, insight.getTitle(), insight.getSubtitle(),
                        store.loadDetails(insight.getId()).actions().stream()
                                .map(a -> new InsightLatestResponse.ActionText(a.getText()))
                                .toList()))
                .orElseGet(() -> new InsightLatestResponse(null, null, null, null,
                        InsightStatus.NOT_PUBLISHED, null, null, List.of()));
    }

    // 9.5: 발행된 주만, 최신순(기본)/오래된순, 페이지는 0부터
    public InsightListResponse getList(Long userId, String sort, Integer page, Integer size) {
        Sort.Direction direction = parseSort(sort);
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + "이어야 합니다.");
        }
        Page<WeeklyInsight> result = insightRepository.findAllByUserIdAndStatus(userId, InsightStatus.PUBLISHED,
                PageRequest.of(pageNumber, pageSize, Sort.by(direction, "weekStart")));
        return new InsightListResponse(
                result.getContent().stream()
                        .map(i -> new InsightListResponse.Item(i.getId(), i.getWeekStart(),
                                WeekRules.weekEnd(i.getWeekStart()), WeekRules.label(i.getWeekStart()),
                                i.getTitle(), i.getSubtitle()))
                        .toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext());
    }

    // D-139: 이번 주 안에서, 금요일(그날 기록이 COMPLETED가 된 뒤)·토요일·일요일에만 요청할 수 있다.
    // 지난 주·다음 주, 월~목은 요청할 수 없고 놓친 주는 요청할 수 없다
    boolean isRequestable(Long userId, LocalDate weekStart) {
        LocalDate today = today();
        if (!weekStart.equals(WeekRules.weekStart(today))) {
            return false;
        }
        DayOfWeek day = today.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
                || (day == DayOfWeek.FRIDAY && recordService.isCompleted(userId, today));
    }

    private InsightWeekResponse.Eligibility eligibility(Long userId, LocalDate weekStart) {
        InsightInputAssembler.WeekData data = assembler.collect(userId, weekStart);
        return new InsightWeekResponse.Eligibility(data.eligible(), isRequestable(userId, weekStart),
                data.recordedDays(), InsightInputAssembler.REQUIRED_DAYS);
    }

    // 첫 요청이 동시에 겹치면 유니크 제약((user_id, week_start))에 걸린다 → 먼저 만들어진 건을 기준으로 다시 판단한다
    private InsightStore.Begun begin(Long userId, LocalDate weekStart) {
        try {
            return store.begin(userId, weekStart);
        } catch (DataIntegrityViolationException e) {
            WeeklyInsight existing = store.find(userId, weekStart).orElseThrow(() -> e);
            if (existing.getStatus() == InsightStatus.PUBLISHED) {
                throw new CustomException(ErrorCode.INSIGHT_ALREADY_PUBLISHED);
            }
            return new InsightStore.Begun(existing, false);
        }
    }

    private InsightWeekResponse.Report report(WeeklyInsight insight) {
        InsightStore.Details details = store.loadDetails(insight.getId());
        Map<Long, List<InsightWeekResponse.Evidence>> strengthEvidences = evidences(details.evidences(), true);
        Map<Long, List<InsightWeekResponse.Evidence>> improvementEvidences = evidences(details.evidences(), false);

        return new InsightWeekResponse.Report(insight.getId(), insight.getGeneratedAt(),
                insight.isInsufficientContent() ? List.of("INSUFFICIENT_CONTENT") : List.of(),
                insight.getTitle(), insight.getSubtitle(),
                new InsightWeekResponse.Summary(insight.getRecordedDays(), insight.getMemoCount(),
                        insight.getReflectionCount(), insight.getAnswerCount()),
                snapshots.readAchievement(insight.getWeeklyAchievement()),
                snapshots.readFlow(insight.getWeeklyFlow()),
                details.strengths().stream()
                        .map(s -> new InsightWeekResponse.Strength(s.getId(), s.getTitle(), s.getDescription(),
                                s.getCompetencyCode(), snapshots.readTags(s.getTags()),
                                strengthEvidences.getOrDefault(s.getId(), List.of())))
                        .toList(),
                details.improvements().stream()
                        .map(m -> new InsightWeekResponse.Improvement(m.getId(), m.getTitle(), m.getDescription(),
                                m.getStarPart(), improvementEvidences.getOrDefault(m.getId(), List.of())))
                        .toList(),
                details.actions().stream()
                        .map(a -> new InsightWeekResponse.Action(a.getId(), a.getImprovementId(), a.getText()))
                        .toList());
    }

    private Map<Long, List<InsightWeekResponse.Evidence>> evidences(List<InsightEvidence> evidences, boolean strength) {
        return evidences.stream()
                .filter(e -> (strength ? e.getStrengthId() : e.getImprovementId()) != null)
                .collect(Collectors.groupingBy(
                        e -> strength ? e.getStrengthId() : e.getImprovementId(),
                        Collectors.mapping(e -> new InsightWeekResponse.Evidence(e.getDate(), e.getType()),
                                Collectors.toList())));
    }

    private InsightWeekResponse week(LocalDate weekStart, InsightStatus status,
                                     InsightWeekResponse.Eligibility eligibility, InsightWeekResponse.Report report) {
        return new InsightWeekResponse(weekStart, WeekRules.weekEnd(weekStart), WeekRules.label(weekStart),
                status, eligibility, report);
    }

    private InsightRequestResponse requested(WeeklyInsight insight) {
        return new InsightRequestResponse(insight.getId(), insight.getWeekStart(),
                WeekRules.weekEnd(insight.getWeekStart()), WeekRules.label(insight.getWeekStart()),
                InsightStatus.GENERATING);
    }

    private Sort.Direction parseSort(String sort) {
        if (sort == null || sort.equals("latest")) {
            return Sort.Direction.DESC;
        }
        if (sort.equals("oldest")) {
            return Sort.Direction.ASC;
        }
        throw new CustomException(ErrorCode.BAD_REQUEST, "sort는 latest 또는 oldest여야 합니다.");
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDate parseDate(String dateText) {
        try {
            return LocalDate.parse(dateText);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)");
        }
    }
}
