package com.unis.tilly.insight.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.ai.client.WeeklyInsightGenerator;
import com.unis.tilly.ai.dto.WeeklyInsightInput;
import com.unis.tilly.ai.dto.WeeklyInsightResult;
import com.unis.tilly.ai.enums.AiEvidenceType;
import com.unis.tilly.ai.enums.AiStarPart;
import com.unis.tilly.auth.client.GoogleOidcClient;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.insight.repository.WeeklyInsightRepository;
import com.unis.tilly.insight.service.InsightStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 9장 주간 인사이트(IN-01~IN-08, HM-12)를 실제 가입된 사용자 + MySQL로 끝까지 확인한다.
// 외부 AI는 호출하지 않는다(WeeklyInsightGenerator를 Mock으로 대체). 생성은 실제 비동기이므로 결과는 폴링으로 기다린다.
// 기준 주: 2026-10-19(월) ~ 10-25(일), "오늘"은 테스트용 Clock으로 옮긴다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InsightFlowTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate MON = LocalDate.of(2026, 10, 19);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 24);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("tilly")
            .withUsername("tilly")
            .withPassword("tilly");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MutableClock clock;
    @Autowired
    private WeeklyInsightRepository insightRepository;
    @Autowired
    private InsightStore insightStore;

    @MockitoBean
    private GoogleOidcClient googleOidcClient;
    @MockitoBean
    private WeeklyInsightGenerator generator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CountDownLatch release;   // 생성을 붙잡아 두는 테스트용. AfterEach에서 항상 풀어 준다

    @BeforeEach
    void setUp() {
        clock.setToday(SATURDAY);
        reset(generator);
        release = new CountDownLatch(1);
        given(generator.generate(any())).willAnswer(invocation -> resultFor(invocation.getArgument(0)));
    }

    @AfterEach
    void releaseBlockedGeneration() {
        release.countDown();
    }

    // 입력에서 만든 결과 (제목의 숫자는 서버가 준 값만 쓴다)
    private WeeklyInsightResult resultFor(WeeklyInsightInput input) {
        LocalDate evidenceDate = input.days().get(0).date();
        List<WeeklyInsightResult.Evidence> evidences =
                List.of(new WeeklyInsightResult.Evidence(evidenceDate, AiEvidenceType.TASK));
        String code = input.profile().competencies().isEmpty() ? null : input.profile().competencies().get(0).code();
        return new WeeklyInsightResult(
                "이번주는 " + input.stats().totalCount() + "개 중 " + input.stats().completedCount() + "개를 끝냈어요", "소제목",
                List.of(new WeeklyInsightResult.Strength("강점", "설명", code, List.of("업무 개선 경험"), evidences)),
                List.of(new WeeklyInsightResult.Improvement("보완", "설명", AiStarPart.A, evidences,
                        "회의 후 결정사항 한 줄 메모")));
    }

    // 생성을 release 래치가 풀릴 때까지 붙잡아 둔다
    // (이미 스텁된 메서드를 다시 스텁하므로 given() 대신 doAnswer().when()을 쓴다)
    private void blockGeneration() {
        doAnswer(invocation -> {
            release.await(30, TimeUnit.SECONDS);
            return resultFor(invocation.getArgument(0));
        }).when(generator).generate(any());
    }

    // ---- 분석 요청 → 발행 (9.1, 9.2) ----

    @Test
    void IN03_토요일에_요청하면_202로_시작하고_비동기로_발행되어_보고서를_돌려준다() throws Exception {
        Session me = register();
        givenCompletedWeek(me);

        requestInsight(me, "2026-10-24")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("GENERATING"))
                .andExpect(jsonPath("$.data.weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.data.weekEnd").value("2026-10-25"))
                .andExpect(jsonPath("$.data.weekLabel").value("10월 3주차"))
                .andExpect(jsonPath("$.data.insightId").isNumber());

        awaitStatus(me, "2026-10-21", "PUBLISHED")
                .andExpect(jsonPath("$.data.weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.data.eligibility").doesNotExist())
                .andExpect(jsonPath("$.data.report.title").value("이번주는 4개 중 2개를 끝냈어요"))
                .andExpect(jsonPath("$.data.report.warnings.length()").value(0))
                .andExpect(jsonPath("$.data.report.summary.recordedDays").value(3))
                .andExpect(jsonPath("$.data.report.summary.memoCount").value(1))
                .andExpect(jsonPath("$.data.report.summary.reflectionCount").value(1))
                .andExpect(jsonPath("$.data.report.summary.answerCount").value(0))
                .andExpect(jsonPath("$.data.report.weeklyAchievement.totalCount").value(4))
                .andExpect(jsonPath("$.data.report.weeklyAchievement.completedCount").value(2))
                .andExpect(jsonPath("$.data.report.weeklyAchievement.days.length()").value(7))
                .andExpect(jsonPath("$.data.report.weeklyAchievement.days[0].dayOfWeek").value("MON"))
                .andExpect(jsonPath("$.data.report.weeklyAchievement.days[0].totalCount").value(2))
                .andExpect(jsonPath("$.data.report.weeklyAchievement.days[0].completedCount").value(1))
                .andExpect(jsonPath("$.data.report.weeklyAchievement.days[6].dayOfWeek").value("SUN"))
                .andExpect(jsonPath("$.data.report.weeklyFlow.length()").value(3))
                .andExpect(jsonPath("$.data.report.weeklyFlow[0].weekLabel").value("10월 1주차"))
                .andExpect(jsonPath("$.data.report.weeklyFlow[2].weekLabel").value("10월 3주차"))
                .andExpect(jsonPath("$.data.report.weeklyFlow[2].totalCount").value(4))
                .andExpect(jsonPath("$.data.report.strengths.length()").value(1))
                .andExpect(jsonPath("$.data.report.strengths[0].title").value("강점"))
                .andExpect(jsonPath("$.data.report.strengths[0].tags[0]").value("업무 개선 경험"))
                .andExpect(jsonPath("$.data.report.strengths[0].competencyCode").doesNotExist())
                .andExpect(jsonPath("$.data.report.strengths[0].evidences[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.data.report.strengths[0].evidences[0].type").value("TASK"))
                .andExpect(jsonPath("$.data.report.improvements[0].starPart").value("A"))
                .andExpect(jsonPath("$.data.report.improvements[0].evidences[0].type").value("TASK"))
                .andExpect(jsonPath("$.data.report.actions[0].text").value("회의 후 결정사항 한 줄 메모"))
                .andExpect(jsonPath("$.data.report.actions[0].improvementId").isNumber());
    }

    @Test
    void IN03_AI_입력에는_서버가_계산한_수치와_퇴근_완료한_날의_기록만_들어간다() throws Exception {
        Session me = register();
        givenCompletedWeek(me);
        putProfile(me, "{\"nickname\":\"닉네임\",\"gender\":\"FEMALE\",\"age\":25,\"targetJobGroup\":\"IT_DEVELOPMENT\","
                + "\"competencies\":[\"PARTNERSHIP\",\"DEV_PROBLEM_SOLVING\",\"DEV_PROGRAMMING\"]}");

        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());
        awaitStatus(me, "2026-10-19", "PUBLISHED");

        ArgumentCaptor<WeeklyInsightInput> captor = ArgumentCaptor.forClass(WeeklyInsightInput.class);
        verify(generator).generate(captor.capture());
        WeeklyInsightInput input = captor.getValue();
        assertThat(input.stats()).isEqualTo(new WeeklyInsightInput.WeekStats(4, 2));
        assertThat(input.previousWeekStats()).isNull();   // 지난 주에 배정된 할 일이 없다
        assertThat(input.days()).extracting(WeeklyInsightInput.DayInput::date)
                .containsExactly(MON, MON.plusDays(1), MON.plusDays(2));
        assertThat(input.days().get(1).reflection()).isEqualTo("정리하며 배운 점이 많았다.");
        assertThat(input.insufficientContent()).isFalse();
        assertThat(input.profile().targetJobGroupCode()).isEqualTo("IT_DEVELOPMENT");
        assertThat(input.profile().competencies()).extracting(WeeklyInsightInput.CompetencyInfo::code)
                .containsExactly("PARTNERSHIP", "DEV_PROBLEM_SOLVING", "DEV_PROGRAMMING");
    }

    @Test
    void IN03_메모_회고_답변이_없으면_경고와_함께_할_일_제목만으로_분석한다() throws Exception {
        Session me = register();
        createTask(me, "보고서", "2026-10-19");
        createTask(me, "회의", "2026-10-20");
        for (String day : List.of("2026-10-19", "2026-10-20", "2026-10-21")) {
            completeDay(me, day, Map.of());
        }

        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());

        awaitStatus(me, "2026-10-19", "PUBLISHED")
                .andExpect(jsonPath("$.data.report.warnings[0]").value("INSUFFICIENT_CONTENT"));
    }

    @Test
    void IN03_할_일이_0개여도_회고가_있으면_분석할_수_있다() throws Exception {
        Session me = register();
        for (String day : List.of("2026-10-19", "2026-10-20", "2026-10-21")) {
            completeDay(me, day, Map.of("reflection", day + " 회고"));
        }

        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());

        awaitStatus(me, "2026-10-19", "PUBLISHED")
                .andExpect(jsonPath("$.data.report.weeklyAchievement.totalCount").value(0))
                .andExpect(jsonPath("$.data.report.summary.reflectionCount").value(3))
                .andExpect(jsonPath("$.data.report.warnings.length()").value(0));
    }

    @Test
    void IN03_할_일_회고_메모_답변이_모두_없으면_INSUFFICIENT_RECORDS() throws Exception {
        Session me = register();
        for (String day : List.of("2026-10-19", "2026-10-20", "2026-10-21")) {
            completeDay(me, day, Map.of());
        }

        requestInsight(me, "2026-10-24")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_RECORDS"));
        verify(generator, times(0)).generate(any());
    }

    @Test
    void IN03_퇴근_완료가_3일_미만이면_INSUFFICIENT_RECORDS() throws Exception {
        Session me = register();
        createTask(me, "보고서", "2026-10-19");
        completeDay(me, "2026-10-19", Map.of());
        completeDay(me, "2026-10-20", Map.of());

        requestInsight(me, "2026-10-24")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_RECORDS"));
        getWeek(me, "2026-10-24")
                .andExpect(jsonPath("$.data.status").value("NOT_PUBLISHED"))
                .andExpect(jsonPath("$.data.eligibility.eligible").value(false))
                .andExpect(jsonPath("$.data.eligibility.completedDays").value(2))
                .andExpect(jsonPath("$.data.eligibility.requiredDays").value(3));
    }

    // ---- 요청 시점 (D-139) ----

    @Test
    void IN03_수요일에는_요청할_수_없고_조회에서_요청_가능_여부를_알려준다() throws Exception {
        Session me = register();
        givenCompletedWeek(me);
        clock.setToday(LocalDate.of(2026, 10, 21));

        requestInsight(me, "2026-10-21")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSIGHT_NOT_REQUESTABLE"));
        getWeek(me, "2026-10-21")
                .andExpect(jsonPath("$.data.status").value("NOT_PUBLISHED"))
                .andExpect(jsonPath("$.data.eligibility.eligible").value(true))      // 기록 조건은 충족
                .andExpect(jsonPath("$.data.eligibility.requestable").value(false)); // 시점이 아니다
        verify(generator, times(0)).generate(any());
    }

    @Test
    void IN03_금요일은_그날_기록을_퇴근_완료한_뒤에만_요청할_수_있다() throws Exception {
        Session me = register();
        givenCompletedWeek(me);
        clock.setToday(LocalDate.of(2026, 10, 23));

        requestInsight(me, "2026-10-23")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSIGHT_NOT_REQUESTABLE"));

        completeDay(me, "2026-10-23", Map.of());
        getWeek(me, "2026-10-23").andExpect(jsonPath("$.data.eligibility.requestable").value(true));
        requestInsight(me, "2026-10-23").andExpect(status().isAccepted());
    }

    @Test
    void IN03_지난_주는_요청할_수_없다() throws Exception {
        Session me = register();
        givenCompletedWeek(me);
        clock.setToday(LocalDate.of(2026, 10, 26));   // 다음 주 월요일

        requestInsight(me, "2026-10-24")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSIGHT_NOT_REQUESTABLE"));
    }

    // ---- 잠금 (D-130, D-137) ----

    @Test
    void IN03_생성_중과_발행_뒤에는_그_주의_할_일_기록_회고가_잠긴다() throws Exception {
        Session me = register();
        Long taskId = givenCompletedWeek(me);
        blockGeneration();

        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());

        // 생성 중(GENERATING)에도 잠긴다
        assertWeekLocked(me, taskId);
        getWeek(me, "2026-10-24").andExpect(jsonPath("$.data.status").value("GENERATING"))
                .andExpect(jsonPath("$.data.report").doesNotExist());

        release.countDown();
        awaitStatus(me, "2026-10-24", "PUBLISHED");
        assertWeekLocked(me, taskId);   // 발행 뒤에도 잠긴다
        // 다른 주는 영향이 없다
        createTask(me, "다음 주 일", "2026-10-26");
        mockMvc.perform(put("/api/records/2026-10-26/draft").header("Authorization", me.bearer())
                        .contentType("application/json").content("{\"manual\":true,\"reflection\":\"다음 주 회고\"}"))
                .andExpect(status().isOk());
    }

    private void assertWeekLocked(Session me, Long taskId) throws Exception {
        patchTask(me, taskId, "{\"title\":\"바꾸기\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RECORD_LOCKED"));
        patchTask(me, taskId, "{\"completed\":true}").andExpect(status().isConflict());
        patchTask(me, taskId, "{\"date\":\"2026-10-26\"}").andExpect(status().isConflict());   // 잠긴 주에서 옮겨 가기
        mockMvc.perform(delete("/api/tasks/" + taskId).header("Authorization", me.bearer()))
                .andExpect(status().isConflict());
        postTask(me, "새 일", "2026-10-22").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RECORD_LOCKED"));
        mockMvc.perform(put("/api/records/2026-10-22/draft").header("Authorization", me.bearer())
                        .contentType("application/json").content("{\"manual\":true,\"reflection\":\"수정\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("RECORD_LOCKED"));
        mockMvc.perform(post("/api/records/2026-10-21/complete").header("Authorization", me.bearer())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/records/2026-10-21/questions").header("Authorization", me.bearer())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/records/2026-10-21/summary").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.locked").value(true));
        // 조회는 잠겨도 된다
        mockMvc.perform(get("/api/tasks").param("date", "2026-10-21").header("Authorization", me.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void IN03_발행된_주는_재요청할_수_없다() throws Exception {
        Session me = register();
        givenCompletedWeek(me);
        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());
        awaitStatus(me, "2026-10-24", "PUBLISHED");

        requestInsight(me, "2026-10-25")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSIGHT_ALREADY_PUBLISHED"));
        verify(generator, times(1)).generate(any());
    }

    // ---- 생성 중 중복 · 실패 · 만료 · 서버 재시작 ----

    @Test
    void IN03_생성_중에_다시_요청하면_기존_건을_돌려주고_AI를_다시_호출하지_않는다() throws Exception {
        Session me = register();
        givenCompletedWeek(me);
        blockGeneration();

        MvcResult first = requestInsight(me, "2026-10-24").andExpect(status().isAccepted()).andReturn();
        Number id = (Number) data(first).get("insightId");

        requestInsight(me, "2026-10-25")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.insightId").value(id.longValue()))
                .andExpect(jsonPath("$.data.status").value("GENERATING"));

        release.countDown();
        awaitStatus(me, "2026-10-24", "PUBLISHED");
        verify(generator, times(1)).generate(any());
    }

    @Test
    void IN03_AI가_실패하면_FAILED가_되어_잠금이_풀리고_다시_요청할_수_있다() throws Exception {
        Session me = register();
        Long taskId = givenCompletedWeek(me);
        doThrow(new CustomException(ErrorCode.AI_GENERATION_FAILED))
                .doAnswer(invocation -> resultFor(invocation.getArgument(0)))
                .when(generator).generate(any());

        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());

        awaitStatus(me, "2026-10-24", "FAILED")
                .andExpect(jsonPath("$.data.eligibility.eligible").value(true))
                .andExpect(jsonPath("$.data.eligibility.requestable").value(true))
                .andExpect(jsonPath("$.data.report").doesNotExist());
        patchTask(me, taskId, "{\"title\":\"실패 뒤 수정\"}").andExpect(status().isOk());   // 잠금이 풀렸다
        mockMvc.perform(get("/api/insights/weekly/latest").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.status").value("NOT_PUBLISHED"));            // 홈에는 발행본만

        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());
        awaitStatus(me, "2026-10-24", "PUBLISHED");
        verify(generator, times(2)).generate(any());
    }

    @Test
    void IN03_생성이_10분을_넘으면_실패로_보고_잠금이_풀리며_늦게_끝난_결과는_저장하지_않는다() throws Exception {
        Session me = register();
        Long taskId = givenCompletedWeek(me);
        CountDownLatch finished = new CountDownLatch(1);
        doAnswer(invocation -> {
            release.await(30, TimeUnit.SECONDS);
            try {
                return resultFor(invocation.getArgument(0));
            } finally {
                finished.countDown();
            }
        }).when(generator).generate(any());
        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());
        patchTask(me, taskId, "{\"title\":\"바꾸기\"}").andExpect(status().isConflict());   // 10분 안에는 잠김

        clock.advance(Duration.ofMinutes(11));

        getWeek(me, "2026-10-24").andExpect(jsonPath("$.data.status").value("FAILED"));
        patchTask(me, taskId, "{\"title\":\"만료 뒤 수정\"}").andExpect(status().isOk());

        release.countDown();
        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        // 늦게 끝난 생성은 발행하지 않는다
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                getWeek(me, "2026-10-24").andExpect(jsonPath("$.data.status").value("FAILED")));
    }

    @Test
    void IN03_서버_시작_정리는_남은_생성_중을_실패로_바꿔_잠금을_푼다() throws Exception {
        Session me = register();
        Long taskId = givenCompletedWeek(me);
        blockGeneration();
        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());
        patchTask(me, taskId, "{\"title\":\"바꾸기\"}").andExpect(status().isConflict());

        assertThat(insightStore.failAllGenerating()).isGreaterThanOrEqualTo(1);

        getWeek(me, "2026-10-24").andExpect(jsonPath("$.data.status").value("FAILED"));
        patchTask(me, taskId, "{\"title\":\"재시작 뒤 수정\"}").andExpect(status().isOk());
    }

    // ---- 홈 카드 · 목록 (9.4, 9.5) ----

    @Test
    void HM12_홈은_가장_최근에_발행된_인사이트의_제목과_추천_행동을_보여준다() throws Exception {
        Session me = register();
        mockMvc.perform(get("/api/insights/weekly/latest").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_PUBLISHED"))
                .andExpect(jsonPath("$.data.insightId").doesNotExist())
                .andExpect(jsonPath("$.data.actions.length()").value(0));

        publishWeek(me, MON.minusWeeks(1));
        publishWeek(me, MON);

        mockMvc.perform(get("/api/insights/weekly/latest").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.data.weekLabel").value("10월 3주차"))
                .andExpect(jsonPath("$.data.title").value("이번주는 2개 중 1개를 끝냈어요"))
                .andExpect(jsonPath("$.data.actions[0].text").value("회의 후 결정사항 한 줄 메모"));
    }

    @Test
    void IN01_목록은_발행된_주만_최신순_오래된순과_페이지로_돌려준다() throws Exception {
        Session me = register();
        publishWeek(me, MON.minusWeeks(1));
        publishWeek(me, MON);
        // 실패한 주는 목록에 없다
        doThrow(new CustomException(ErrorCode.AI_TIMEOUT)).when(generator).generate(any());
        clock.setToday(LocalDate.of(2026, 10, 31));
        givenWeekRecords(me, LocalDate.of(2026, 10, 26));
        requestInsight(me, "2026-10-31").andExpect(status().isAccepted());
        awaitStatus(me, "2026-10-31", "FAILED");

        list(me, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.data.items[0].weekEnd").value("2026-10-25"))
                .andExpect(jsonPath("$.data.items[0].weekLabel").value("10월 3주차"))
                .andExpect(jsonPath("$.data.items[0].title").isString())
                .andExpect(jsonPath("$.data.items[0].subtitle").value("소제목"))
                .andExpect(jsonPath("$.data.items[1].weekStart").value("2026-10-12"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        list(me, "?sort=oldest")
                .andExpect(jsonPath("$.data.items[0].weekStart").value("2026-10-12"));
        list(me, "?size=1&page=0")
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        list(me, "?size=1&page=1")
                .andExpect(jsonPath("$.data.items[0].weekStart").value("2026-10-12"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        list(me, "?sort=popular").andExpect(status().isBadRequest());
        list(me, "?size=0").andExpect(status().isBadRequest());
        list(me, "?page=-1").andExpect(status().isBadRequest());
    }

    // ---- 접근 통제 · 입력 오류 · 탈퇴 ----

    @Test
    void IN01_다른_사용자의_인사이트는_조회되지도_잠기지도_않는다() throws Exception {
        Session owner = register();
        givenCompletedWeek(owner);
        requestInsight(owner, "2026-10-24").andExpect(status().isAccepted());
        awaitStatus(owner, "2026-10-24", "PUBLISHED");
        Session me = register();
        Long myTask = createTask(me, "내 일", "2026-10-21");

        getWeek(me, "2026-10-21").andExpect(jsonPath("$.data.status").value("NOT_PUBLISHED"))
                .andExpect(jsonPath("$.data.report").doesNotExist());
        mockMvc.perform(get("/api/insights/weekly/latest").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.status").value("NOT_PUBLISHED"));
        list(me, "").andExpect(jsonPath("$.data.items.length()").value(0));
        patchTask(me, myTask, "{\"title\":\"내 일 수정\"}").andExpect(status().isOk());   // 남의 발행이 내 주를 잠그지 않는다
    }

    @Test
    void IN03_입력_오류는_400이고_토큰이_없으면_401() throws Exception {
        Session me = register();

        getWeek(me, "2026-13-01").andExpect(status().isBadRequest());
        requestInsight(me, "2026/10/24").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/insights/weekly").header("Authorization", me.bearer())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/insights/weekly")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/insights/weekly").contentType("application/json").content("{\"date\":\"2026-10-24\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/insights/weekly/latest")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/insights/weekly/list")).andExpect(status().isUnauthorized());
    }

    @Test
    void IN01_날짜를_생략하면_오늘이_속한_주를_조회한다() throws Exception {
        Session me = register();

        mockMvc.perform(get("/api/insights/weekly").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.data.status").value("NOT_PUBLISHED"));
    }

    @Test
    void ST03_탈퇴하면_인사이트도_함께_삭제된다() throws Exception {
        Session me = register();
        givenCompletedWeek(me);
        requestInsight(me, "2026-10-24").andExpect(status().isAccepted());
        awaitStatus(me, "2026-10-24", "PUBLISHED");
        assertThat(insightRepository.findByUserIdAndWeekStart(me.userId(), MON)).isPresent();

        mockMvc.perform(post("/api/users/me/withdrawal").header("Authorization", me.bearer())
                        .contentType("application/json").content("{\"confirmEmail\":\"" + me.email() + "\"}"))
                .andExpect(status().isOk());

        assertThat(insightRepository.findByUserIdAndWeekStart(me.userId(), MON)).isEmpty();
    }

    // ---- helpers ----

    // 10/19: 보고서(완료)·회의, 10/20: 정리(완료, 메모, 회고), 10/21: 발표 + 세 날 퇴근 완료. 첫 할 일의 ID를 돌려준다
    private Long givenCompletedWeek(Session me) throws Exception {
        return givenWeekRecords(me, MON);
    }

    private Long givenWeekRecords(Session me, LocalDate monday) throws Exception {
        String d1 = monday.toString();
        String d2 = monday.plusDays(1).toString();
        String d3 = monday.plusDays(2).toString();
        Long first = createTask(me, "보고서", d1);
        createTask(me, "회의", d1);
        Long organize = createTask(me, "정리", d2);
        createTask(me, "발표", d3);
        patchTask(me, first, "{\"completed\":true}").andExpect(status().isOk());
        patchTask(me, organize, "{\"completed\":true}").andExpect(status().isOk());
        completeDay(me, d1, Map.of());
        completeDay(me, d2, Map.of("taskMemos", List.of(memo(organize, "구조를 바꿨다")), "reflection", "정리하며 배운 점이 많았다."));
        completeDay(me, d3, Map.of());
        return first;
    }

    // monday 주를 만들고 그 주 토요일에 요청해 발행까지 기다린다. 2개 중 1개 완료한 단순한 주
    private void publishWeek(Session me, LocalDate monday) throws Exception {
        clock.setToday(monday.plusDays(5));
        Long a = createTask(me, "보고서", monday.toString());
        createTask(me, "회의", monday.toString());
        patchTask(me, a, "{\"completed\":true}").andExpect(status().isOk());
        for (int i = 0; i < 3; i++) {
            completeDay(me, monday.plusDays(i).toString(), Map.of("reflection", "회고 " + i));
        }
        requestInsight(me, monday.plusDays(5).toString()).andExpect(status().isAccepted());
        awaitStatus(me, monday.toString(), "PUBLISHED");
    }

    private ResultActions awaitStatus(Session me, String date, String expected) throws Exception {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                assertThat(data(getWeek(me, date).andReturn()).get("status")).isEqualTo(expected));
        return getWeek(me, date);
    }

    private ResultActions getWeek(Session me, String date) throws Exception {
        return mockMvc.perform(get("/api/insights/weekly").param("date", date).header("Authorization", me.bearer()));
    }

    private ResultActions requestInsight(Session me, String date) throws Exception {
        return mockMvc.perform(post("/api/insights/weekly").header("Authorization", me.bearer())
                .contentType("application/json").content("{\"date\":\"" + date + "\"}"));
    }

    private ResultActions list(Session me, String query) throws Exception {
        return mockMvc.perform(get("/api/insights/weekly/list" + query).header("Authorization", me.bearer()));
    }

    private ResultActions completeDay(Session me, String date, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/records/" + date + "/complete").header("Authorization", me.bearer())
                        .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private ResultActions putProfile(Session me, String json) throws Exception {
        return mockMvc.perform(put("/api/users/me/profile").header("Authorization", me.bearer())
                        .contentType("application/json").content(json))
                .andExpect(status().isOk());
    }

    private ResultActions patchTask(Session me, Long taskId, String json) throws Exception {
        return mockMvc.perform(patch("/api/tasks/" + taskId).header("Authorization", me.bearer())
                .contentType("application/json").content(json));
    }

    private ResultActions postTask(Session me, String title, String date) throws Exception {
        return mockMvc.perform(post("/api/tasks").header("Authorization", me.bearer())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("title", title, "date", date))));
    }

    private Long createTask(Session me, String title, String date) throws Exception {
        MvcResult result = postTask(me, title, date).andExpect(status().isCreated()).andReturn();
        return ((Number) data(result).get("id")).longValue();
    }

    private Map<String, Object> memo(Long taskId, String memo) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", taskId);
        map.put("memo", memo);
        return map;
    }

    private Session register() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        String email = "user-" + UUID.randomUUID() + "@example.com";
        given(googleOidcClient.exchangeCodeForUserInfo(sub)).willReturn(new GoogleOidcClient.GoogleUserInfo(sub, email, true));
        MvcResult login = mockMvc.perform(post("/api/auth/login/google")
                        .contentType("application/json").content("{\"code\":\"" + sub + "\"}"))
                .andReturn();
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "signupToken", data(login).get("signupToken"), "nickname", "닉네임"))))
                .andReturn();
        Map<String, Object> body = data(registered);
        return new Session((String) body.get("accessToken"), ((Number) body.get("userId")).longValue(), email);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
        return (Map<String, Object>) body.get("data");
    }

    private record Session(String accessToken, Long userId, String email) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    // 테스트에서 "오늘"을 옮기고 시간을 흘려보낼 수 있는 Clock (Asia/Seoul)
    static class MutableClock extends Clock {
        private volatile Instant instant = Instant.now();

        void setToday(LocalDate today) {
            this.instant = today.atTime(10, 0).atZone(SEOUL).toInstant();
        }

        void advance(Duration duration) {
            this.instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
