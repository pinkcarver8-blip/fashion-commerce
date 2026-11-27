package io.github.pinkcarver8_blip.commerce.member.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * 고객 액세스 토큰 발급(ADR-008 결정 2). JWT HS256, 15분, {@code aud=store}, {@code sub}는 회원 외부 ID.
 *
 * <p>발급 도구는 Spring의 {@link JwtEncoder}로 받는다. platform 모듈을 부르지 않는다(ADR-001 보충, 작업진행.md E4-2 확인 항목).
 * 발급자·용도 값은 platform의 고객 보안 설정과 같아야 한다. 다르면 R1-01 T1 시험이 실패한다.
 */
@Component
class AccessTokenIssuer {

    static final Duration LIFETIME = Duration.ofMinutes(15);
    private static final String ISSUER = "fashion-commerce";
    private static final String AUDIENCE = "store";

    private final JwtEncoder encoder;

    AccessTokenIssuer(@Qualifier("storeJwtEncoder") JwtEncoder encoder) {
        this.encoder = encoder;
    }

    IssuedAccessToken issue(UUID memberPublicId, Instant now) {
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .audience(List.of(AUDIENCE))
                        .subject(memberPublicId.toString())
                        .issuedAt(now)
                        .expiresAt(now.plus(LIFETIME))
                        .id(UUID.randomUUID().toString())
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, LIFETIME.toSeconds());
    }

    record IssuedAccessToken(String value, long expiresInSeconds) {

        @Override
        public String toString() {
            return "IssuedAccessToken[value=(숨김), expiresInSeconds=" + expiresInSeconds + "]";
        }
    }
}
