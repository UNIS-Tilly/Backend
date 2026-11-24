package com.unis.tilly.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unis.tilly.ai.prompt.Prompt;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

// Gemini REST 호출 (Spring이 직접 호출, 별도 AI 서버 없음).
// 요청 형식은 공식 API 레퍼런스(generateContent)로 확인: POST {base-url}/v1beta/models/{model}:generateContent,
// 키는 x-goog-api-key 헤더, JSON 응답은 generationConfig.responseMimeType + responseJsonSchema.
// 로그에는 상태 코드·종료 사유만 남기고 프롬프트·응답 본문·키는 남기지 않는다.
@Slf4j
@Component
public class GeminiClient {

    private static final String GENERATE_PATH = "/v1beta/models/{model}:generateContent";
    private static final String API_KEY_HEADER = "x-goog-api-key";

    public enum CallKind { QUESTION, WEEKLY_INSIGHT }

    private final GeminiProperties properties;
    private final RestClient questionClient;
    private final RestClient insightClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiClient(RestClient.Builder restClientBuilder, GeminiProperties properties) {
        this.properties = properties;
        this.questionClient = build(restClientBuilder, properties.getQuestionTimeout());
        this.insightClient = build(restClientBuilder, properties.getInsightTimeout());
    }

    // 응답 본문(JSON)을 돌려준다. 형식이 깨졌거나 차단되면 AiResponseInvalidException(다시 생성 대상)
    public JsonNode generate(Prompt prompt, CallKind kind) {
        String requestBody = requestBody(prompt);
        RestClient client = kind == CallKind.QUESTION ? questionClient : insightClient;

        for (int attempt = 0; ; attempt++) {
            try {
                String responseBody = client.post()
                        .uri(properties.getBaseUrl() + GENERATE_PATH, properties.getModel())
                        .header(API_KEY_HEADER, properties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
                return extractJson(responseBody);
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                if (retryable && attempt < properties.getMaxRetries()) {
                    log.warn("Gemini 호출 실패 status={} kind={} → 재시도 {}/{}", status, kind, attempt + 1,
                            properties.getMaxRetries());
                    sleep(properties.getRetryBackoff().multipliedBy(1L << attempt));
                    continue;
                }
                log.warn("Gemini 호출 실패 status={} kind={}", status, kind);
                throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
            } catch (ResourceAccessException e) {
                if (isTimeout(e)) {
                    log.warn("Gemini 응답 시간 초과 kind={}", kind);
                    throw new CustomException(ErrorCode.AI_TIMEOUT);
                }
                log.warn("Gemini 연결 실패 kind={} type={}", kind, e.getClass().getSimpleName());
                throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
            }
        }
    }

    private String requestBody(Prompt prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", prompt.systemInstruction());
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", prompt.userContent());
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseJsonSchema", prompt.responseSchema());
        return body.toString();
    }

    private JsonNode extractJson(String responseBody) {
        JsonNode root = readTree(responseBody, "응답 본문이 JSON이 아닙니다.");
        JsonNode blockReason = root.path("promptFeedback").path("blockReason");
        if (!blockReason.isMissingNode() && !blockReason.isNull()) {
            log.warn("Gemini 요청 차단 blockReason={}", blockReason.asText());
            throw new AiResponseInvalidException("요청이 차단되었습니다.");
        }
        JsonNode candidate = root.path("candidates").path(0);
        String finishReason = candidate.path("finishReason").asText("");
        if (candidate.isMissingNode() || !"STOP".equals(finishReason)) {
            log.warn("Gemini 응답 비정상 종료 finishReason={}", finishReason);
            throw new AiResponseInvalidException("응답이 정상 종료되지 않았습니다.");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            text.append(part.path("text").asText(""));
        }
        return readTree(text.toString(), "응답 내용이 JSON이 아닙니다.");
    }

    private JsonNode readTree(String json, String reason) {
        try {
            JsonNode node = objectMapper.readTree(json == null ? "" : json);
            if (node == null || node.isMissingNode()) {
                throw new AiResponseInvalidException(reason);
            }
            return node;
        } catch (IOException e) {
            throw new AiResponseInvalidException(reason);
        }
    }

    private boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    // HTTP/1.1로 고정한다. 2026-11-12 실제 호출 확인에서 같은 요청을 curl(HTTP/2)로 보내면 1.5초에 응답이 왔지만,
    // JDK HttpClient 기본값(HTTP/2)으로 보내면 30초 넘게 응답을 받지 못했다.
    private static RestClient build(RestClient.Builder builder, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return builder.clone().requestFactory(factory).build();
    }
}
