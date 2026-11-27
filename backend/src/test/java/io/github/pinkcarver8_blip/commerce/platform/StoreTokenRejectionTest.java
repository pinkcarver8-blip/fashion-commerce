package io.github.pinkcarver8_blip.commerce.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.pinkcarver8_blip.commerce.member.AuthTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;

/**
 * R1-01 T1·T2: 고객 보안 설정은 고객 키로 서명되고 용도·발급자·만료가 맞는 토큰만 받는다(ADR-008 결정 1·2, A5-9 S1·S3).
 *
 * <p>공유 보안 설정 막는 장치 3번: 이 시험을 지우거나 약하게 고치지 않는다. 관리자 체인(R1-02)에도 같은 시험을 만든다
 * (.claude/rules/shared-modules.md).
 */
class StoreTokenRejectionTest extends AuthTestSupport {

    @Autowired
    @Qualifier("storeJwtEncoder")
    JwtEncoder storeEncoder;

    @Test
    void R1_01_T1_고객_토큰으로_내_정보를_보고_토큰이_없으면_401이다() throws Exception {
        Session session = registerMember("me@example.com", PASSWORD);

        MvcResult result =
                mvc.perform(get("/api/members/me").header("Authorization", bearer(session)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.email").value("me@example.com"))
                        .andExpect(jsonPath("$.name").value("홍길동"))
                        .andReturn();
        assertThat(body(result).propertyNames()).containsExactlyInAnyOrder("email", "name");

        mvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void R1_01_T2_만료된_토큰은_401이다() throws Exception {
        UUID member = memberPublicId();
        Instant past = Instant.now().minus(Duration.ofMinutes(20));
        assertRejected(
                sign(
                        storeEncoder,
                        claims ->
                                claims.subject(member.toString())
                                        .issuedAt(past)
                                        .expiresAt(past.plus(Duration.ofMinutes(15)))));
    }

    @Test
    void R1_01_T2_내용을_고친_토큰은_401이다() throws Exception {
        Session session = registerMember("tamper@example.com", PASSWORD);
        String[] parts = session.accessToken().split("\\.");
        String otherSub =
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                        .replaceAll("\"sub\":\"[^\"]+\"", "\"sub\":\"" + UUID.randomUUID() + "\"");
        String tampered =
                parts[0]
                        + "."
                        + Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(otherSub.getBytes(StandardCharsets.UTF_8))
                        + "."
                        + parts[2];

        assertRejected(tampered);
    }

    @Test
    void R1_01_T2_다른_키로_서명한_토큰은_401이다() throws Exception {
        UUID member = memberPublicId();
        JwtEncoder otherKey =
                new NimbusJwtEncoder(
                        new ImmutableSecret<>(
                                new SecretKeySpec(
                                        "another-test-key-also-32-bytes!!"
                                                .getBytes(StandardCharsets.UTF_8),
                                        "HmacSHA256")));

        assertRejected(sign(otherKey, claims -> claims.subject(member.toString())));
    }

    @Test
    void R1_01_T2_관리자_용도_토큰과_다른_발급자_토큰은_401이다() throws Exception {
        UUID member = memberPublicId();

        assertRejected(
                sign(
                        storeEncoder,
                        claims -> claims.subject(member.toString()).audience(List.of("admin"))));
        assertRejected(
                sign(
                        storeEncoder,
                        claims ->
                                claims.subject(member.toString())
                                        .audience(List.of("store", "admin"))));
        assertRejected(
                sign(
                        storeEncoder,
                        claims -> claims.subject(member.toString()).issuer("someone-else")));
    }

    @Test
    void R1_01_T2_서명_없는_토큰_alg_none은_401이다() throws Exception {
        UUID member = memberPublicId();
        long now = Instant.now().getEpochSecond();
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload =
                base64Url(
                        "{\"iss\":\"fashion-commerce\",\"aud\":[\"store\"],\"sub\":\""
                                + member
                                + "\",\"iat\":"
                                + now
                                + ",\"exp\":"
                                + (now + 600)
                                + "}");

        assertRejected(header + "." + payload + ".");
    }

    @Test
    void R1_01_T2_만료_칸이_없거나_회원_ID_모양이_아닌_토큰은_401이다() throws Exception {
        UUID member = memberPublicId();
        Instant now = Instant.now();

        assertRejected(
                sign(
                        storeEncoder,
                        claims ->
                                claims.subject(member.toString())
                                        .issuedAt(now)
                                        .claims(map -> map.remove("exp"))));
        assertRejected(sign(storeEncoder, claims -> claims.subject("1")));
        assertRejected(sign(storeEncoder, claims -> claims.subject("1-1-1-1-1")));
    }

    @Test
    void R1_01_T_기본_거부_목록에_없는_주소는_401이고_관리자_주소는_고객_토큰으로_403이다() throws Exception {
        Session session = registerMember("deny@example.com", PASSWORD);

        mvc.perform(get("/api/anything-not-listed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders").header("Authorization", bearer(session)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private void assertRejected(String token) throws Exception {
        MvcResult result =
                mvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                        .andReturn();
        // 무엇이 틀렸는지(만료·서명 등)는 알려 주지 않는다.
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    private UUID memberPublicId() throws Exception {
        registerMember("token@example.com", PASSWORD);
        return UUID.fromString(
                jdbc.queryForObject(
                        "SELECT BIN_TO_UUID(public_id) FROM member WHERE email = ?",
                        String.class,
                        "token@example.com"));
    }

    private static JwtClaimsSet.Builder validClaims(Instant now) {
        return JwtClaimsSet.builder()
                .issuer("fashion-commerce")
                .audience(List.of("store"))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)));
    }

    private static String sign(JwtEncoder encoder, Consumer<JwtClaimsSet.Builder> change) {
        JwtClaimsSet.Builder claims = validClaims(Instant.now());
        change.accept(claims);
        return encoder.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private static String bearer(Session session) {
        return "Bearer " + session.accessToken();
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
