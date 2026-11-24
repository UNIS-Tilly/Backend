package com.unis.tilly.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.unis.tilly.ai.prompt.Prompt;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Gemini REST 호출을 로컬 가짜 서버로 확인한다 (외부 네트워크 호출 없음)
class GeminiClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<Recorded> requests = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private GeminiProperties properties;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            synchronized (requests) {
                requests.add(new Recorded(exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            }
            Reply reply = replies.isEmpty() ? new Reply(500, "{}", 0) : replies.poll();
            if (reply.delayMillis() > 0) {
                try {
                    Thread.sleep(reply.delayMillis());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            } catch (IOException ignored) {
                // 시간 초과 테스트에서 클라이언트가 먼저 끊을 수 있다
            }
        });
        server.start();

        properties = GeminiPropertiesTest.properties("test-model", List.of("test-model"));
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setQuestionTimeout(Duration.ofMillis(500));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 키_헤더와_경로_JSON_응답_설정으로_호출하고_응답_JSON을_꺼낸다() throws Exception {
        replies.add(ok("{\"questions\":[\"오늘 가장 어려웠던 점은?\"]}"));

        JsonNode result = client().generate(prompt(), GeminiClient.CallKind.QUESTION);

        assertThat(result.path("questions").get(0).asText()).isEqualTo("오늘 가장 어려웠던 점은?");
        Recorded request = requests.get(0);
        assertThat(request.path()).isEqualTo("/v1beta/models/test-model:generateContent");
        assertThat(request.apiKey()).isEqualTo("secret-key");
        JsonNode body = MAPPER.readTree(request.body());
        assertThat(body.path("systemInstruction").path("parts").get(0).path("text").asText()).isEqualTo("지시문");
        assertThat(body.path("contents").get(0).path("parts").get(0).path("text").asText()).isEqualTo("사용자 데이터");
        assertThat(body.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(body.path("generationConfig").path("responseJsonSchema").path("type").asText()).isEqualTo("object");
    }

    @Test
    void 호출_한도_초과_429는_재시도해서_성공할_수_있다() {
        replies.add(new Reply(429, "{}", 0));
        replies.add(new Reply(503, "{}", 0));
        replies.add(ok("{\"questions\":[\"질문\"]}"));

        JsonNode result = client().generate(prompt(), GeminiClient.CallKind.QUESTION);

        assertThat(result.path("questions").size()).isEqualTo(1);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void 재시도를_다_써도_429면_AI_GENERATION_FAILED() {
        for (int i = 0; i < 5; i++) {
            replies.add(new Reply(429, "{}", 0));
        }

        assertError(ErrorCode.AI_GENERATION_FAILED);
        assertThat(calls.get()).isEqualTo(3); // 첫 호출 + 재시도 2번
    }

    @Test
    void 요청_오류_400은_재시도하지_않고_AI_GENERATION_FAILED() {
        replies.add(new Reply(400, "{}", 0));

        assertError(ErrorCode.AI_GENERATION_FAILED);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void 응답이_늦으면_AI_TIMEOUT() {
        replies.add(new Reply(200, wrap("{}"), 2_000));

        assertError(ErrorCode.AI_TIMEOUT);
    }

    @Test
    void 차단되거나_정상_종료가_아니거나_JSON이_아니면_다시_생성_대상() {
        replies.add(new Reply(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", 0));
        assertThatThrownBy(() -> client().generate(prompt(), GeminiClient.CallKind.QUESTION))
                .isInstanceOf(AiResponseInvalidException.class);

        replies.add(new Reply(200,
                "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"{\"}]}}]}", 0));
        assertThatThrownBy(() -> client().generate(prompt(), GeminiClient.CallKind.QUESTION))
                .isInstanceOf(AiResponseInvalidException.class);

        replies.add(ok("JSON 아님"));
        assertThatThrownBy(() -> client().generate(prompt(), GeminiClient.CallKind.QUESTION))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    private void assertError(ErrorCode errorCode) {
        assertThatThrownBy(() -> client().generate(prompt(), GeminiClient.CallKind.QUESTION))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(errorCode));
    }

    private GeminiClient client() {
        return new GeminiClient(RestClient.builder(), properties);
    }

    private Prompt prompt() {
        try {
            return new Prompt("지시문", "사용자 데이터", MAPPER.readTree("{\"type\":\"object\"}"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Reply ok(String innerJson) {
        return new Reply(200, wrap(innerJson), 0);
    }

    // 실제 응답 구조: candidates[0].content.parts[].text 에 JSON 문자열, finishReason STOP
    private static String wrap(String innerJson) {
        try {
            return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"role\":\"model\",\"parts\":[{\"text\":"
                    + MAPPER.writeValueAsString(innerJson) + "}]}}]}";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Reply(int status, String body, long delayMillis) {
    }

    private record Recorded(String path, String apiKey, String body) {
    }
}
