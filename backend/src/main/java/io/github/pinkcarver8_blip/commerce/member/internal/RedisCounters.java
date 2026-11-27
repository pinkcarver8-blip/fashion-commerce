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

    /** ARGV[1] 만료(ms). 처음 올릴 때만 만료를 건다(그때부터 센다). */
    private static final RedisScript<Long> INCREMENT =
            new DefaultRedisScript<>(
                    "local c = redis.call('INCR', KEYS[1]) "
                            + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                            + "return c",
                    Long.class);

    /**
     * ARGV[1] 만료(ms), ARGV[2] 한도. 이미 한도에 닿았으면 올리지 않고 -1. 아니면 올리고 만료를 새로 건다. 읽기와 올리기가 한 번에 일어나, 동시에
     * 온 요청들이 모두 "아직 0번"을 읽고 통과하는 일이 없다.
     */
    private static final RedisScript<Long> RESERVE =
            new DefaultRedisScript<>(
                    "local c = tonumber(redis.call('GET', KEYS[1]) or '0') "
                            + "if c >= tonumber(ARGV[2]) then return -1 end "
                            + "c = redis.call('INCR', KEYS[1]) "
                            + "redis.call('PEXPIRE', KEYS[1], ARGV[1]) "
                            + "return c",
                    Long.class);

    private final StringRedisTemplate redis;

    RedisCounters(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 처음 올린 때부터 {@code window} 동안 센다. */
    long incrementInWindow(String key, Duration window) {
        Long count = redis.execute(INCREMENT, List.of(key), String.valueOf(window.toMillis()));
        if (count == null) {
            throw new IllegalStateException("Redis 카운터 응답이 없다");
        }
        return count;
    }

    /** 한도 안이면 한 칸을 차지하고 true, 이미 한도면 false. 차지한 칸은 {@code ttl} 뒤 사라진다(마지막으로 차지한 때부터). */
    boolean reserve(String key, long limit, Duration ttl) {
        Long count =
                redis.execute(
                        RESERVE,
                        List.of(key),
                        String.valueOf(ttl.toMillis()),
                        String.valueOf(limit));
        if (count == null) {
            throw new IllegalStateException("Redis 카운터 응답이 없다");
        }
        return count >= 0;
    }

    void delete(String key) {
        redis.delete(key);
    }
}
