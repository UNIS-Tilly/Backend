package com.unis.tilly.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 구글 OIDC Discovery(https://accounts.google.com/.well-known/openid-configuration)로
// token/userinfo 엔드포인트를 조회해 쓴다. 엔드포인트 URL을 코드에 직접 하드코딩하지 않는다.
@Slf4j
@Component
public class GoogleOidcClient {

    private static final String DISCOVERY_URL = "https://accounts.google.com/.well-known/openid-configuration";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    private volatile DiscoveryDocument cachedDiscovery;

    public GoogleOidcClient(RestClient.Builder restClientBuilder,
                             @Value("${google.client-id}") String clientId,
                             @Value("${google.client-secret}") String clientSecret,
                             @Value("${google.redirect-uri}") String redirectUri) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public GoogleUserInfo exchangeCodeForUserInfo(String code) {
        DiscoveryDocument discovery = discoveryDocument();
        TokenResponse tokenResponse = exchangeToken(discovery.tokenEndpoint(), code);
        return fetchUserInfo(discovery.userinfoEndpoint(), tokenResponse.accessToken());
    }

    private DiscoveryDocument discoveryDocument() {
        DiscoveryDocument cached = cachedDiscovery;
        if (cached != null) {
            return cached;
        }
        try {
            DiscoveryDocument fetched = restClient.get()
                    .uri(DISCOVERY_URL)
                    .retrieve()
                    .body(DiscoveryDocument.class);
            cachedDiscovery = fetched;
            return fetched;
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private TokenResponse exchangeToken(String tokenEndpoint, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        try {
            return restClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (HttpStatusCodeException e) {
            logGoogleFailure("토큰 교환", e);
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        } catch (RestClientException e) {
            logGoogleFailure("토큰 교환", e);
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private GoogleUserInfo fetchUserInfo(String userinfoEndpoint, String accessToken) {
        try {
            return restClient.get()
                    .uri(userinfoEndpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserInfo.class);
        } catch (HttpStatusCodeException e) {
            logGoogleFailure("userinfo 조회", e);
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        } catch (RestClientException e) {
            logGoogleFailure("userinfo 조회", e);
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    // 인가 코드·client secret·토큰·이메일은 절대 로그에 남기지 않는다.
    private void logGoogleFailure(String stage, HttpStatusCodeException e) {
        log.warn("구글 {} 실패: status={}, {}, redirect_uri={}, client_id_prefix={}",
                stage, e.getStatusCode().value(), extractGoogleError(e.getResponseBodyAsString()),
                redirectUri, clientIdPrefix());
    }

    private void logGoogleFailure(String stage, RestClientException e) {
        log.warn("구글 {} 요청 실패(응답 없음): 원인={}, redirect_uri={}, client_id_prefix={}",
                stage, e.getClass().getSimpleName(), redirectUri, clientIdPrefix());
    }

    private String extractGoogleError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "error=(본문 없음)";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorNode = root.get("error");
            String error = errorNode == null ? null : (errorNode.isTextual() ? errorNode.asText() : errorNode.toString());
            JsonNode descriptionNode = root.get("error_description");
            String description = descriptionNode == null ? null : descriptionNode.asText();
            return "error=" + error + ", error_description=" + description;
        } catch (Exception parseException) {
            return "error=(본문 파싱 실패)";
        }
    }

    private String clientIdPrefix() {
        if (clientId == null) {
            return null;
        }
        return clientId.length() <= 8 ? clientId : clientId.substring(0, 8);
    }

    record DiscoveryDocument(
            @JsonProperty("token_endpoint") String tokenEndpoint,
            @JsonProperty("userinfo_endpoint") String userinfoEndpoint) {
    }

    record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    public record GoogleUserInfo(
            String sub,
            String email,
            @JsonProperty("email_verified") Boolean emailVerified) {
    }
}
