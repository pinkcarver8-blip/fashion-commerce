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

    /**
     * 비밀번호를 비교하기 전에 시도 한 칸을 먼저 차지한다. 이미 5칸이면 false(막힘). 비교 뒤에 세면, 같은 순간에 보낸 시도 50개가 모두 "아직 0번"을 보고
     * 통과한다(2026-11-28 독립 검토). 성공하면 {@link #reset}이 칸을 비운다. 그래서 남는 칸은 실패한 시도뿐이다.
     */
    boolean tryStart(String rawEmail, String ip) {
        return counters.reserve(pairKey(rawEmail, ip), MAX_FAILURES, BLOCK);
    }

    /** 실패는 {@link #tryStart}에서 이미 셌다. 여기서는 계정별 경고만 센다. */
    void recordFailure(String rawEmail) {
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
