package com.unis.tilly.record.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.ai.client.QuestionGenerator;
import com.unis.tilly.ai.dto.QuestionGenerationInput;
import com.unis.tilly.auth.client.GoogleOidcClient;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.record.service.RecordQuestionService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 6.5 질문 받기, 6.6 자동 질문 (RC-06, D-46, D-47, D-127)을 실제 가입된 사용자 + MySQL로 끝까지 확인한다.
// 외부 AI는 호출하지 않는다(QuestionGenerator를 Mock으로 대체).
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RecordQuestionFlowTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

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
    private RecordQuestionService recordQuestionService;

    @MockitoBean
    private GoogleOidcClient googleOidcClient;
    @MockitoBean
    private QuestionGenerator questionGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        clock.setToday(DAY);
        reset(questionGenerator);
    }

    // ---- 6.5 질문 받기 ----

    @Test
    void RC06_입력을_먼저_저장하고_질문을_만들어_돌려준다() throws Exception {
        Session me = register();
        Long taskId = createCompletedTask(me, "보고서", "2026-10-21");
        given(questionGenerator.generate(any())).willReturn(List.of("판단 기준은 무엇이었나요?", "어려웠던 점은요?"));

        askQuestions(me, "2026-10-21", Map.of("taskMemos", List.of(memo(taskId, "구조를 바꿨다")), "reflection", "회고"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[0].order").value(1))
                .andExpect(jsonPath("$.data.questions[0].text").value("판단 기준은 무엇이었나요?"))
                .andExpect(jsonPath("$.data.questions[0].answer").doesNotExist())
                .andExpect(jsonPath("$.data.questions[1].order").value(2));

        // 입력이 먼저 저장되었고(메모·회고), 기록 상태는 올라가지 않는다 (D-46, D-95)
        getTasks(me, "2026-10-21").andExpect(jsonPath("$.data.completed[0].memo").value("구조를 바꿨다"));
        mockMvc.perform(get("/api/records/2026-10-21/reflection").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.reflection").value("회고"));
        mockMvc.perform(get("/api/records/2026-10-21/summary").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));
        // 조회 API로 같은 질문이 보인다
        mockMvc.perform(get("/api/records/2026-10-21/questions").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[1].text").value("어려웠던 점은요?"));

        // AI에는 그날 할 일·메모·회고가 들어간다
        ArgumentCaptor<QuestionGenerationInput> captor = ArgumentCaptor.forClass(QuestionGenerationInput.class);
        verify(questionGenerator).generate(captor.capture());
        assertThat(captor.getValue().date()).isEqualTo(DAY);
        assertThat(captor.getValue().tasks()).extracting("title", "memo").containsExactly(
                org.assertj.core.groups.Tuple.tuple("보고서", "구조를 바꿨다"));
        assertThat(captor.getValue().reflection()).isEqualTo("회고");
    }

    @Test
    void RC06_질문이_이미_있으면_다시_만들지_않고_본문은_저장한다() throws Exception {
        Session me = register();
        Long taskId = createCompletedTask(me, "보고서", "2026-10-21");
        given(questionGenerator.generate(any())).willReturn(List.of("질문1"));
        MvcResult first = askQuestions(me, "2026-10-21", Map.of()).andExpect(status().isOk()).andReturn();
        Number questionId = (Number) ((List<Map<String, Object>>) data(first).get("questions")).get(0).get("id");

        askQuestions(me, "2026-10-21", Map.of("taskMemos", List.of(memo(taskId, "두 번째 메모")),
                "answers", List.of(answer(questionId.longValue(), "내 답변", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(1))
                .andExpect(jsonPath("$.data.questions[0].id").value(questionId.longValue()))
                .andExpect(jsonPath("$.data.questions[0].answer").value("내 답변"));

        verify(questionGenerator, times(1)).generate(any());   // 재생성 없음 (D-47)
        getTasks(me, "2026-10-21").andExpect(jsonPath("$.data.completed[0].memo").value("두 번째 메모"));
    }

    @Test
    void RC06_AI가_4개를_줘도_질문은_최대_3개만_저장한다() throws Exception {
        Session me = register();
        createCompletedTask(me, "보고서", "2026-10-21");
        given(questionGenerator.generate(any())).willReturn(List.of("1", "2", "3", "4"));

        askQuestions(me, "2026-10-21", Map.of())
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.questions[2].order").value(3));
    }

    @Test
    void RC06_AI가_질문할_게_없다고_하면_빈_목록이고_저장도_기록_행_생성도_하지_않는다() throws Exception {
        Session me = register();
        createCompletedTask(me, "보고서", "2026-10-21");
        given(questionGenerator.generate(any())).willReturn(List.of());

        askQuestions(me, "2026-10-21", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(0));
        mockMvc.perform(get("/api/records/2026-10-21/questions").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.questions.length()").value(0));
        assertThat(recordQuestionService.generateAutomatically(me.userId(), DAY)).isFalse();
    }

    @Test
    void RC06_할_일이_없어도_회고가_있으면_만들_수_있다() throws Exception {
        Session me = register();
        given(questionGenerator.generate(any())).willReturn(List.of("오늘 회고에서 가장 기억에 남는 순간은요?"));

        askQuestions(me, "2026-10-21", Map.of("reflection", "오늘은 배운 게 많았다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(1));

        ArgumentCaptor<QuestionGenerationInput> captor = ArgumentCaptor.forClass(QuestionGenerationInput.class);
        verify(questionGenerator).generate(captor.capture());
        assertThat(captor.getValue().tasks()).isEmpty();
    }

    @Test
    void RC06_그날_할_일도_회고도_없으면_422이고_AI를_호출하지_않는다() throws Exception {
        Session me = register();
        createTask(me, "다른 날 일", "2026-10-22");   // 다른 날 할 일은 세지 않는다

        askQuestions(me, "2026-10-21", Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RECORD_EMPTY"));

        verify(questionGenerator, never()).generate(any());
    }

    @Test
    void RC06_AI가_실패하면_502_504이고_입력은_저장되며_다시_시도할_수_있다() throws Exception {
        Session me = register();
        Long taskId = createCompletedTask(me, "보고서", "2026-10-21");
        given(questionGenerator.generate(any()))
                .willThrow(new CustomException(ErrorCode.AI_GENERATION_FAILED))
                .willThrow(new CustomException(ErrorCode.AI_TIMEOUT))
                .willReturn(List.of("다시 만든 질문"));

        askQuestions(me, "2026-10-21", Map.of("taskMemos", List.of(memo(taskId, "저장될 메모"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("AI_GENERATION_FAILED"));
        askQuestions(me, "2026-10-21", Map.of())
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.errorCode").value("AI_TIMEOUT"));

        // 실패해도 입력은 저장되고 질문은 만들어지지 않았다 (퇴근은 그대로 가능)
        getTasks(me, "2026-10-21").andExpect(jsonPath("$.data.completed[0].memo").doesNotExist());
        mockMvc.perform(get("/api/records/2026-10-21/questions").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.questions.length()").value(0));

        askQuestions(me, "2026-10-21", Map.of("taskMemos", List.of(memo(taskId, "저장될 메모"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].text").value("다시 만든 질문"));
        getTasks(me, "2026-10-21").andExpect(jsonPath("$.data.completed[0].memo").value("저장될 메모"));
    }

    @Test
    void RC06_퇴근_완료_뒤에도_질문이_없으면_만들_수_있다() throws Exception {
        Session me = register();
        createCompletedTask(me, "보고서", "2026-10-21");
        mockMvc.perform(post("/api/records/2026-10-21/complete").header("Authorization", me.bearer())
                .contentType("application/json").content("{}")).andExpect(status().isOk());
        given(questionGenerator.generate(any())).willReturn(List.of("퇴근 뒤 질문"));

        askQuestions(me, "2026-10-21", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].text").value("퇴근 뒤 질문"));
        mockMvc.perform(get("/api/records/2026-10-21/summary").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void RC06_미완료_할_일만_있고_회고가_없으면_422이고_AI를_호출하지_않는다() throws Exception {
        Session me = register();
        createTask(me, "아직 못 끝낸 일", "2026-10-21");

        askQuestions(me, "2026-10-21", Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RECORD_EMPTY"));
        assertThat(recordQuestionService.generateAutomatically(me.userId(), DAY)).isFalse();

        verify(questionGenerator, never()).generate(any());
    }

    @Test
    void RC06_미완료_할_일이_있어도_회고가_있으면_질문을_만들고_할_일은_그대로_보낸다() throws Exception {
        Session me = register();
        createTask(me, "아직 못 끝낸 일", "2026-10-21");
        given(questionGenerator.generate(any())).willReturn(List.of("회고 질문"));

        askQuestions(me, "2026-10-21", Map.of("reflection", "회고"))
                .andExpect(status().isOk());

        ArgumentCaptor<QuestionGenerationInput> captor = ArgumentCaptor.forClass(QuestionGenerationInput.class);
        verify(questionGenerator).generate(captor.capture());
        assertThat(captor.getValue().tasks()).extracting("title", "completed")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("아직 못 끝낸 일", false));
    }

    @Test
    void RC06_질문_입력에_희망_직군_프로필이_들어간다() throws Exception {
        Session me = register();
        createCompletedTask(me, "보고서", "2026-10-21");
        given(questionGenerator.generate(any())).willReturn(List.of("질문"));

        askQuestions(me, "2026-10-21", Map.of()).andExpect(status().isOk());

        ArgumentCaptor<QuestionGenerationInput> captor = ArgumentCaptor.forClass(QuestionGenerationInput.class);
        verify(questionGenerator).generate(captor.capture());
        assertThat(captor.getValue().profile()).isNotNull();
        assertThat(captor.getValue().profile().competencies()).isEmpty();   // 방금 가입해 역량을 고르지 않았다
    }

    @Test
    void RC06_다른_사용자의_할_일과_질문은_쓰이지도_보이지도_않는다() throws Exception {
        Session owner = register();
        createCompletedTask(owner, "남의 일", "2026-10-21");
        given(questionGenerator.generate(any())).willReturn(List.of("남의 질문"));
        askQuestions(owner, "2026-10-21", Map.of()).andExpect(status().isOk());
        Session me = register();

        askQuestions(me, "2026-10-21", Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RECORD_EMPTY"));
        mockMvc.perform(get("/api/records/2026-10-21/questions").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.questions.length()").value(0));
    }

    @Test
    void RC06_입력_오류는_400이고_토큰이_없으면_401() throws Exception {
        Session me = register();

        askQuestions(me, "2026-13-01", Map.of()).andExpect(status().isBadRequest());
        askQuestions(me, "2026-10-21", Map.of("reflection", "가".repeat(2001))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/records/2026-10-21/questions").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verify(questionGenerator, never()).generate(any());
    }

    @Test
    void RC06_생성된_질문에_임시저장으로_답변할_수_있다() throws Exception {
        Session me = register();
        createCompletedTask(me, "보고서", "2026-10-21");
        given(questionGenerator.generate(any())).willReturn(List.of("질문1", "질문2"));
        MvcResult result = askQuestions(me, "2026-10-21", Map.of()).andReturn();
        List<Map<String, Object>> questions = (List<Map<String, Object>>) data(result).get("questions");
        long first = ((Number) questions.get(0).get("id")).longValue();

        mockMvc.perform(put("/api/records/2026-10-21/draft").header("Authorization", me.bearer())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("manual", true,
                                "answers", List.of(answer(first, "첫 답", false))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/records/2026-10-21/questions").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.questions[0].answer").value("첫 답"));
    }

    // ---- 6.6 자동 질문 ----

    @Test
    void RC06_자동_질문은_오늘_기록으로_만들고_이미_있으면_건너뛴다() throws Exception {
        Session me = register();
        createCompletedTask(me, "보고서", "2026-10-21");
        given(questionGenerator.generate(any())).willReturn(List.of("자동 질문"));

        assertThat(recordQuestionService.generateAutomatically(me.userId(), DAY)).isTrue();
        assertThat(recordQuestionService.generateAutomatically(me.userId(), DAY)).isFalse();   // 이미 질문이 있음

        verify(questionGenerator, times(1)).generate(any());
        mockMvc.perform(get("/api/records/2026-10-21/questions").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.questions.length()").value(1))
                .andExpect(jsonPath("$.data.questions[0].text").value("자동 질문"));
        mockMvc.perform(get("/api/records/2026-10-21/summary").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));
    }

    @Test
    void RC06_자동_질문은_이미_퇴근_완료했거나_할_일도_회고도_없으면_건너뛴다() throws Exception {
        Session completed = register();
        createCompletedTask(completed, "보고서", "2026-10-21");
        mockMvc.perform(post("/api/records/2026-10-21/complete").header("Authorization", completed.bearer())
                .contentType("application/json").content("{}")).andExpect(status().isOk());
        Session empty = register();

        assertThat(recordQuestionService.generateAutomatically(completed.userId(), DAY)).isFalse();
        assertThat(recordQuestionService.generateAutomatically(empty.userId(), DAY)).isFalse();

        verify(questionGenerator, never()).generate(any());
    }

    @Test
    void RC06_자동_질문이_AI_실패하면_질문을_만들지_않는다() throws Exception {
        Session me = register();
        createCompletedTask(me, "보고서", "2026-10-21");
        given(questionGenerator.generate(any())).willThrow(new CustomException(ErrorCode.AI_TIMEOUT));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> recordQuestionService.generateAutomatically(me.userId(), DAY))
                .isInstanceOf(CustomException.class);

        mockMvc.perform(get("/api/records/2026-10-21/questions").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.questions.length()").value(0));
    }

    // ---- helpers ----

    private ResultActions askQuestions(Session session, String date, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/records/" + date + "/questions").header("Authorization", session.bearer())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions getTasks(Session session, String date) throws Exception {
        return mockMvc.perform(get("/api/tasks").param("date", date).header("Authorization", session.bearer()));
    }

    private Map<String, Object> memo(Long taskId, String memo) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", taskId);
        map.put("memo", memo);
        return map;
    }

    private Map<String, Object> answer(Long questionId, String answer, boolean skipped) {
        Map<String, Object> map = new HashMap<>();
        map.put("questionId", questionId);
        map.put("answer", answer);
        map.put("skipped", skipped);
        return map;
    }

    // 질문은 완료한 할 일이나 회고가 있어야 만들어진다
    private Long createCompletedTask(Session session, String title, String date) throws Exception {
        Long id = createTask(session, title, date);
        mockMvc.perform(patch("/api/tasks/" + id).header("Authorization", session.bearer())
                        .contentType("application/json").content("{\"completed\":true}"))
                .andExpect(status().isOk());
        return id;
    }

    private Long createTask(Session session, String title, String date) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks").header("Authorization", session.bearer())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("title", title, "date", date))))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) data(result).get("id")).longValue();
    }

    private Session register() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        given(googleOidcClient.exchangeCodeForUserInfo(sub)).willReturn(
                new GoogleOidcClient.GoogleUserInfo(sub, "user-" + UUID.randomUUID() + "@example.com", true));
        MvcResult login = mockMvc.perform(post("/api/auth/login/google")
                        .contentType("application/json").content("{\"code\":\"" + sub + "\"}"))
                .andReturn();
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "signupToken", data(login).get("signupToken"), "nickname", "닉네임"))))
                .andReturn();
        Map<String, Object> body = data(registered);
        return new Session((String) body.get("accessToken"), ((Number) body.get("userId")).longValue());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
        return (Map<String, Object>) body.get("data");
    }

    private record Session(String accessToken, Long userId) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    // 테스트에서 "오늘"을 옮길 수 있는 Clock (Asia/Seoul)
    static class MutableClock extends Clock {
        private volatile Instant instant = Instant.now();

        void setToday(LocalDate today) {
            this.instant = today.atTime(10, 0).atZone(SEOUL).toInstant();
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
