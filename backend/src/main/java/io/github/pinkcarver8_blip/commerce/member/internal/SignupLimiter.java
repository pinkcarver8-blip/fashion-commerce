package io.github.pinkcarver8_blip.commerce.member.internal;

import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 가입 신청 횟수 제한(V5, 작업진행.md A5-9 S13).
 *
 * <ul>
 *   <li>같은 IP의 신청은 1시간에 20번까지. 넘으면 429(회원 여부와 상관없는 답이라 아무것도 새지 않는다).
 *   <li>같은 이메일로 가는 메일은 1시간에 5통까지. 넘으면 메일을 보내지 않고 같은 답을 준다(메일 폭탄 방지, 회원 여부를 드러내지 않는다).
 * </ul>
 */
@Component
class SignupLimiter {

    static final int MAX_REQUESTS_PER_IP = 20;
    static final int MAX_MAILS_PER_EMAIL = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final RedisCounters counters;

    SignupLimiter(RedisCounters counters) {
        this.counters = counters;
    }

    boolean allowRequestFrom(String ip) {
        return counters.incrementInWindow("member:signup-ip:" + ip, WINDOW) <= MAX_REQUESTS_PER_IP;
    }

    boolean allowMailTo(String normalizedEmail) {
        String key = "member:signup-mail:" + OpaqueTokens.sha256Hex(normalizedEmail);
        return counters.incrementInWindow(key, WINDOW) <= MAX_MAILS_PER_EMAIL;
    }
}
