package com.unis.tilly.insight.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.insight.dto.InsightLatestResponse;
import com.unis.tilly.insight.dto.InsightListResponse;
import com.unis.tilly.insight.dto.InsightRequestResponse;
import com.unis.tilly.insight.dto.InsightSnapshot;
import com.unis.tilly.insight.dto.InsightWeekResponse;
import com.unis.tilly.insight.entity.InsightAction;
import com.unis.tilly.insight.entity.InsightEvidence;
import com.unis.tilly.insight.entity.InsightImprovement;
import com.unis.tilly.insight.entity.InsightStrength;
import com.unis.tilly.insight.entity.WeeklyInsight;
import com.unis.tilly.insight.enums.EvidenceType;
import com.unis.tilly.insight.enums.InsightStatus;
import com.unis.tilly.insight.enums.StarPart;
import com.unis.tilly.insight.repository.WeeklyInsightRepository;
import com.unis.tilly.record.service.RecordService;
import com.unis.tilly.task.entity.Task;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 9.1 조회, 9.2 분석 요청(D-139 요청 시점, D-136, D-137), 9.4 홈, 9.5 목록
@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long USER = 1L;
    private static final LocalDate WEEK = LocalDate.of(2026, 10, 19);          // 월요일
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 21);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 23);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 24);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 25);

    @Mock
    private InsightStore store;
    @Mock
    private InsightGenerationService generationService;
    @Mock
    private InsightInputAssembler assembler;
    @Mock
    private RecordService recordService;
    @Mock
    private WeeklyInsightRepository insightRepository;

    private final InsightSnapshots snapshots = new InsightSnapshots();

    private InsightService serviceOn(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(10, 0).atZone(SEOUL).toInstant(), SEOUL);
        return new InsightService(store, generationService, assembler, recordService, insightRepository, snapshots, clock);
    }

    private InsightInputAssembler.WeekData data(int completedDays, boolean withTask) {
        List<RecordService.CompletedDay> days = java.util.stream.IntStream.range(0, completedDays)
                .mapToObj(i -> new RecordService.CompletedDay(WEEK.plusDays(i), null, List.of())).toList();
        List<Task> tasks = withTask ? List.of(new Task(USER, "일", WEEK, null)) : List.of();
        return new InsightInputAssembler.WeekData(WEEK, LocalDate.of(2026, 10, 25), tasks, days);
    }

    private WeeklyInsight generating(Long id) {
        WeeklyInsight insight = new WeeklyInsight(USER, WEEK, LocalDateTime.of(2026, 10, 24, 10, 0));
        ReflectionTestUtils.setField(insight, "id", id);
        return insight;
    }

    private void assertCode(ThrowableAssert.ThrowingCallable callable, ErrorCode code) {
        assertThatThrownBy(callable).isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(code));
    }

    // ---- 분석 요청 시점 (D-139) ----

    @Test
    void IN03_월요일부터_목요일은_요청할_수_없다() {
        for (int offset = 0; offset < 4; offset++) {
            LocalDate day = WEEK.plusDays(offset);
            assertCode(() -> serviceOn(day).request(USER, day.toString()), ErrorCode.INSIGHT_NOT_REQUESTABLE);
        }
        verify(store, never()).begin(anyLong(), any());
    }

    @Test
    void IN03_금요일은_그날_기록을_퇴근_완료한_뒤에만_요청할_수_있다() {
        given(recordService.isCompleted(USER, FRIDAY)).willReturn(false);

        assertCode(() -> serviceOn(FRIDAY).request(USER, FRIDAY.toString()), ErrorCode.INSIGHT_NOT_REQUESTABLE);
        verify(store, never()).begin(anyLong(), any());
    }

    @Test
    void IN03_금요일_퇴근_완료_뒤에는_이번_주를_분석_요청한다() {
        given(recordService.isCompleted(USER, FRIDAY)).willReturn(true);
        givenRequestPath(10L);

        InsightRequestResponse response = serviceOn(FRIDAY).request(USER, FRIDAY.toString());

        assertThat(response.status()).isEqualTo(InsightStatus.GENERATING);
        assertThat(response.weekStart()).isEqualTo(WEEK);
        assertThat(response.weekEnd()).isEqualTo(SUNDAY);
        assertThat(response.weekLabel()).isEqualTo("10월 3주차");
        assertThat(response.insightId()).isEqualTo(10L);
    }

    @Test
    void IN03_토요일과_일요일은_퇴근_시간과_무관하게_요청할_수_있다() {
        givenRequestPath(10L);

        assertThat(serviceOn(SATURDAY).request(USER, SATURDAY.toString()).status()).isEqualTo(InsightStatus.GENERATING);
        assertThat(serviceOn(SUNDAY).request(USER, SUNDAY.toString()).status()).isEqualTo(InsightStatus.GENERATING);
        verify(recordService, never()).isCompleted(anyLong(), any());
    }

    @Test
    void IN03_지난_주나_다음_주는_요청할_수_없고_놓친_주는_요청할_수_없다() {
        // 토요일에 지난 주(10/12~)·다음 주(10/26~) 날짜로 요청
        assertCode(() -> serviceOn(SATURDAY).request(USER, "2026-10-14"), ErrorCode.INSIGHT_NOT_REQUESTABLE);
        assertCode(() -> serviceOn(SATURDAY).request(USER, "2026-10-27"), ErrorCode.INSIGHT_NOT_REQUESTABLE);
        // 월요일에 방금 지난 주를 요청
        assertCode(() -> serviceOn(LocalDate.of(2026, 10, 26)).request(USER, "2026-10-24"), ErrorCode.INSIGHT_NOT_REQUESTABLE);
    }

    @Test
    void IN03_요청의_날짜_형식이_틀리거나_없으면_400() {
        assertCode(() -> serviceOn(SATURDAY).request(USER, "2026/10/24"), ErrorCode.BAD_REQUEST);
        assertCode(() -> serviceOn(SATURDAY).request(USER, null), ErrorCode.BAD_REQUEST);
    }

    // ---- 분석 요청 상태 분기 ----

    @Test
    void IN03_이미_발행된_주는_재요청할_수_없다() {
        WeeklyInsight published = generating(10L);
        published.publish(LocalDateTime.of(2026, 10, 24, 10, 5), false, "t", "s",
                new WeeklyInsight.Summary(3, 0, 0, 0, 0, 0), "{}", "[]");
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.of(published));

        assertCode(() -> serviceOn(SATURDAY).request(USER, SATURDAY.toString()), ErrorCode.INSIGHT_ALREADY_PUBLISHED);
        verify(assembler, never()).collect(anyLong(), any());
    }

    @Test
    void IN03_생성_중이면_새로_시작하지_않고_기존_건을_돌려준다() {
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.of(generating(10L)));

        InsightRequestResponse response = serviceOn(SATURDAY).request(USER, SATURDAY.toString());

        assertThat(response.insightId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(InsightStatus.GENERATING);
        verify(store, never()).begin(anyLong(), any());
        verify(generationService, never()).submit(any(), any());
    }

    @Test
    void IN03_실패한_주는_다시_요청할_수_있다() {
        WeeklyInsight failed = generating(10L);
        failed.fail();
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.of(failed));
        given(assembler.collect(USER, WEEK)).willReturn(data(3, true));
        given(store.begin(USER, WEEK)).willReturn(new InsightStore.Begun(generating(10L), true));
        InsightInputAssembler.Assembled assembled = new InsightInputAssembler.Assembled(null, null, "{}", "[]");
        given(assembler.assemble(any(), any())).willReturn(assembled);

        serviceOn(SATURDAY).request(USER, SATURDAY.toString());

        verify(generationService).submit(10L, assembled);
    }

    @Test
    void IN03_퇴근_완료가_3일_미만이거나_분석할_내용이_없으면_INSUFFICIENT_RECORDS() {
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.empty());
        given(assembler.collect(USER, WEEK)).willReturn(data(2, true), data(3, false));

        assertCode(() -> serviceOn(SATURDAY).request(USER, SATURDAY.toString()), ErrorCode.INSUFFICIENT_RECORDS);
        assertCode(() -> serviceOn(SATURDAY).request(USER, SATURDAY.toString()), ErrorCode.INSUFFICIENT_RECORDS);
        verify(store, never()).begin(anyLong(), any());
    }

    @Test
    void IN03_시작하면_AI_입력을_한_번만_만들어_비동기_생성을_맡긴다() {
        InsightInputAssembler.WeekData data = givenRequestPath(10L);

        serviceOn(SATURDAY).request(USER, SATURDAY.toString());

        verify(assembler).assemble(USER, data);
        verify(generationService).submit(10L, ASSEMBLED);
    }

    @Test
    void IN03_동시에_먼저_시작된_건이_있으면_그_건을_돌려주고_다시_시작하지_않는다() {
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.empty());
        given(assembler.collect(USER, WEEK)).willReturn(data(3, true));
        given(store.begin(USER, WEEK)).willThrow(new DataIntegrityViolationException("duplicate"));
        given(store.find(USER, WEEK)).willReturn(Optional.of(generating(10L)));

        InsightRequestResponse response = serviceOn(SATURDAY).request(USER, SATURDAY.toString());

        assertThat(response.insightId()).isEqualTo(10L);
        verify(generationService, never()).submit(any(), any());
    }

    @Test
    void IN03_동시에_먼저_발행된_건이_있으면_409() {
        WeeklyInsight published = generating(10L);
        published.publish(LocalDateTime.of(2026, 10, 24, 10, 5), false, "t", "s",
                new WeeklyInsight.Summary(3, 0, 0, 0, 0, 0), "{}", "[]");
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.empty());
        given(assembler.collect(USER, WEEK)).willReturn(data(3, true));
        given(store.begin(USER, WEEK)).willThrow(new DataIntegrityViolationException("duplicate"));
        given(store.find(USER, WEEK)).willReturn(Optional.of(published));

        assertCode(() -> serviceOn(SATURDAY).request(USER, SATURDAY.toString()), ErrorCode.INSIGHT_ALREADY_PUBLISHED);
    }

    @Test
    void IN03_입력을_만들다_실패하면_실패로_돌려_잠금을_풀고_오류를_올린다() {
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.empty());
        given(assembler.collect(USER, WEEK)).willReturn(data(3, true));
        given(store.begin(USER, WEEK)).willReturn(new InsightStore.Begun(generating(10L), true));
        given(assembler.assemble(any(), any())).willThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> serviceOn(SATURDAY).request(USER, SATURDAY.toString()))
                .isInstanceOf(IllegalStateException.class);
        verify(store).markFailed(10L);
        verify(generationService, never()).submit(any(), any());
    }

    private static final InsightInputAssembler.Assembled ASSEMBLED =
            new InsightInputAssembler.Assembled(null, null, "{}", "[]");

    private InsightInputAssembler.WeekData givenRequestPath(Long insightId) {
        InsightInputAssembler.WeekData data = data(3, true);
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.empty());
        given(assembler.collect(USER, WEEK)).willReturn(data);
        given(store.begin(USER, WEEK)).willReturn(new InsightStore.Begun(generating(insightId), true));
        given(assembler.assemble(USER, data)).willReturn(ASSEMBLED);
        return data;
    }

    // ---- 조회 (9.1) ----

    @Test
    void IN01_인사이트가_없는_주는_NOT_PUBLISHED이고_요청_조건을_알려준다() {
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.empty());
        given(assembler.collect(USER, WEEK)).willReturn(data(3, true));

        InsightWeekResponse response = serviceOn(SATURDAY).getWeek(USER, "2026-10-21");

        assertThat(response.status()).isEqualTo(InsightStatus.NOT_PUBLISHED);
        assertThat(response.weekStart()).isEqualTo(WEEK);
        assertThat(response.weekEnd()).isEqualTo(SUNDAY);
        assertThat(response.weekLabel()).isEqualTo("10월 3주차");
        assertThat(response.report()).isNull();
        assertThat(response.eligibility()).isEqualTo(new InsightWeekResponse.Eligibility(true, true, 3, 3));
    }

    @Test
    void IN01_요청_조건은_기록_조건과_요청_시점을_따로_알려준다() {
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.empty());
        given(assembler.collect(USER, WEEK)).willReturn(data(2, true), data(3, true));

        // 수요일: 시점 아님 + 기록 2일
        assertThat(serviceOn(WEDNESDAY).getWeek(USER, null).eligibility())
                .isEqualTo(new InsightWeekResponse.Eligibility(false, false, 2, 3));
        // 수요일: 기록 조건은 충족했지만 시점이 아니다
        assertThat(serviceOn(WEDNESDAY).getWeek(USER, null).eligibility())
                .isEqualTo(new InsightWeekResponse.Eligibility(true, false, 3, 3));
    }

    @Test
    void IN01_미래_주는_요청할_수_없다() {
        LocalDate nextWeek = LocalDate.of(2026, 10, 26);
        given(store.findExpiringStale(USER, nextWeek)).willReturn(Optional.empty());
        given(assembler.collect(USER, nextWeek)).willReturn(new InsightInputAssembler.WeekData(
                nextWeek, LocalDate.of(2026, 11, 1), List.of(), List.of()));

        InsightWeekResponse response = serviceOn(SATURDAY).getWeek(USER, "2026-10-27");

        assertThat(response.status()).isEqualTo(InsightStatus.NOT_PUBLISHED);
        assertThat(response.eligibility().eligible()).isFalse();
        assertThat(response.eligibility().requestable()).isFalse();
    }

    @Test
    void IN01_생성_중이면_보고서와_요청_조건_없이_GENERATING만_돌려준다() {
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.of(generating(10L)));

        InsightWeekResponse response = serviceOn(SATURDAY).getWeek(USER, WEEK.toString());

        assertThat(response.status()).isEqualTo(InsightStatus.GENERATING);
        assertThat(response.eligibility()).isNull();
        assertThat(response.report()).isNull();
        verify(assembler, never()).collect(anyLong(), any());
    }

    @Test
    void IN01_실패한_주는_FAILED와_함께_다시_요청할_수_있는_조건을_돌려준다() {
        WeeklyInsight failed = generating(10L);
        failed.fail();
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.of(failed));
        given(assembler.collect(USER, WEEK)).willReturn(data(3, true));

        InsightWeekResponse response = serviceOn(SATURDAY).getWeek(USER, WEEK.toString());

        assertThat(response.status()).isEqualTo(InsightStatus.FAILED);
        assertThat(response.eligibility().eligible()).isTrue();
        assertThat(response.report()).isNull();
    }

    @Test
    void IN01_조회_날짜_형식이_틀리면_400이고_생략하면_오늘의_주다() {
        assertCode(() -> serviceOn(SATURDAY).getWeek(USER, "2026-13-01"), ErrorCode.BAD_REQUEST);

        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.of(generating(10L)));
        assertThat(serviceOn(SATURDAY).getWeek(USER, null).weekStart()).isEqualTo(WEEK);
    }

    @Test
    void IN01_발행된_주는_스냅샷과_강점_보완_근거_행동을_보고서로_돌려준다() {
        WeeklyInsight published = publishedInsight(10L, true);
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.of(published));
        given(store.loadDetails(10L)).willReturn(details());

        InsightWeekResponse response = serviceOn(SATURDAY).getWeek(USER, WEEK.toString());

        assertThat(response.status()).isEqualTo(InsightStatus.PUBLISHED);
        assertThat(response.eligibility()).isNull();
        InsightWeekResponse.Report report = response.report();
        assertThat(report.insightId()).isEqualTo(10L);
        assertThat(report.title()).isEqualTo("이번주는 12개 중 9개를 끝냈어요");
        assertThat(report.warnings()).containsExactly("INSUFFICIENT_CONTENT");
        assertThat(report.summary()).isEqualTo(new InsightWeekResponse.Summary(4, 7, 3, 6));
        assertThat(report.weeklyAchievement().totalCount()).isEqualTo(12);
        assertThat(report.weeklyFlow()).hasSize(1);
        assertThat(report.strengths()).hasSize(1);
        InsightWeekResponse.Strength strength = report.strengths().get(0);
        assertThat(strength.competencyCode()).isEqualTo("DEV_PROBLEM_SOLVING");
        assertThat(strength.tags()).containsExactly("업무 개선 경험");
        assertThat(strength.evidences()).extracting(InsightWeekResponse.Evidence::type)
                .containsExactly(EvidenceType.REFLECTION, EvidenceType.MEMO);
        assertThat(report.improvements().get(0).starPart()).isEqualTo(StarPart.A);
        assertThat(report.improvements().get(0).evidences()).extracting(InsightWeekResponse.Evidence::date)
                .containsExactly(LocalDate.of(2026, 10, 22));
        assertThat(report.actions()).hasSize(1);
        assertThat(report.actions().get(0).text()).isEqualTo("회의 후 결정사항 한 줄 메모");
        assertThat(report.actions().get(0).improvementId()).isEqualTo(200L);
    }

    @Test
    void IN01_경고는_분석에_쓴_내용이_충분하면_비어_있다() {
        given(store.findExpiringStale(USER, WEEK)).willReturn(Optional.of(publishedInsight(10L, false)));
        given(store.loadDetails(10L)).willReturn(details());

        assertThat(serviceOn(SATURDAY).getWeek(USER, WEEK.toString()).report().warnings()).isEmpty();
    }

    private WeeklyInsight publishedInsight(Long id, boolean insufficient) {
        WeeklyInsight insight = generating(id);
        insight.publish(LocalDateTime.of(2026, 10, 24, 10, 5), insufficient, "이번주는 12개 중 9개를 끝냈어요", "소제목",
                new WeeklyInsight.Summary(4, 7, 3, 6, 12, 9),
                snapshots.write(new InsightSnapshot.Achievement(12, 9, List.of(
                        new InsightSnapshot.Day("2026-10-19", "MON", 2, 3)))),
                snapshots.write(List.of(new InsightSnapshot.FlowWeek("10월 3주차", "2026-10-19", "2026-10-25", 9, 12))));
        return insight;
    }

    private InsightStore.Details details() {
        InsightStrength strength = new InsightStrength(10L, 1, "비교 기준을 스스로 정리했어요", "설명",
                "DEV_PROBLEM_SOLVING", snapshots.write(List.of("업무 개선 경험")));
        ReflectionTestUtils.setField(strength, "id", 100L);
        InsightImprovement improvement = new InsightImprovement(10L, 1, "협업 과정을 더 기록하면 좋아요", "설명", StarPart.A);
        ReflectionTestUtils.setField(improvement, "id", 200L);
        InsightEvidence reflection = InsightEvidence.ofStrength(10L, 100L, LocalDate.of(2026, 10, 23), EvidenceType.REFLECTION);
        InsightEvidence memo = InsightEvidence.ofStrength(10L, 100L, LocalDate.of(2026, 10, 21), EvidenceType.MEMO);
        InsightEvidence task = InsightEvidence.ofImprovement(10L, 200L, LocalDate.of(2026, 10, 22), EvidenceType.TASK);
        InsightAction action = new InsightAction(10L, 200L, "회의 후 결정사항 한 줄 메모");
        ReflectionTestUtils.setField(action, "id", 300L);
        return new InsightStore.Details(List.of(strength), List.of(improvement), List.of(reflection, memo, task), List.of(action));
    }

    // ---- 홈 (9.4) ----

    @Test
    void HM12_가장_최근에_발행된_인사이트의_제목_소제목_추천_행동을_돌려준다() {
        WeeklyInsight latest = publishedInsight(10L, false);
        given(insightRepository.findFirstByUserIdAndStatusOrderByWeekStartDesc(USER, InsightStatus.PUBLISHED))
                .willReturn(Optional.of(latest));
        given(store.loadDetails(10L)).willReturn(details());

        InsightLatestResponse response = serviceOn(SATURDAY).getLatest(USER);

        assertThat(response.status()).isEqualTo(InsightStatus.PUBLISHED);
        assertThat(response.insightId()).isEqualTo(10L);
        assertThat(response.weekStart()).isEqualTo(WEEK);
        assertThat(response.weekEnd()).isEqualTo(SUNDAY);
        assertThat(response.weekLabel()).isEqualTo("10월 3주차");
        assertThat(response.title()).isEqualTo("이번주는 12개 중 9개를 끝냈어요");
        assertThat(response.actions()).extracting(InsightLatestResponse.ActionText::text)
                .containsExactly("회의 후 결정사항 한 줄 메모");
    }

    @Test
    void HM12_발행된_인사이트가_없으면_NOT_PUBLISHED와_빈_추천_행동이다() {
        given(insightRepository.findFirstByUserIdAndStatusOrderByWeekStartDesc(USER, InsightStatus.PUBLISHED))
                .willReturn(Optional.empty());

        InsightLatestResponse response = serviceOn(SATURDAY).getLatest(USER);

        assertThat(response.status()).isEqualTo(InsightStatus.NOT_PUBLISHED);
        assertThat(response.insightId()).isNull();
        assertThat(response.title()).isNull();
        assertThat(response.actions()).isEmpty();
    }

    // ---- 목록 (9.5) ----

    @Test
    void IN01_목록은_기본_최신순_0페이지_20개이고_주차와_기간을_채운다() {
        given(insightRepository.findAllByUserIdAndStatus(any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(publishedInsight(10L, false)),
                        org.springframework.data.domain.PageRequest.of(0, 20), 41));

        InsightListResponse response = serviceOn(SATURDAY).getList(USER, null, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(insightRepository).findAllByUserIdAndStatus(any(), any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort().getOrderFor("weekStart").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(response.totalElements()).isEqualTo(41);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.items().get(0).weekLabel()).isEqualTo("10월 3주차");
        assertThat(response.items().get(0).weekEnd()).isEqualTo(SUNDAY);
        assertThat(response.items().get(0).subtitle()).isEqualTo("소제목");
    }

    @Test
    void IN01_정렬은_latest_oldest만_허용하고_oldest는_오름차순이다() {
        given(insightRepository.findAllByUserIdAndStatus(any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        serviceOn(SATURDAY).getList(USER, "oldest", 1, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(insightRepository).findAllByUserIdAndStatus(any(), any(), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("weekStart").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);

        assertCode(() -> serviceOn(SATURDAY).getList(USER, "popular", null, null), ErrorCode.BAD_REQUEST);
    }

    @Test
    void IN01_페이지와_크기_범위를_벗어나면_400() {
        assertCode(() -> serviceOn(SATURDAY).getList(USER, null, -1, null), ErrorCode.BAD_REQUEST);
        assertCode(() -> serviceOn(SATURDAY).getList(USER, null, null, 0), ErrorCode.BAD_REQUEST);
        assertCode(() -> serviceOn(SATURDAY).getList(USER, null, null, 101), ErrorCode.BAD_REQUEST);
        verify(insightRepository, never()).findAllByUserIdAndStatus(any(), any(), any(Pageable.class));
    }
}
