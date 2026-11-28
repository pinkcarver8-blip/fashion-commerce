package io.github.pinkcarver8_blip.commerce.member.web;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;

/**
 * 고객 리프레시 토큰 쿠키(ADR-008 결정 3, A5-9 S4). HttpOnly(자바스크립트가 못 읽음), Secure(HTTPS만, 브라우저의 localhost 예외는
 * Chrome· Firefox), SameSite=Strict(다른 사이트에서 시작한 요청에는 붙지 않음), Path=/api/auth(인증 주소에만 붙음).
 *
 * <p>Secure는 개발에서도 끄지 않는다(2026-11-27 사용자 결정). Safari는 http://localhost에 Secure 쿠키를 보내지 않아 개발 브라우저에서
 * 뺐다.
 */
final class RefreshCookie {

    static final String NAME = "store_rt";
    static final String PATH = "/api/auth";

    private RefreshCookie() {}

    static ResponseCookie of(String value, Instant expiresAt, Instant now) {
        Duration maxAge = Duration.between(now, expiresAt);
        return base(value).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
    }

    static ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH);
    }
}
