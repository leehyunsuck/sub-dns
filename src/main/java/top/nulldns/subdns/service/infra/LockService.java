package top.nulldns.subdns.service.infra;

import lombok.AllArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@AllArgsConstructor
public class LockService {
    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_KEY_PREFIX = "lock:";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    public String lock(String key) {
        return lock(key, DEFAULT_TTL);
    }

    public String lock(String key, Duration ttl) {
        String value = UUID.randomUUID().toString();

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + key, value, ttl);

        if (!Boolean.TRUE.equals(locked)) {
            throw new ConcurrencyFailureException("해당 LOCK은 이미 작업중 ...");
        }

        return value;
    }

    public void unlock(String key, String value) {
        String lockedValue = redisTemplate.opsForValue().get(LOCK_KEY_PREFIX + key);

        if (value.equals(lockedValue)) {
            redisTemplate.delete(LOCK_KEY_PREFIX + key);
        }
    }
}
