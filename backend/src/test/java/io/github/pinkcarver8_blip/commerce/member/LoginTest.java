package io.github.pinkcarver8_blip.commerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** R1-01 로그인(L1~L4, ADR-008 결정 2·7, A5-9 S6). L5(신뢰 프록시)는 실제 Tomcat이 필요해 TrustedProxyTest에 있다. */
class LoginTest extends AuthTestSupport {

    private static final String EMAIL = "login@example.com";

    @Test
    void R1_01_L1_로그인하면_15분짜리_고객_토큰과_보호된_리프레시_쿠키를_준다() throws Exception {
        registerMember(EMAIL, PASSWORD);

        MvcResult result =
                login(EMAIL, PASSWORD, "10.0.0.1")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tokenType").value("Bearer"))
                        .andExpect(jsonPath("$.expiresIn").value(900))
                        .andReturn();

        JsonNode claims = claimsOf(body(result).get("accessToken").asString());
        // 받는 쪽이 하나면 문자열 하나로 쓴다(RFC 7519 4.1.3은 문자열·배열 모두 허용).
        JsonNode audience = claims.get("aud");
        assertThat(audience.isArray() ? audience.get(0).asString() : audience.asString())
                .isEqualTo("store");
        assertThat(claims.get("iss").asString()).isEqualTo("fashion-commerce");
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(900);
        String publicId =
                jdbc.queryForObject(
                        "SELECT BIN_TO_UUID(public_id) FROM member WHERE email = ?",
                        String.class,
                        EMAIL);
        assertThat(claims.get("sub").asString()).isEqualTo(publicId);
        assertThat(claims.toString()).doesNotContain(EMAIL);

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith("store_rt=")
                .contains("Path=/api/auth")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
        // 14일(1,209,600초). 발급과 응답 사이에 흐른 시간만큼 조금 짧을 수 있다.
        long maxAge = Long.parseLong(setCookie.replaceAll(".*Max-Age=([0-9]+).*", "$1"));
        assertThat(maxAge).isBetween(1_209_600L - 60, 1_209_600L);
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void R1_01_L2_틀린_비밀번호와_없는_이메일은_같은_답이고_시간도_비슷하다() throws Exception {
        registerMember(EMAIL, PASSWORD);

        List<Long> wrongPassword = new ArrayList<>();
        List<Long> unknownEmail = new ArrayList<>();
        String wrongBody = null;
        String unknownBody = null;
        for (int i = 0; i < 6; i++) {
            // 조합마다 IP를 바꿔 횟수 제한에 걸리지 않게 한다.
            long start = System.nanoTime();
            MvcResult wrong =
                    login(EMAIL, "wrong password " + i, "10.1.0." + i)
                            .andExpect(status().isUnauthorized())
                            .andReturn();
            wrongPassword.add(System.nanoTime() - start);
            start = System.nanoTime();
            MvcResult unknown =
                    login("nobody" + i + "@example.com", "wrong password " + i, "10.2.0." + i)
                            .andExpect(status().isUnauthorized())
                            .andReturn();
            unknownEmail.add(System.nanoTime() - start);
            wrongBody = wrong.getResponse().getContentAsString(StandardCharsets.UTF_8);
            unknownBody = unknown.getResponse().getContentAsString(StandardCharsets.UTF_8);
        }

        assertThat(unknownBody).isEqualTo(wrongBody).contains("아이디 또는 비밀번호가 맞지 않습니다");
        // 없는 이메일도 bcrypt 비교를 한다. 비교를 건너뛰면 몇 ms로 끝나 절반에 못 미친다.
        assertThat(median(unknownEmail)).isGreaterThan(median(wrongPassword) / 2);
    }

    @Test
    void R1_01_L3_같은_계정과_IP로_5번_틀리면_맞는_비밀번호도_15분_막고_다른_IP는_된다() throws Exception {
        registerMember(EMAIL, PASSWORD);

        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong password " + i, "10.0.0.1").andExpect(status().isUnauthorized());
        }

        login(EMAIL, PASSWORD, "10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_TEMPORARILY_BLOCKED"));
        login(EMAIL, PASSWORD, "10.0.0.2").andExpect(status().isOk());

        String key = onlyKey("member:login-fail:*:10.0.0.1");
        Long ttlSeconds = redis.getExpire(key);
        assertThat(ttlSeconds).isBetween(Duration.ofMinutes(14).toSeconds(), 900L);
        // 15분이 지나 열쇠가 사라진 상황.
        redis.delete(key);
        login(EMAIL, PASSWORD, "10.0.0.1").andExpect(status().isOk());
    }

    @Test
    void R1_01_L3_동시에_보낸_틀린_시도도_5번까지만_비교한다() throws Exception {
        registerMember(EMAIL, PASSWORD);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int n = i;
                calls.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return login(EMAIL, "wrong password " + n, "10.0.0.9")
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> call : calls) {
                statuses.add(call.get());
            }

            // 비교 전에 칸을 차지하므로, 한꺼번에 20개를 보내도 비밀번호 비교(401)는 5번뿐이다.
            assertThat(statuses.stream().filter(code -> code == 401).count()).isEqualTo(5);
            assertThat(statuses.stream().filter(code -> code == 429).count()).isEqualTo(15);
        } finally {
            pool.shutdownNow();
        }
        login(EMAIL, PASSWORD, "10.0.0.9").andExpect(status().isTooManyRequests());
    }

    @Test
    void R1_01_L3_없는_이메일도_똑같이_막혀_회원_여부가_드러나지_않는다() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("ghost@example.com", "wrong password " + i, "10.0.0.1")
                    .andExpect(status().isUnauthorized());
        }

        login("ghost@example.com", "wrong password x", "10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_TEMPORARILY_BLOCKED"));
    }

    @Test
    void R1_01_L4_로그인에_성공하면_그_조합의_실패_횟수가_지워진다() throws Exception {
        registerMember(EMAIL, PASSWORD);
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "wrong password " + i, "10.0.0.1").andExpect(status().isUnauthorized());
        }

        login(EMAIL, PASSWORD, "10.0.0.1").andExpect(status().isOk());

        assertThat(redis.keys("member:login-fail:*:10.0.0.1")).isEmpty();
        for (int i = 0; i < 4; i++) {
            login(EMAIL, "wrong password " + i, "10.0.0.1").andExpect(status().isUnauthorized());
        }
        login(EMAIL, PASSWORD, "10.0.0.1").andExpect(status().isOk());
    }

    @Test
    void R1_01_L_72바이트를_넘는_비밀번호로_로그인하면_오류가_아니라_실패로_답한다() throws Exception {
        registerMember(EMAIL, PASSWORD);

        login(EMAIL, "가".repeat(30), "10.0.0.1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void R1_01_L_Redis_열쇠에는_이메일을_그대로_두지_않는다() throws Exception {
        login(EMAIL, "wrong password", "10.0.0.1").andExpect(status().isUnauthorized());

        Set<String> keys = redis.keys("member:*");
        assertThat(keys).isNotEmpty().noneMatch(key -> key.contains("login@"));
        assertThat(keys).allMatch(key -> key.matches("member:[a-z-]+:[0-9a-f]{64}(:.*)?"));
    }

    static JsonNode claimsOf(String jwt) {
        String payload = jwt.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(decoded);
    }

    private String onlyKey(String pattern) {
        Set<String> keys = redis.keys(pattern);
        assertThat(keys).hasSize(1);
        return keys.iterator().next();
    }

    private static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
