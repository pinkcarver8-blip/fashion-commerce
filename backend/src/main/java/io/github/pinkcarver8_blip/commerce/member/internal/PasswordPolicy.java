package io.github.pinkcarver8_blip.commerce.member.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 가입 비밀번호 규칙(작업진행.md A5-9 S13, 2026-11-28 사용자 결정).
 *
 * <ul>
 *   <li>8자 이상(글자 수는 유니코드 글자 단위). <b>보안을 양보함</b>: NIST SP 800-63B-4의 15자 대신 OWASP ASVS 5.0 6.2.1의
 *       최소값을 쓴다.
 *   <li>UTF-8 72바이트 이하. bcrypt는 72바이트를 넘으면 오류를 낸다(spring-security-crypto BCrypt). 영문 72자, 한글 24자.
 *   <li>흔한 비밀번호 목록(resources/member/common-passwords.txt), 이메일 아이디·서비스 이름이 든 것, 한 글자 반복, 연속 문자는
 *       거절한다. 대소문자는 가리지 않고 비교한다.
 *   <li>문자 종류 섞기(대문자·숫자·특수문자)는 요구하지 않는다(NIST·ASVS 6.2.5). 받은 값을 바꾸지 않고(공백도 그대로) 검사·저장한다(ASVS
 *       6.2.8).
 * </ul>
 */
@Component
class PasswordPolicy {

    static final int MIN_CHARACTERS = 8;
    static final int MAX_UTF8_BYTES = 72;

    /** 이메일 아이디가 이보다 짧으면 포함 여부를 보지 않는다(예: "kim"이 든 비밀번호를 모두 막지 않기 위해). */
    private static final int MIN_LOCAL_PART_TO_CHECK = 4;

    /** 서비스 이름. 브랜드명이 정해지면(R0-10) 더한다. 비교 전에 영문·숫자 밖의 글자를 지운다. */
    private static final List<String> SERVICE_WORDS = List.of("fashioncommerce");

    private final Set<String> commonPasswords;

    PasswordPolicy() {
        this.commonPasswords = load("member/common-passwords.txt");
    }

    Optional<MemberErrorCode> check(String password, String normalizedEmail) {
        int characters = password.codePointCount(0, password.length());
        if (characters < MIN_CHARACTERS) {
            return Optional.of(MemberErrorCode.PASSWORD_TOO_SHORT);
        }
        if (utf8Bytes(password) > MAX_UTF8_BYTES) {
            return Optional.of(MemberErrorCode.PASSWORD_TOO_LONG);
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(lower)) {
            return Optional.of(MemberErrorCode.PASSWORD_TOO_COMMON);
        }
        if (containsContext(lower, normalizedEmail)) {
            return Optional.of(MemberErrorCode.PASSWORD_CONTAINS_CONTEXT);
        }
        if (isRepetitiveOrSequential(lower)) {
            return Optional.of(MemberErrorCode.PASSWORD_REPETITIVE);
        }
        return Optional.empty();
    }

    static int utf8Bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean containsContext(String lower, String normalizedEmail) {
        String alphanumeric = lower.replaceAll("[^a-z0-9]", "");
        if (SERVICE_WORDS.stream().anyMatch(alphanumeric::contains)) {
            return true;
        }
        if (lower.contains(normalizedEmail)) {
            return true;
        }
        String localPart = EmailAddresses.localPartOf(normalizedEmail);
        return localPart.length() >= MIN_LOCAL_PART_TO_CHECK && lower.contains(localPart);
    }

    /** 전부 같은 글자(aaaaaaaa), 또는 한 칸씩 오르거나 내리는 글자(12345678, abcdefgh, 87654321). */
    private static boolean isRepetitiveOrSequential(String lower) {
        int[] points = lower.codePoints().toArray();
        boolean same = true;
        boolean up = true;
        boolean down = true;
        for (int i = 1; i < points.length; i++) {
            int step = points[i] - points[i - 1];
            same &= step == 0;
            up &= step == 1;
            down &= step == -1;
        }
        return same || up || down;
    }

    private static Set<String> load(String path) {
        Set<String> words = new HashSet<>();
        try (InputStream in = new ClassPathResource(path).getInputStream();
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#")) {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("흔한 비밀번호 목록을 읽지 못했다: " + path, e);
        }
        if (words.isEmpty()) {
            // 목록 없이 뜨면 규칙이 조용히 약해진다. 시작을 막는다.
            throw new IllegalStateException("흔한 비밀번호 목록이 비어 있다: " + path);
        }
        return Set.copyOf(words);
    }
}
