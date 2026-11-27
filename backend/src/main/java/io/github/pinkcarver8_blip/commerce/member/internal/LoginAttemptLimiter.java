package io.github.pinkcarver8_blip.commerce.member.internal;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 로그인 시도 제한(작업진행.md A5-9 S6, ADR-008 결정 7). 계정 + 접속 IP 조합이 5번 틀리면 그 조합만 15분 막는다. 계정 전체는 잠그지 않는다(남이
 * 일부러 틀려 진짜 주인을 못 들어오게 할 수 있어서).
 *
 * <p>접속 IP는 Tomcat이 신뢰 프록시(web 컨테이너)에서 온 요청에서만 {@code X-Forwarded-For}로 바꾼 값이다(application.yml). 없는
 * 이메일도 똑같이 센다. 그래서 막혔다는 답으로 회원 여부를 알 수 없다.
 */
@Component
class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration BLOCK = Duration.ofMinutes(15);

    /** 여러 IP에서 한 계정을 노리면 계정별 실패 수로 경고 로그를 남긴다. 대응(TOTP 등)은 S11. */
    private static final int ACCOUNT_WARNING_THRESHOLD = 20;

    private static final Duration ACCOUNT_WINDOW = Duration.ofHours(1);

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    private final RedisCounters counters;

    LoginAttemptLimiter(RedisCounters counters) {
        this.counters = counters;
    }

    boolean isBlocked(String rawEmail, String ip) {
        return counters.current(pairKey(rawEmail, ip)) >= MAX_FAILURES;
    }

    void recordFailure(String rawEmail, String ip) {
        counters.incrementSliding(pairKey(rawEmail, ip), BLOCK);
        long accountFailures = counters.incrementInWindow(accountKey(rawEmail), ACCOUNT_WINDOW);
        if (accountFailures == ACCOUNT_WARNING_THRESHOLD) {
            // 이메일 대신 해시 앞 12자만 남긴다(A5-10 로그 마스킹).
            log.warn(
                    "한 계정에 1시간 안에 로그인 실패가 {}번 쌓였다. account={}",
                    ACCOUNT_WARNING_THRESHOLD,
                    accountHash(rawEmail).substring(0, 12));
        }
    }

    void reset(String rawEmail, String ip) {
        counters.delete(pairKey(rawEmail, ip));
    }

    static String pairKey(String rawEmail, String ip) {
        return "member:login-fail:" + accountHash(rawEmail) + ":" + ip;
    }

    private static String accountKey(String rawEmail) {
        return "member:login-fail-account:" + accountHash(rawEmail);
    }

    private static String accountHash(String rawEmail) {
        return OpaqueTokens.sha256Hex(EmailAddresses.limiterKeyOf(rawEmail));
    }
}
