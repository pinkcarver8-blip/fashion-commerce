package io.github.pinkcarver8_blip.commerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;

/** R1-01 가입과 이메일 인증(G1~G5, V1~V5, 작업진행.md A5-9 S13). 이슈 #80의 시나리오. */
class SignupTest extends AuthTestSupport {

    @Test
    void R1_01_G1_V2_가입을_마치면_비밀번호는_bcrypt_해시로만_남고_바로_로그인된다() throws Exception {
        Session session = registerMember("kim@example.com", PASSWORD);

        assertThat(session.accessToken()).isNotBlank();
        String hash =
                jdbc.queryForObject(
                        "SELECT password_hash FROM member WHERE email = ?",
                        String.class,
                        "kim@example.com");
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
        assertThat(allTextIn("member")).noneMatch(value -> value.contains(PASSWORD));
        assertThat(allTextIn("member_signup")).isEmpty();
    }

    @Test
    void R1_01_G2_이메일은_대소문자를_가리지_않는다() throws Exception {
        registerMember("Kim@Example.COM", PASSWORD);

        requestSignup("kim@example.com", "another long password").andExpect(status().isAccepted());

        assertThat(lastMailTo("kim@example.com").getSubject()).contains("이미 가입된 이메일");
        assertThat(count("member")).isEqualTo(1);
        login("KIM@example.com", PASSWORD, "10.0.0.1").andExpect(status().isOk());
    }

    @Test
    void R1_01_G3_요청에_권한이나_회원번호를_끼워_넣어도_무시된다() throws Exception {
        mvc.perform(
                        authPost(
                                "/api/auth/signup",
                                Map.of(
                                        "email", "lee@example.com",
                                        "password", PASSWORD,
                                        "name", "이몽룡",
                                        "role", "ADMIN",
                                        "id", 1,
                                        "publicId", "00000000-0000-4000-8000-000000000000")))
                .andExpect(status().isAccepted());
        MvcResult result =
                completeSignup(lastVerificationToken("lee@example.com"), PASSWORD)
                        .andExpect(status().isOk())
                        .andReturn();

        String publicId =
                jdbc.queryForObject(
                        "SELECT BIN_TO_UUID(public_id) FROM member WHERE email = ?",
                        String.class,
                        "lee@example.com");
        assertThat(publicId).isNotEqualTo("00000000-0000-4000-8000-000000000000");
        List<String> columns =
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns"
                                + " WHERE table_schema = DATABASE() AND table_name = 'member'",
                        String.class);
        assertThat(columns).noneMatch(name -> name.toLowerCase().contains("role"));
        String accessToken = body(result).get("accessToken").asString();
        mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lee@example.com"));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "abc1234 | PASSWORD_TOO_SHORT",
                "가나다라마바사 | PASSWORD_TOO_SHORT",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaxyz9 | PASSWORD_TOO_LONG",
                "가나다라마바사아자차카타파하가나다라마바사아자차카 | PASSWORD_TOO_LONG",
                "password1 | PASSWORD_TOO_COMMON",
                "PassWord1 | PASSWORD_TOO_COMMON",
                "iloveyou | PASSWORD_TOO_COMMON",
                "my-Fashion-Commerce! | PASSWORD_CONTAINS_CONTEXT",
                "gildong.hong99 | PASSWORD_CONTAINS_CONTEXT",
                "kkkkkkkkkkk | PASSWORD_REPETITIVE",
                "lmnopqrs | PASSWORD_REPETITIVE",
                "srqponml | PASSWORD_REPETITIVE"
            })
    void R1_01_G4_비밀번호_규칙에_맞지_않으면_이유와_함께_거절된다(String password, String code) throws Exception {
        MvcResult result =
                requestSignup("gildong.hong@example.com", password)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(code))
                        .andReturn();

        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(text)
                .doesNotContain(password)
                .doesNotContain("Exception")
                .doesNotContain("trace");
        assertThat(mail.sent()).isEmpty();
        assertThat(count("member_signup")).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                // 8자, 섞기 없음: 받는다(문자 종류 섞기를 요구하지 않는다).
                "mintleaf",
                // 한글 24자 = 72바이트: 받는다(상한).
                "가나다라마바사아자차카타파하가나다라마바사아자차",
                // 띄어쓰기를 그대로 받는다.
                "blue coffee rain"
            })
    void R1_01_G4_규칙에_맞으면_받는다(String password) throws Exception {
        requestSignup("gildong.hong@example.com", password).andExpect(status().isAccepted());
        completeSignup(lastVerificationToken("gildong.hong@example.com"), password)
                .andExpect(status().isOk());
    }

    @Test
    void R1_01_G5_이미_회원인_이메일도_같은_답을_받고_계정은_바뀌지_않는다() throws Exception {
        registerMember("park@example.com", PASSWORD);
        String hashBefore = passwordHashOf("park@example.com");
        mail.clear();

        MvcResult existing =
                requestSignup("park@example.com", "attacker password 1")
                        .andExpect(status().isAccepted())
                        .andReturn();
        MvcResult fresh =
                requestSignup("new@example.com", "attacker password 1")
                        .andExpect(status().isAccepted())
                        .andReturn();

        assertThat(existing.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(fresh.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(lastMailTo("park@example.com").getSubject()).contains("이미 가입된 이메일");
        assertThat(lastMailTo("park@example.com").getText()).doesNotContain("#token=");
        assertThat(passwordHashOf("park@example.com")).isEqualTo(hashBefore);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM member_signup WHERE email = ?",
                                Integer.class,
                                "park@example.com"))
                .isZero();
    }

    @Test
    void R1_01_V1_인증_전에는_회원이_아니고_DB에는_토큰_해시만_있다() throws Exception {
        requestSignup("choi@example.com", PASSWORD).andExpect(status().isAccepted());

        String token = lastVerificationToken("choi@example.com");
        assertThat(lastMailTo("choi@example.com").getText()).contains("/signup/complete#token=");
        assertThat(count("member")).isZero();
        login("choi@example.com", PASSWORD, "10.0.0.1").andExpect(status().isUnauthorized());
        byte[] stored =
                jdbc.queryForObject(
                        "SELECT token_hash FROM member_signup WHERE email = ?",
                        byte[].class,
                        "choi@example.com");
        assertThat(stored)
                .hasSize(32)
                .isEqualTo(
                        MessageDigest.getInstance("SHA-256")
                                .digest(token.getBytes(StandardCharsets.UTF_8)));
        assertThat(allTextIn("member_signup")).noneMatch(value -> value.contains(token));
    }

    @Test
    void R1_01_V3_남이_먼저_신청해_둔_링크는_메일_주인의_비밀번호로_완성되지_않는다() throws Exception {
        // 공격자가 피해자 이메일로 먼저 신청한다.
        requestSignup("victim@example.com", "attacker secret pw").andExpect(status().isAccepted());
        String attackerLink = lastVerificationToken("victim@example.com");
        // 피해자가 직접 신청한다.
        requestSignup("victim@example.com", PASSWORD).andExpect(status().isAccepted());
        String victimLink = lastVerificationToken("victim@example.com");

        // 피해자가 공격자 메일의 링크를 눌러 자기 비밀번호를 넣어도 완성되지 않는다.
        completeSignup(attackerLink, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIGNUP_PASSWORD_MISMATCH"));
        assertThat(count("member")).isZero();

        // 자기 링크로는 완성되고, 그 뒤 공격자 링크는 쓸 수 없다.
        completeSignup(victimLink, PASSWORD).andExpect(status().isOk());
        completeSignup(attackerLink, "attacker secret pw")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIGNUP_LINK_INVALID"));
        assertThat(passwordMatchesStored("victim@example.com")).isTrue();
    }

    @Test
    void R1_01_V3_한_링크에_비밀번호를_5번_틀리면_그_링크는_쓸_수_없다() throws Exception {
        requestSignup("jung@example.com", PASSWORD).andExpect(status().isAccepted());
        String token = lastVerificationToken("jung@example.com");

        for (int i = 0; i < 5; i++) {
            completeSignup(token, "wrong password " + i)
                    .andExpect(jsonPath("$.code").value("SIGNUP_PASSWORD_MISMATCH"));
        }

        completeSignup(token, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIGNUP_LINK_INVALID"));
        assertThat(count("member")).isZero();
    }

    @Test
    void R1_01_V4_만료되거나_이미_쓴_링크와_엉터리_토큰은_거절된다() throws Exception {
        requestSignup("han@example.com", PASSWORD).andExpect(status().isAccepted());
        String token = lastVerificationToken("han@example.com");
        jdbc.update(
                "UPDATE member_signup SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND"
                        + " WHERE email = ?",
                "han@example.com");

        completeSignup(token, PASSWORD).andExpect(jsonPath("$.code").value("SIGNUP_LINK_INVALID"));

        requestSignup("han@example.com", PASSWORD).andExpect(status().isAccepted());
        String fresh = lastVerificationToken("han@example.com");
        completeSignup(fresh, PASSWORD).andExpect(status().isOk());
        completeSignup(fresh, PASSWORD).andExpect(jsonPath("$.code").value("SIGNUP_LINK_INVALID"));
        completeSignup("not-a-token", PASSWORD)
                .andExpect(jsonPath("$.code").value("SIGNUP_LINK_INVALID"));
    }

    @Test
    void R1_01_V5_같은_이메일로_가는_메일은_1시간에_5통까지이고_답은_같다() throws Exception {
        for (int i = 0; i < 7; i++) {
            requestSignup("spam@example.com", PASSWORD).andExpect(status().isAccepted());
        }

        assertThat(mail.sentTo("spam@example.com")).hasSize(5);
    }

    @Test
    void R1_01_V5_같은_IP의_가입_신청은_1시간에_20번까지다() throws Exception {
        for (int i = 0; i < 20; i++) {
            requestSignup("user" + i + "@example.com", PASSWORD).andExpect(status().isAccepted());
        }

        requestSignup("user20@example.com", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SIGNUP_RATE_LIMITED"));
    }

    @Test
    void R1_01_메일을_보내지_못하면_503이고_내부_정보는_싣지_않는다() throws Exception {
        mail.failNext(true);

        MvcResult result =
                requestSignup("song@example.com", PASSWORD)
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("MAIL_UNAVAILABLE"))
                        .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("MailSendException")
                .doesNotContain("메일 서버 없음");
    }

    @Test
    void R1_01_X_요청_헤더나_JSON이_아니면_받지_않는다() throws Exception {
        mvc.perform(
                        post("/api/auth/signup")
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"a@example.com\",\"password\":\"blue coffee"
                                                + " rain\",\"name\":\"a\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUESTED_WITH_REQUIRED"));
        assertThat(mail.sent()).isEmpty();
    }

    private SimpleMailMessage lastMailTo(String email) {
        List<SimpleMailMessage> messages = mail.sentTo(email);
        assertThat(messages).as("받은 메일").isNotEmpty();
        return messages.get(messages.size() - 1);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String passwordHashOf(String email) {
        return jdbc.queryForObject(
                "SELECT password_hash FROM member WHERE email = ?", String.class, email);
    }

    private boolean passwordMatchesStored(String email) throws Exception {
        return login(email, PASSWORD, "10.9.9.9").andReturn().getResponse().getStatus() == 200;
    }

    /** 표의 모든 문자열 칸 값. 평문 비밀번호·토큰이 어디에도 없는지 볼 때 쓴다. */
    private List<String> allTextIn(String table) {
        return jdbc.queryForList("SELECT * FROM " + table).stream()
                .flatMap(row -> row.values().stream())
                .filter(value -> value instanceof String)
                .map(String.class::cast)
                .toList();
    }
}
