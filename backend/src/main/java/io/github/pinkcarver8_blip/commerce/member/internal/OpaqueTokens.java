package io.github.pinkcarver8_blip.commerce.member.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 추측할 수 없는 일회용 값(리프레시 토큰, 가입 인증 토큰). 32바이트 무작위 값을 base64url 43자로 쓴다(ADR-008 결정 3).
 *
 * <p>DB에는 값 대신 SHA-256 해시만 둔다. DB가 유출돼도 해시로는 토큰을 쓸 수 없다.
 */
final class OpaqueTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private OpaqueTokens() {}

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 모양이 맞는 토큰만 해시를 돌려준다. 모양이 다르면 DB를 찾아보지 않는다. */
    static Optional<byte[]> hashOf(String token) {
        if (token == null || !FORMAT.matcher(token).matches()) {
            return Optional.empty();
        }
        return Optional.of(sha256(token));
    }

    static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없다", e);
        }
    }

    /** Redis 열쇠·로그에 이메일 대신 쓰는 값(개인정보를 그대로 두지 않는다, A5-10). */
    static String sha256Hex(String text) {
        return HexFormat.of().formatHex(sha256(text));
    }
}
