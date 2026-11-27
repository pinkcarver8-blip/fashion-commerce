package io.github.pinkcarver8_blip.commerce.member.internal;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 횟수 제한용 카운터(Redis, A1: 레이트리밋 전용). 올리기와 만료 설정을 한 번에(Lua) 해서, 그 사이에 멈춰도 만료 없는 열쇠가 남지 않는다.
 *
 * <p>Redis가 응답하지 않으면 예외가 그대로 올라가 요청이 실패한다. 제한을 확인할 수 없을 때 통과시키지 않는다(안전한 쪽으로 실패).
 */
@Component
class RedisCounters {

    /** ARGV[1] 만료(ms), ARGV[2] "1"이면 올릴 때마다 만료를 새로 건다(마지막 실패부터 센다). */
    private static final RedisScript<Long> INCREMENT =
            new DefaultRedisScript<>(
                    "local c = redis.call('INCR', KEYS[1]) "
                            + "if c == 1 or ARGV[2] == '1' then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                            + "return c",
                    Long.class);

    private final StringRedisTemplate redis;

    RedisCounters(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 처음 올린 때부터 {@code window} 동안 센다. */
    long incrementInWindow(String key, Duration window) {
        return run(key, window, false);
    }

    /** 마지막으로 올린 때부터 {@code ttl}이 지나면 사라진다. */
    long incrementSliding(String key, Duration ttl) {
        return run(key, ttl, true);
    }

    long current(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    void delete(String key) {
        redis.delete(key);
    }

    private long run(String key, Duration ttl, boolean refresh) {
        Long count =
                redis.execute(
                        INCREMENT,
                        List.of(key),
                        String.valueOf(ttl.toMillis()),
                        refresh ? "1" : "0");
        if (count == null) {
            throw new IllegalStateException("Redis 카운터 응답이 없다");
        }
        return count;
    }
}
