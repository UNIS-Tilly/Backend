package com.unis.tilly.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 구글 OIDC Discovery(https://accounts.google.com/.well-known/openid-configuration)로
// token/userinfo 엔드포인트를 조회해 쓴다. 엔드포인트 URL을 코드에 직접 하드코딩하지 않는다.
@Component
public class GoogleOidcClient {

    private static final String DISCOVERY_URL = "https://accounts.google.com/.well-known/openid-configuration";

    private final RestClient restClient;
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
        } catch (RestClientException e) {
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
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
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
