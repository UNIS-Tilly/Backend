package com.unis.tilly.auth.client;

import com.unis.tilly.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 구글에 실제로 호출하지 않는다 (RULES 4장) — MockRestServiceServer로 대체한다.
class GoogleOidcClientTest {

    private static final String DISCOVERY_URL = "https://accounts.google.com/.well-known/openid-configuration";
    private static final String DISCOVERY_JSON = """
            {"token_endpoint":"https://oauth2.googleapis.com/token","userinfo_endpoint":"https://openidconnect.googleapis.com/v1/userinfo"}
            """;

    private MockRestServiceServer server;
    private GoogleOidcClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleOidcClient(builder, "client-id", "client-secret", "http://localhost:3000/auth/callback/google");
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
    void discovery_문서_조회가_실패하면_GOOGLE_AUTH_FAILED를_던진다() {
        server.expect(requestTo(DISCOVERY_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeCodeForUserInfo("any-code"))
                .isInstanceOf(CustomException.class);
    }
}
