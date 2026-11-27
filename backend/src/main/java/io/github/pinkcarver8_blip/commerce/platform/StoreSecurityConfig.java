package io.github.pinkcarver8_blip.commerce.platform;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.pinkcarver8_blip.commerce.identity.CustomerPrincipal;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * 고객 보안 설정(ADR-008 결정 1·2, 작업진행.md A5-9 S1·S3·S4). 모든 요청이 이 체인을 지난다. 관리자 체인은 R1-02에서 더한다.
 *
 * <ul>
 *   <li><b>기본 거부</b>: 공개 경로는 {@link #PUBLIC_POST} 한 곳에서만 연다. 나머지는 고객 토큰이 있어야 한다. 관리자 경로는 관리자 체인이
 *       생기기 전까지 모두 막는다.
 *   <li><b>토큰</b>: HS256, 고객 전용 키, 발급자와 용도({@code aud=store})가 맞고 만료 시각이 있는 토큰만 받는다. 다른 서명
 *       방식({@code none} 포함)은 받지 않는다.
 *   <li><b>주체</b>: 검사를 통과한 토큰의 {@code sub}(회원 외부 ID)로만 {@link CustomerPrincipal}을 만든다.
 * </ul>
 *
 * <p>이 클래스를 고칠 때 지킬 것: .claude/rules/shared-modules.md "공유 보안 설정의 약점과 막는 장치".
 */
@Configuration
public class StoreSecurityConfig {

    /** 발급자. member 모듈의 토큰 발급(AccessTokenIssuer)과 같아야 한다. 다르면 R1-01 T1 시험이 실패한다. */
    static final String ISSUER = "fashion-commerce";

    /** 용도. 관리자 토큰({@code admin})은 이 체인에서 받지 않는다(S3). */
    static final String AUDIENCE = "store";

    /** 비로그인으로 열어 두는 경로. 줄을 더하는 PR은 이유를 적는다(막는 장치 4번). */
    static final List<String> PUBLIC_POST =
            List.of(
                    "/api/auth/signup",
                    "/api/auth/signup/complete",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout");

    /** 회원 외부 ID 모양(UUID 소문자 36자). UUID.fromString은 "1-1-1-1-1" 같은 값도 받아서 따로 본다. */
    private static final Pattern UUID_TEXT =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey storeKey;

    StoreSecurityConfig(@Value("${commerce.auth.store.jwt-secret}") String base64Secret) {
        this.storeKey = toKey(base64Secret);
    }

    @Bean
    SecurityFilterChain storeSecurityFilterChain(HttpSecurity http) throws Exception {
        // CSRF 토큰을 쓰지 않는 이유(ADR-008 결정 2·3): 다른 API의 토큰은 브라우저가 자동으로 붙이지 않는 요청 머리(Authorization)로
        // 온다.
        // 쿠키를 쓰는 곳은 /api/auth뿐이고, 그 쿠키는 SameSite=Strict, 그 주소들은 X-Requested-With 헤더와 JSON 본문을
        // 요구하며 CORS는 열지 않는다. CodeQL java/spring-disabled-csrf-protection 경고는 이 이유로 닫는다(R1-01 최종
        // 보고).
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        // 오류 응답(/error)으로 넘어간 요청은 원래 요청의 검사를 이미 거쳤다.
                                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers("/api/admin/**")
                                        .denyAll()
                                        .requestMatchers(
                                                HttpMethod.POST, PUBLIC_POST.toArray(String[]::new))
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer
                                        .jwt(
                                                jwt ->
                                                        jwt.decoder(storeJwtDecoder())
                                                                .jwtAuthenticationConverter(
                                                                        StoreSecurityConfig
                                                                                ::toAuthentication))
                                        .authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        writeUnauthorized(response))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        writeForbidden(response)))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        writeUnauthorized(response))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        writeForbidden(response)));
        return http.build();
    }

    /** 고객 토큰 검사. 서명(고객 키, HS256만) → 만료·발급자 → 용도·만료 칸·회원 ID 모양. */
    @Bean
    JwtDecoder storeJwtDecoder() {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(storeKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(ISSUER),
                        new JwtClaimValidator<List<String>>(
                                JwtClaimNames.AUD,
                                audience -> audience != null && audience.equals(List.of(AUDIENCE))),
                        new JwtClaimValidator<Object>(JwtClaimNames.EXP, exp -> exp != null),
                        new JwtClaimValidator<String>(
                                JwtClaimNames.SUB,
                                sub -> sub != null && UUID_TEXT.matcher(sub).matches())));
        return decoder;
    }

    /** 고객 토큰 발급 도구. member 모듈은 이 클래스를 부르지 않고 Spring의 {@link JwtEncoder}로 받는다(ADR-001 보충). */
    @Bean
    JwtEncoder storeJwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(storeKey));
    }

    private static AbstractAuthenticationToken toAuthentication(Jwt jwt) {
        CustomerPrincipal principal = new CustomerPrincipal(UUID.fromString(jwt.getSubject()));
        return new PreAuthenticatedAuthenticationToken(
                principal, jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private static SecretKey toKey(String base64Secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Secret.strip());
        } catch (IllegalArgumentException e) {
            // 값은 오류 메시지에 싣지 않는다(A5-10).
            throw new IllegalStateException("STORE_JWT_SECRET은 base64 값이어야 한다");
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("STORE_JWT_SECRET은 32바이트(256비트) 이상이어야 한다");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    // 오류 응답(RFC 9457 + code, ADR-010). 무엇이 틀렸는지(만료·서명 등)는 알려 주지 않는다.
    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        writeProblem(response, 401, "UNAUTHENTICATED", "로그인이 필요합니다");
    }

    private static void writeForbidden(HttpServletResponse response) throws IOException {
        writeProblem(response, 403, "FORBIDDEN", "접근할 수 없습니다");
    }

    private static void writeProblem(
            HttpServletResponse response, int status, String code, String title)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write(
                        "{\"type\":\"about:blank\",\"title\":\""
                                + title
                                + "\",\"status\":"
                                + status
                                + ",\"code\":\""
                                + code
                                + "\"}");
    }
}
