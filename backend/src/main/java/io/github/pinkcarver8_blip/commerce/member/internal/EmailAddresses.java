package io.github.pinkcarver8_blip.commerce.member.internal;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 이메일 모양 검사와 맞추기(G2). 앞뒤 공백을 지우고 소문자로 바꾼다. {@code A@x.com}과 {@code a@x.com}은 같은 계정이다.
 *
 * <p>영문·숫자·일부 기호만 받는다. 한글 같은 비영문 주소는 대소문자 규칙이 언어마다 달라 같은 주소를 다르게 볼 수 있어서 받지 않는다.
 */
final class EmailAddresses {

    private static final Pattern FORMAT =
            Pattern.compile("^[a-z0-9._%+-]{1,64}@[a-z0-9-]+(\\.[a-z0-9-]+)+$");
    private static final int MAX_LENGTH = 254;

    private EmailAddresses() {}

    static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String email = raw.strip().toLowerCase(Locale.ROOT);
        if (email.length() > MAX_LENGTH || !FORMAT.matcher(email).matches()) {
            return Optional.empty();
        }
        return Optional.of(email);
    }

    /** 로그인 제한의 열쇠. 모양이 틀린 주소도 같은 규칙으로 센다(모양 검사로 제한을 피하지 못하게). */
    static String limiterKeyOf(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    static String localPartOf(String normalizedEmail) {
        return normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
    }
}
