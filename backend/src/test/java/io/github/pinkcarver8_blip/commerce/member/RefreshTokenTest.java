package io.github.pinkcarver8_blip.commerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** R1-01 갱신·회전·폐기(R1~R7, X2, E36, ADR-008 결정 3). 옛 토큰 재사용은 봐주기 없이 계열 전체 폐기. */
class RefreshTokenTest extends AuthTestSupport {

    private static final String EMAIL = "refresh@example.com";

    @Test
    void R1_01_R1_갱신하면_새_액세스_토큰과_새_리프레시_쿠키를_주고_옛_토큰은_쓴_것으로_남는다() throws Exception {
        Session first = registerMember(EMAIL, PASSWORD);

        MvcResult result = refresh(first.refreshToken()).andExpect(status().isOk()).andReturn();
        Session second = Session.of(result, json);

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotBlank();
        Map<String, Object> old = row(first.refreshToken());
        Map<String, Object> next = row(second.refreshToken());
        assertThat(old.get("used_at")).isNotNull();
        assertThat(next.get("used_at")).isNull();
        assertThat(next.get("family_id")).isEqualTo(old.get("family_id"));
        // 회전해도 계열 만료(로그인 후 14일)는 늘어나지 않는다.
        assertThat(next.get("expires_at")).isEqualTo(old.get("expires_at"));
    }

    @Test
    void R1_01_R2_요청_헤더가_없으면_거절되고_토큰은_회전하지_않는다() throws Exception {
        Session session = registerMember(EMAIL, PASSWORD);

        mvc.perform(
                        post("/api/auth/refresh")
                                .cookie(new Cookie("store_rt", session.refreshToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUESTED_WITH_REQUIRED"));
        mvc.perform(
                        post("/api/auth/refresh")
                                .header("X-Requested-With", "fetch")
                                .cookie(new Cookie("store_rt", session.refreshToken())))
                .andExpect(status().isForbidden());

        assertThat(row(session.refreshToken()).get("used_at")).isNull();
        refresh(session.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void R1_01_R3_쓴_토큰이_다시_오면_그_로그인_계열이_모두_폐기된다() throws Exception {
        Session first = registerMember(EMAIL, PASSWORD);
        Session second =
                Session.of(
                        refresh(first.refreshToken()).andExpect(status().isOk()).andReturn(), json);

        MvcResult reuse =
                refresh(first.refreshToken())
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("REFRESH_REJECTED"))
                        .andReturn();

        assertThat(reuse.getResponse().getHeader("Set-Cookie"))
                .startsWith("store_rt=;")
                .contains("Max-Age=0");
        refresh(second.refreshToken()).andExpect(status().isUnauthorized());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM member_refresh_token WHERE revoked_at IS NULL",
                                Integer.class))
                .isZero();
    }

    @Test
    void R1_01_R3_다른_로그인_계열은_폐기되지_않는다() throws Exception {
        Session phone = registerMember(EMAIL, PASSWORD);
        Session laptop =
                Session.of(
                        login(EMAIL, PASSWORD, "10.0.0.5").andExpect(status().isOk()).andReturn(),
                        json);
        refresh(phone.refreshToken()).andExpect(status().isOk());

        refresh(phone.refreshToken()).andExpect(status().isUnauthorized());

        refresh(laptop.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void R1_01_R4_같은_토큰으로_동시에_갱신하면_하나만_성공하고_계열이_폐기된다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                cleanState();
                Session session = registerMember(EMAIL, PASSWORD);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<MvcResult>> calls = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    calls.add(
                            pool.submit(
                                    () -> {
                                        start.await();
                                        return refresh(session.refreshToken()).andReturn();
                                    }));
                }
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                String winnerToken = null;
                for (Future<MvcResult> call : calls) {
                    MvcResult result = call.get();
                    statuses.add(result.getResponse().getStatus());
                    if (result.getResponse().getStatus() == 200) {
                        winnerToken = result.getResponse().getCookie("store_rt").getValue();
                    }
                }

                assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 401);
                // 이긴 쪽이 받은 새 토큰도 폐기됐다(봐주기 없음).
                refresh(winnerToken).andExpect(status().isUnauthorized());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void R1_01_R5_만료된_리프레시_토큰은_401이다() throws Exception {
        Session session = registerMember(EMAIL, PASSWORD);
        jdbc.update(
                "UPDATE member_refresh_token SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND");

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void R1_01_R6_DB에는_리프레시_토큰의_SHA_256_해시만_있다() throws Exception {
        Session session = registerMember(EMAIL, PASSWORD);

        Map<String, Object> stored = row(session.refreshToken());
        assertThat((byte[]) stored.get("token_hash")).hasSize(32);
        assertThat(
                        jdbc.queryForList("SELECT * FROM member_refresh_token").stream()
                                .flatMap(r -> r.values().stream())
                                .map(String::valueOf))
                .noneMatch(value -> value.contains(session.refreshToken()));
    }

    @Test
    void R1_01_R7_로그아웃하면_계열이_폐기되고_쿠키가_지워진다() throws Exception {
        Session session = registerMember(EMAIL, PASSWORD);

        MvcResult result =
                mvc.perform(
                                post("/api/auth/logout")
                                        .header("X-Requested-With", "XMLHttpRequest")
                                        .cookie(new Cookie("store_rt", session.refreshToken())))
                        .andExpect(status().isNoContent())
                        .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .startsWith("store_rt=;")
                .contains("Max-Age=0")
                .contains("Path=/api/auth");
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
        // 쿠키 없이 로그아웃해도 같은 답이다.
        mvc.perform(post("/api/auth/logout").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());
    }

    @Test
    void R1_01_R_쿠키가_없거나_엉터리면_401이다() throws Exception {
        mvc.perform(post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
        refresh("A".repeat(43)).andExpect(status().isUnauthorized());
    }

    @Test
    void R1_01_X2_리프레시_쿠키는_인증_주소에만_붙는다() throws Exception {
        Session session = registerMember(EMAIL, PASSWORD);

        assertThat(session.setCookie()).contains("Path=/api/auth").doesNotContain("Domain=");
    }

    private Map<String, Object> row(String refreshToken) throws Exception {
        byte[] hash =
                MessageDigest.getInstance("SHA-256")
                        .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
        return jdbc.queryForMap("SELECT * FROM member_refresh_token WHERE token_hash = ?", hash);
    }
}
