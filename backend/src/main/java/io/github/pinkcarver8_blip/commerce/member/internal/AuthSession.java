package io.github.pinkcarver8_blip.commerce.member.internal;

import java.time.Instant;

/** 로그인 결과: 본문으로 줄 액세스 토큰과 쿠키로 줄 리프레시 토큰. 기록에 찍혀도 값이 보이지 않게 toString을 가린다(X1). */
public record AuthSession(
        String accessToken,
        long accessExpiresInSeconds,
        String refreshToken,
        Instant refreshExpiresAt) {

    @Override
    public String toString() {
        return "AuthSession[accessToken=(숨김), refreshToken=(숨김), refreshExpiresAt="
                + refreshExpiresAt
                + "]";
    }
}
