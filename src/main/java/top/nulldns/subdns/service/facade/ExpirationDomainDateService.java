package top.nulldns.subdns.service.facade;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import top.nulldns.subdns.config.StorageProperties;
import top.nulldns.subdns.config.finalconfig.Event;
import top.nulldns.subdns.config.finalconfig.PubSubChannel;
import top.nulldns.subdns.service.infra.InstanceIdProvider;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpirationDomainDateService {
    private final StorageProperties storageProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final InstanceIdProvider instanceIdProvider;

    private volatile HashMap<String, LocalDate> expiryDatesMap = new HashMap<>();

    @PostConstruct
    private void init() {
        refreshMaxDomainDates(false);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    private void scheduledRefresh() {
        refreshMaxDomainDates(false);
    }

    public LocalDate getDomainExpiryDate(String domain) {
        if (!expiryDatesMap.containsKey(domain)) {
            throw new IllegalArgumentException("Invalid domain: " + domain);
        }
        return this.expiryDatesMap.get(domain);
    }

    public void refreshMaxDomainDates(boolean isSendEvent) {
        if (isSendEvent) {
            stringRedisTemplate.convertAndSend(PubSubChannel.REFRESH.name(), Event.REFRESH_DOMAIN_MAX_DATES.name() + ":" + instanceIdProvider.getInstanceId());
        }

        try {
            this.expiryDatesMap = getRefreshExpiryDatesMap();
        } catch (Exception e) {
            log.error("도메인 최대 만료일 갱신 중 에러 발생: {}", e.toString());
        }
    }

    private HashMap<String, LocalDate> getRefreshExpiryDatesMap() {
        RestTemplate restTemplate = new RestTemplate();
        String url = storageProperties.getStaticUrl() + "/domains.json";
        ResponseEntity<Map<String, String>> responseEntity =
                restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        Map<String, String> response = responseEntity.getBody();

        if (response == null || Objects.requireNonNull(response).isEmpty()) {
            throw new RuntimeException("갱신하고자 받은 domains.json 내용이 비어있습니다");
        }

        HashMap<String, LocalDate> newMap = new HashMap<>();
        for (Map.Entry<String, String> entry : response.entrySet()) {
            newMap.put(entry.getKey(), LocalDate.parse(entry.getValue()));
        }
        return newMap;
    }
}
