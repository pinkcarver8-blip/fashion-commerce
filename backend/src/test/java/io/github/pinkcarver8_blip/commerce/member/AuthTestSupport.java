package io.github.pinkcarver8_blip.commerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * R1-01 인증 시험의 공통 바탕. 실제 MySQL·Redis 컨테이너(compose와 같은 판·지문, A5-12)에 붙는다(A4: Testcontainers).
 *
 * <p>메일은 실제로 보내지 않고 시험용 메일 보내기 도구가 받아 둔다. 컨테이너는 시험 클래스끼리 한 번만 띄운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthTestSupport.TestMail.class)
public abstract class AuthTestSupport {

    static final DockerImageName MYSQL_IMAGE =
            DockerImageName.parse(
                            "mysql:8.4.11@sha256:0f51e63e537a1f176423fa03ef559aabe6fa4372a53160a44bc7e75da7d15364")
                    .asCompatibleSubstituteFor("mysql");

    static final DockerImageName REDIS_IMAGE =
            DockerImageName.parse(
                    "redis:8.10.3-alpine@sha256:7958cfbb9d152f87dadfbc89f1b0b15938f0b81ff8982feb327476f4911935c3");

    /** 시험 전용 값이다. 운영 키가 아니다(A5-10). */
    static final String REDIS_TEST_PASSWORD = "redis-test-only";

    /** 시험 전용 서명 키(32바이트). 저장소에 비밀값을 두지 않으려고 실행 때 만든다. */
    public static final String STORE_TEST_KEY =
            Base64.getEncoder()
                    .encodeToString(
                            "test-only-store-key-not-a-secret!".getBytes(StandardCharsets.UTF_8));

    /** web 컨테이너의 고정 내부 주소(infra/docker-compose.yml). 시험의 요청(127.0.0.1)은 이 주소가 아니라 믿지 않는다. */
    static final String TRUSTED_PROXY = "172.29.250.10/32";

    static final MySQLContainer MYSQL = new MySQLContainer(MYSQL_IMAGE);

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(REDIS_IMAGE)
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost);
        registry.add("DB_PORT", () -> MYSQL.getMappedPort(3306));
        registry.add("DB_NAME", MYSQL::getDatabaseName);
        registry.add("DB_USERNAME", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("REDIS_HOST", REDIS::getHost);
        registry.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
        registry.add("REDIS_PASSWORD", () -> REDIS_TEST_PASSWORD);
        registry.add("STORE_JWT_SECRET", () -> STORE_TEST_KEY);
        registry.add("TRUSTED_PROXY_CIDR", () -> TRUSTED_PROXY);
        registry.add("MAIL_HOST", () -> "localhost");
    }

    protected static final String PASSWORD = "blue coffee rain";

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected CapturingMailSender mail;
    @Autowired protected JsonMapper json;

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM member_refresh_token");
        jdbc.update("DELETE FROM member_signup");
        jdbc.update("DELETE FROM member");
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        mail.clear();
        mail.failNext(false);
    }

    // ---- 요청 도우미 ----

    /** 브라우저 앱이 보내는 모양: JSON 본문 + X-Requested-With. */
    protected static MockHttpServletRequestBuilder authPost(String path, Object body) {
        return post(path)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body));
    }

    protected static MockHttpServletRequestBuilder fromIp(
            MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(
                request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    protected ResultActions requestSignup(String email, String password) throws Exception {
        return mvc.perform(
                authPost(
                        "/api/auth/signup",
                        Map.of("email", email, "password", password, "name", "홍길동")));
    }

    /** 가장 최근에 그 주소로 간 인증 메일의 토큰. */
    protected String lastVerificationToken(String email) {
        Pattern link = Pattern.compile("/signup/complete#token=([A-Za-z0-9_-]{43})");
        for (int i = mail.sent().size() - 1; i >= 0; i--) {
            SimpleMailMessage message = mail.sent().get(i);
            Matcher matcher = link.matcher(message.getText());
            if (List.of(message.getTo()).contains(email) && matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("인증 메일이 없다: " + email);
    }

    protected ResultActions completeSignup(String token, String password) throws Exception {
        return mvc.perform(
                authPost(
                        "/api/auth/signup/complete", Map.of("token", token, "password", password)));
    }

    /** 가입 신청 → 메일 링크 → 완료까지 해서 로그인된 상태를 돌려준다. */
    protected Session registerMember(String email, String password) throws Exception {
        requestSignup(email, password).andExpect(status().isAccepted());
        MvcResult result =
                completeSignup(lastVerificationToken(email.strip().toLowerCase()), password)
                        .andExpect(status().isOk())
                        .andReturn();
        return Session.of(result, json);
    }

    protected ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(
                fromIp(
                        authPost("/api/auth/login", Map.of("email", email, "password", password)),
                        ip));
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(
                post("/api/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("store_rt", refreshToken)));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String toJson(Object body) {
        return JsonMapper.builder().build().writeValueAsString(body);
    }

    /** 로그인 결과: 본문의 액세스 토큰과 쿠키의 리프레시 토큰. */
    protected record Session(String accessToken, String refreshToken, String setCookie) {

        static Session of(MvcResult result, JsonMapper json) throws Exception {
            JsonNode node =
                    json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            Cookie cookie = result.getResponse().getCookie("store_rt");
            assertThat(cookie).as("리프레시 쿠키").isNotNull();
            return new Session(
                    node.get("accessToken").asString(),
                    cookie.getValue(),
                    result.getResponse().getHeader("Set-Cookie"));
        }
    }

    /** 실제로 보내지 않고 받아 두는 메일 보내기 도구. 실패를 흉내 낼 수 있다. */
    public static class CapturingMailSender extends JavaMailSenderImpl {

        private final List<SimpleMailMessage> sent = new ArrayList<>();
        private volatile boolean failNext;

        @Override
        protected synchronized void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            if (failNext) {
                throw new MailSendException("시험: 메일 서버 없음");
            }
            for (Object original : originalMessages) {
                sent.add((SimpleMailMessage) original);
            }
        }

        public synchronized List<SimpleMailMessage> sent() {
            return List.copyOf(sent);
        }

        public synchronized List<SimpleMailMessage> sentTo(String email) {
            return sent.stream().filter(m -> List.of(m.getTo()).contains(email)).toList();
        }

        synchronized void clear() {
            sent.clear();
        }

        void failNext(boolean fail) {
            this.failNext = fail;
        }
    }

    @TestConfiguration
    static class TestMail {

        @Bean
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }
}
