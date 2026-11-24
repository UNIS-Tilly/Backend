package com.unis.tilly.insight.service;

import com.unis.tilly.ai.dto.WeeklyInsightInput;
import com.unis.tilly.insight.dto.InsightSnapshot;
import com.unis.tilly.record.service.RecordService;
import com.unis.tilly.task.entity.Task;
import com.unis.tilly.task.service.TaskService;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.Competency;
import com.unis.tilly.user.enums.Gender;
import com.unis.tilly.user.enums.JobGroup;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

// 한 주의 기록 모으기·집계 (D-112, D-126, D-128, D-133, D-135, D-136, API_SPEC 8장 4번)
@ExtendWith(MockitoExtension.class)
class InsightInputAssemblerTest {

    private static final Long USER = 1L;
    private static final LocalDate MON = LocalDate.of(2026, 10, 19);
    private static final LocalDate SUN = LocalDate.of(2026, 10, 25);

    @Mock
    private TaskService taskService;
    @Mock
    private RecordService recordService;
    @Mock
    private UserService userService;

    private InsightInputAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new InsightInputAssembler(taskService, recordService, userService, new InsightSnapshots());
    }

    private Task task(LocalDate date, String title, boolean completed, String memo) {
        Task task = new Task(USER, title, date, null);
        if (completed) {
            task.complete(date.plusDays(5));   // 완료일은 집계에 쓰지 않는다 (D-135)
        }
        task.changeMemo(memo);
        return task;
    }

    // 10/19: 2개(1 완료), 10/20: 1개(완료, 메모), 10/21: 1개(미완료), 10/24(토): 1개(완료)
    private List<Task> weekTasks() {
        return List.of(
                task(MON, "보고서", true, null), task(MON, "회의", false, null),
                task(MON.plusDays(1), "정리", true, "구조를 바꿨다"),
                task(MON.plusDays(2), "발표", false, null),
                task(MON.plusDays(5), "주말 일", true, null));
    }

    private List<RecordService.CompletedDay> completedDays() {
        return List.of(
                new RecordService.CompletedDay(MON, null, List.of()),
                new RecordService.CompletedDay(MON.plusDays(1), "회고 내용", List.of()),
                new RecordService.CompletedDay(MON.plusDays(2), null,
                        List.of(new RecordService.AnsweredQuestion("질문", "답변"))));
    }

    private void givenWeek(List<Task> weekTasks, List<RecordService.CompletedDay> days) {
        given(taskService.getTasksBetween(USER, MON, SUN)).willReturn(weekTasks);
        given(recordService.getCompletedDays(USER, MON, SUN)).willReturn(days);
    }

    private void givenOtherQueries(List<Task> flowTasks, List<Task> previousWeekTasks) {
        given(taskService.getTasksBetween(USER, LocalDate.of(2026, 10, 5), SUN)).willReturn(flowTasks);
        given(taskService.getTasksBetween(USER, MON.minusWeeks(1), MON.minusDays(1))).willReturn(previousWeekTasks);
        User user = new User("a@example.com", "닉네임", SocialProvider.GOOGLE, "sub");
        user.changeProfile("닉네임", Gender.FEMALE, 25, JobGroup.DESIGN, JobGroup.IT_DEVELOPMENT);
        given(userService.getProfile(USER)).willReturn(new UserService.UserProfile(user,
                List.of(Competency.PARTNERSHIP, Competency.DEV_PROBLEM_SOLVING)));
    }

    @Test
    void IN05_주간_달성은_월_일_7일_전체를_배정일_기준으로_센다() {
        givenWeek(weekTasks(), completedDays());
        givenOtherQueries(weekTasks(), List.of());

        InsightInputAssembler.Assembled assembled = assembler.assemble(USER, assembler.collect(USER, MON));

        InsightSnapshot.Achievement achievement = new InsightSnapshots().readAchievement(assembled.achievementJson());
        assertThat(achievement.totalCount()).isEqualTo(5);
        assertThat(achievement.completedCount()).isEqualTo(3);
        assertThat(achievement.days()).hasSize(7);
        assertThat(achievement.days()).extracting(InsightSnapshot.Day::dayOfWeek)
                .containsExactly("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
        assertThat(achievement.days().get(0)).isEqualTo(new InsightSnapshot.Day("2026-10-19", "MON", 1, 2));
        assertThat(achievement.days().get(1)).isEqualTo(new InsightSnapshot.Day("2026-10-20", "TUE", 1, 1));
        assertThat(achievement.days().get(2)).isEqualTo(new InsightSnapshot.Day("2026-10-21", "WED", 0, 1));
        assertThat(achievement.days().get(3)).isEqualTo(new InsightSnapshot.Day("2026-10-22", "THU", 0, 0));
        assertThat(achievement.days().get(5)).isEqualTo(new InsightSnapshot.Day("2026-10-24", "SAT", 1, 1));   // 토요일 포함
    }

    @Test
    void IN05_업무_흐름은_같은_달_1주차부터_해당_주차까지_주별로_센다() {
        List<Task> flowTasks = List.of(
                task(LocalDate.of(2026, 10, 6), "1주차 일", true, null),
                task(LocalDate.of(2026, 10, 13), "2주차 일", false, null),
                task(LocalDate.of(2026, 10, 14), "2주차 일2", true, null),
                task(MON, "3주차 일", true, null));
        givenWeek(weekTasks(), completedDays());
        givenOtherQueries(flowTasks, List.of());

        InsightInputAssembler.Assembled assembled = assembler.assemble(USER, assembler.collect(USER, MON));

        List<InsightSnapshot.FlowWeek> flow = new InsightSnapshots().readFlow(assembled.flowJson());
        assertThat(flow).containsExactly(
                new InsightSnapshot.FlowWeek("10월 1주차", "2026-10-05", "2026-10-11", 1, 1),
                new InsightSnapshot.FlowWeek("10월 2주차", "2026-10-12", "2026-10-18", 1, 2),
                new InsightSnapshot.FlowWeek("10월 3주차", "2026-10-19", "2026-10-25", 1, 1));
    }

    @Test
    void IN03_AI_입력은_퇴근_완료한_날의_할_일_답변_회고만_담고_수치는_서버가_계산해_준다() {
        givenWeek(weekTasks(), completedDays());
        givenOtherQueries(weekTasks(), List.of(task(MON.minusDays(7), "지난 주", true, null),
                task(MON.minusDays(6), "지난 주2", false, null)));

        WeeklyInsightInput input = assembler.assemble(USER, assembler.collect(USER, MON)).input();

        assertThat(input.weekLabel()).isEqualTo("10월 3주차");
        assertThat(input.stats()).isEqualTo(new WeeklyInsightInput.WeekStats(5, 3));
        assertThat(input.previousWeekStats()).isEqualTo(new WeeklyInsightInput.WeekStats(2, 1));
        // 퇴근 완료한 3일만, 토요일 할 일(퇴근 미완료)은 AI 입력에서 제외되지만 주간 수치에는 들어간다
        assertThat(input.days()).extracting(WeeklyInsightInput.DayInput::date)
                .containsExactly(MON, MON.plusDays(1), MON.plusDays(2));
        assertThat(input.days().get(0).tasks()).extracting("title").containsExactly("보고서", "회의");
        assertThat(input.days().get(1).tasks().get(0).memo()).isEqualTo("구조를 바꿨다");
        assertThat(input.days().get(1).reflection()).isEqualTo("회고 내용");
        assertThat(input.days().get(2).answers()).extracting(WeeklyInsightInput.AnswerInput::answer).containsExactly("답변");
        assertThat(input.insufficientContent()).isFalse();
    }

    @Test
    void IN03_지난_주에_배정된_할_일이_없으면_비교_자료는_null이다() {
        givenWeek(weekTasks(), completedDays());
        givenOtherQueries(weekTasks(), List.of());

        assertThat(assembler.assemble(USER, assembler.collect(USER, MON)).input().previousWeekStats()).isNull();
    }

    @Test
    void IN03_AI에는_희망_직군과_선택_역량_정의만_보내고_식별_정보는_넣지_않는다() {
        givenWeek(weekTasks(), completedDays());
        givenOtherQueries(weekTasks(), List.of());

        WeeklyInsightInput.Profile profile = assembler.assemble(USER, assembler.collect(USER, MON)).input().profile();

        assertThat(profile.targetJobGroupCode()).isEqualTo("IT_DEVELOPMENT");   // 재직 직군(DESIGN)이 아니라 희망 직군
        assertThat(profile.targetJobGroupLabel()).isEqualTo("IT/개발");
        assertThat(profile.competencies()).extracting(WeeklyInsightInput.CompetencyInfo::code)
                .containsExactly("PARTNERSHIP", "DEV_PROBLEM_SOLVING");
        assertThat(profile.competencies().get(1).definition()).isEqualTo(Competency.DEV_PROBLEM_SOLVING.getDescription());
        // 프로필 record에는 닉네임·이메일·성별·나이를 담을 필드가 없다
        assertThat(WeeklyInsightInput.Profile.class.getRecordComponents()).extracting("name")
                .containsExactly("targetJobGroupCode", "targetJobGroupLabel", "competencies");
    }

    @Test
    void IN03_희망_직군이_없으면_직군_정보는_null이고_선택_역량만_준다() {
        givenWeek(weekTasks(), completedDays());
        given(taskService.getTasksBetween(USER, LocalDate.of(2026, 10, 5), SUN)).willReturn(weekTasks());
        given(taskService.getTasksBetween(USER, MON.minusWeeks(1), MON.minusDays(1))).willReturn(List.of());
        User user = new User("a@example.com", "닉네임", SocialProvider.GOOGLE, "sub");
        given(userService.getProfile(USER)).willReturn(new UserService.UserProfile(user, List.of(Competency.RESILIENCE)));

        WeeklyInsightInput.Profile profile = assembler.assemble(USER, assembler.collect(USER, MON)).input().profile();

        assertThat(profile.targetJobGroupCode()).isNull();
        assertThat(profile.targetJobGroupLabel()).isNull();
        assertThat(profile.competencies()).extracting(WeeklyInsightInput.CompetencyInfo::code).containsExactly("RESILIENCE");
    }

    @Test
    void IN04_요약_수치는_퇴근_완료한_날_기준이다() {
        givenWeek(weekTasks(), completedDays());
        givenOtherQueries(weekTasks(), List.of());

        InsightInputAssembler.Assembled assembled = assembler.assemble(USER, assembler.collect(USER, MON));

        assertThat(assembled.summary().recordedDays()).isEqualTo(3);
        assertThat(assembled.summary().memoCount()).isEqualTo(1);        // 토요일(미완료 기록)의 메모는 제외
        assertThat(assembled.summary().reflectionCount()).isEqualTo(1);
        assertThat(assembled.summary().answerCount()).isEqualTo(1);
        assertThat(assembled.summary().totalCount()).isEqualTo(5);
        assertThat(assembled.summary().completedCount()).isEqualTo(3);
    }

    // ---- 요청 가능 조건 (D-51, D-136) ----

    @Test
    void IN03_메모_회고_답변이_없으면_할_일_제목만으로_분석하고_경고한다() {
        givenWeek(List.of(task(MON, "보고서", true, null)), List.of(
                new RecordService.CompletedDay(MON, null, List.of()),
                new RecordService.CompletedDay(MON.plusDays(1), null, List.of()),
                new RecordService.CompletedDay(MON.plusDays(2), null, List.of())));

        InsightInputAssembler.WeekData data = assembler.collect(USER, MON);

        assertThat(data.insufficientContent()).isTrue();
        assertThat(data.hasContent()).isTrue();
        assertThat(data.eligible()).isTrue();
    }

    @Test
    void IN03_퇴근_완료가_3일_미만이면_요청할_수_없다() {
        givenWeek(weekTasks(), completedDays().subList(0, 2));

        InsightInputAssembler.WeekData data = assembler.collect(USER, MON);

        assertThat(data.recordedDays()).isEqualTo(2);
        assertThat(data.eligible()).isFalse();
    }

    @Test
    void IN03_할_일이_0개여도_회고나_답변이_있으면_분석할_수_있다() {
        givenWeek(List.of(), completedDays());

        InsightInputAssembler.WeekData data = assembler.collect(USER, MON);

        assertThat(data.hasContent()).isTrue();   // D-136
        assertThat(data.eligible()).isTrue();
    }

    @Test
    void IN03_할_일_회고_메모_답변이_모두_없으면_분석할_수_없다() {
        givenWeek(List.of(), List.of(
                new RecordService.CompletedDay(MON, null, List.of()),
                new RecordService.CompletedDay(MON.plusDays(1), null, List.of()),
                new RecordService.CompletedDay(MON.plusDays(2), null, List.of())));

        InsightInputAssembler.WeekData data = assembler.collect(USER, MON);

        assertThat(data.recordedDays()).isEqualTo(3);
        assertThat(data.hasContent()).isFalse();
        assertThat(data.eligible()).isFalse();   // D-136: INSUFFICIENT_RECORDS
    }
}
