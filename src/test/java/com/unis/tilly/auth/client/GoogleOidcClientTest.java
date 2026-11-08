package com.unis.tilly.auth.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.unis.tilly.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 구글에 실제로 호출하지 않는다 (RULES 4장) — MockRestServiceServer로 대체한다.
class GoogleOidcClientTest {

    private static final String DISCOVERY_URL = "https://accounts.google.com/.well-known/openid-configuration";
    private static final String DISCOVERY_JSON = """
            {"token_endpoint":"https://oauth2.googleapis.com/token","userinfo_endpoint":"https://openidconnect.googleapis.com/v1/userinfo"}
            """;

    private static final String REDIRECT_URI = "http://localhost:3000/auth/callback/google";
    private static final String CLIENT_ID = "client-id-1234567890";
    private static final String CLIENT_SECRET = "super-secret-value";

    private MockRestServiceServer server;
    private GoogleOidcClient client;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleOidcClient(builder, CLIENT_ID, CLIENT_SECRET, REDIRECT_URI);

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(GoogleOidcClient.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(GoogleOidcClient.class)).detachAppender(logAppender);
    }

    @Test
    void 인가코드를_구글_userinfo로_교환한다() {
        server.expect(requestTo(DISCOVERY_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(DISCOVERY_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"access_token\":\"google-access-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"sub\":\"1234567890\",\"email\":\"user@example.com\",\"email_verified\":true}",
                        MediaType.APPLICATION_JSON));

        GoogleOidcClient.GoogleUserInfo info = client.exchangeCodeForUserInfo("auth-code");

        assertThat(info.sub()).isEqualTo("1234567890");
        assertThat(info.email()).isEqualTo("user@example.com");
        assertThat(info.emailVerified()).isTrue();

        server.verify();
    }

    @Test
    void 토큰_교환이_실패하면_GOOGLE_AUTH_FAILED를_던진다() {
        server.expect(requestTo(DISCOVERY_URL))
                .andRespond(withSuccess(DISCOVERY_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("bad-code"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 토큰_교환_실패시_구글의_error와_error_description을_WARN_로그로_남기고_민감값은_남기지_않는다() {
        String authCode = "secret-auth-code-xyz";
        server.expect(requestTo(DISCOVERY_URL))
                .andRespond(withSuccess(DISCOVERY_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"Malformed auth code.\"}"));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo(authCode))
                .isInstanceOf(CustomException.class);

        List<ILoggingEvent> warnLogs = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .toList();
        assertThat(warnLogs).hasSize(1);
        String message = warnLogs.get(0).getFormattedMessage();
        assertThat(message).contains("400");
        assertThat(message).contains("invalid_grant");
        assertThat(message).contains("Malformed auth code.");
        assertThat(message).contains(REDIRECT_URI);
        assertThat(message).contains(CLIENT_ID.substring(0, 8));

        assertThat(message).doesNotContain(authCode);
        assertThat(message).doesNotContain(CLIENT_SECRET);
        assertThat(message).doesNotContain(CLIENT_ID.substring(8));
    }

    @Test
    void userinfo_조회_실패시_구글의_오류_본문을_WARN_로그로_남기고_이메일_토큰은_남기지_않는다() {
        String accessToken = "google-access-token-secret";
        server.expect(requestTo(DISCOVERY_URL))
                .andRespond(withSuccess(DISCOVERY_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"" + accessToken + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":401,\"message\":\"Invalid Credentials\"}}"));

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("auth-code"))
                .isInstanceOf(CustomException.class);

        List<ILoggingEvent> warnLogs = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .toList();
        assertThat(warnLogs).hasSize(1);
        String message = warnLogs.get(0).getFormattedMessage();
        assertThat(message).contains("401");
        assertThat(message).contains("Invalid Credentials");
        assertThat(message).contains(REDIRECT_URI);
        assertThat(message).contains(CLIENT_ID.substring(0, 8));

        assertThat(message).doesNotContain(accessToken);
        assertThat(message).doesNotContain(CLIENT_SECRET);
    }

    @Test
    void discovery_문서_조회가_실패하면_GOOGLE_AUTH_FAILED를_던진다() {
        server.expect(requestTo(DISCOVERY_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("any-code"))
                .isInstanceOf(CustomException.class);
    }
}
