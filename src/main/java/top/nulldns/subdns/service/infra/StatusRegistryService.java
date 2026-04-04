package top.nulldns.subdns.service.infra;

import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class StatusRegistryService {
    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_KEY_PREFIX = "status:";

    public void newOrKeepStatus(String variable, String value) {
        this.redisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + variable, value);
    }

    public void setStatus(String variable, String value) {
        this.redisTemplate.opsForValue().set(LOCK_KEY_PREFIX + variable, value);
    }

    public String getStatus(String variable) {
        return this.redisTemplate.opsForValue().get(LOCK_KEY_PREFIX + variable);
    }

    public void increment(String variable) {
        this.redisTemplate.opsForValue().increment(LOCK_KEY_PREFIX + variable);
    }
}
