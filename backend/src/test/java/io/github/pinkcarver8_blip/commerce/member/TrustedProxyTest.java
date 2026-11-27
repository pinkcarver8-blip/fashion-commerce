package io.github.pinkcarver8_blip.commerce.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * R1-01 L5: 로그인 제한의 접속 IP는 신뢰 프록시(web 컨테이너)에서 온 요청에서만 {@code X-Forwarded-For}를 쓴다(ADR-008 결정 7,
 * 2026-11-28 사용자 결정 ③). 실제 Tomcat을 띄워 HTTP로 보낸다(MockMvc는 Tomcat의 IP 처리를 거치지 않는다).
 */
class TrustedProxyTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** 기본 설정: 신뢰 프록시는 web 컨테이너 주소(172.29.250.10)뿐이다. 시험 요청은 127.0.0.1에서 오므로 믿지 않는다. */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class 신뢰하지_않는_곳에서_온_요청 extends AuthTestSupport {

        @LocalServerPort int port;

        @Test
        void R1_01_L5_IP_헤더를_바꿔_가며_보내도_같은_IP로_세어_막힌다() throws Exception {
            registerMember("proxy@example.com", PASSWORD);

            for (int i = 0; i < 5; i++) {
                assertThat(httpLogin(port, "proxy@example.com", "wrong password", "203.0.113." + i))
                        .isEqualTo(401);
            }

            assertThat(httpLogin(port, "proxy@example.com", PASSWORD, "198.51.100.7"))
                    .as("헤더 값이 달라도 실제 접속 IP(127.0.0.1)로 세어 막힌다")
                    .isEqualTo(429);
            assertThat(redis.keys("member:login-fail:*"))
                    .allMatch(key -> key.endsWith(":127.0.0.1"));
        }
    }

    /** 시험용으로 127.0.0.1을 신뢰 프록시로 두면 X-Forwarded-For가 접속 IP가 된다. 설정 형식(CIDR)이 실제로 먹는지 본다. */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    // 공통 바탕의 환경변수(@DynamicPropertySource)가 더 우선이라 설정 이름 자체를 덮는다.
    @TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=127.0.0.1/32")
    class 신뢰_프록시에서_온_요청 extends AuthTestSupport {

        @LocalServerPort int port;

        @Test
        void R1_01_L5_신뢰_프록시가_넣은_IP로_센다() throws Exception {
            registerMember("proxy@example.com", PASSWORD);

            for (int i = 0; i < 5; i++) {
                assertThat(httpLogin(port, "proxy@example.com", "wrong password", "203.0.113.1"))
                        .isEqualTo(401);
            }

            assertThat(httpLogin(port, "proxy@example.com", PASSWORD, "203.0.113.1"))
                    .isEqualTo(429);
            assertThat(httpLogin(port, "proxy@example.com", PASSWORD, "203.0.113.2"))
                    .isEqualTo(200);
        }
    }

    private static int httpLogin(int port, String email, String password, String forwardedFor)
            throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("X-Forwarded-For", forwardedFor)
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"email\":\""
                                                + email
                                                + "\",\"password\":\""
                                                + password
                                                + "\"}"))
                        .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
