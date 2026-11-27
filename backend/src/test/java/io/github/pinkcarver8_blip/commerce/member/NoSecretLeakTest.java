package io.github.pinkcarver8_blip.commerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * R1-01 X1: 응답 본문과 서버 기록에 비밀번호·토큰 값이 남지 않는다(A5-10, ADR-008 보안·개인정보 영향).
 *
 * <p>웹·보안·SQL 기록을 가장 자세하게(DEBUG) 켜고 가입부터 로그아웃까지 한 바퀴 돈다. 자세한 기록에서도 값이 안 보여야 한다.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
        properties = {
            "logging.level.org.springframework.web=DEBUG",
            "logging.level.org.springframework.security=DEBUG",
            "logging.level.org.hibernate.SQL=DEBUG",
            "logging.level.io.github.pinkcarver8_blip=DEBUG"
        })
class NoSecretLeakTest extends AuthTestSupport {

    @Test
    void R1_01_X1_응답과_기록에_비밀번호와_토큰이_남지_않는다(CapturedOutput output) throws Exception {
        String email = "leak@example.com";
        String password = "very private words";
        List<String> bodies = new ArrayList<>();

        MvcResult signup =
                requestSignup(email, password).andExpect(status().isAccepted()).andReturn();
        bodies.add(text(signup));
        String verification = lastVerificationToken(email);
        MvcResult wrongComplete = completeSignup(verification, "not my password").andReturn();
        bodies.add(text(wrongComplete));
        MvcResult complete =
                completeSignup(verification, password).andExpect(status().isOk()).andReturn();
        Session session = Session.of(complete, json);
        bodies.add(text(login(email, "wrong words here", "10.0.0.1").andReturn()));
        MvcResult me =
                mvc.perform(
                                get("/api/members/me")
                                        .header("Authorization", "Bearer " + session.accessToken()))
                        .andExpect(status().isOk())
                        .andReturn();
        bodies.add(text(me));
        MvcResult refreshed =
                refresh(session.refreshToken()).andExpect(status().isOk()).andReturn();
        Session next = Session.of(refreshed, json);
        bodies.add(text(refresh(session.refreshToken()).andReturn()));

        List<String> secrets =
                List.of(
                        password,
                        "not my password",
                        "wrong words here",
                        verification,
                        session.accessToken(),
                        session.refreshToken(),
                        next.accessToken(),
                        next.refreshToken());
        String logs = output.getAll();
        assertThat(logs).as("기록을 실제로 받았다").contains("/api/auth/login");
        for (String secret : secrets) {
            assertThat(logs).as("기록에 값이 없다").doesNotContain(secret);
        }
        for (String body : bodies) {
            assertThat(body)
                    .doesNotContain(password)
                    .doesNotContain("Exception")
                    .doesNotContain("at io.");
        }
    }

    private static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
