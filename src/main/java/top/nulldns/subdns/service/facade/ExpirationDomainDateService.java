package top.nulldns.subdns.service.facade;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import top.nulldns.subdns.config.StorageProperties;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpirationDomainDateService implements SchedulingConfigurer {
    private final StorageProperties storageProperties;

    private volatile HashMap<String, LocalDate> expiryDateMap = new HashMap<>();
    
    // 갱신 주기 (기본 3시간, 실패 시 1분으로 변경됨)
    private volatile Duration nextInterval = Duration.ofHours(3);

    @PostConstruct
    private void init() {
        updateData();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
            this::updateData,
            triggerContext -> {
                // 마지막 실행 완료 시간에 동적으로 변경된 주기를 더하여 다음 실행 시간 계산
                Instant lastCompletion = Optional.ofNullable(triggerContext.lastCompletion())
                        .orElse(Instant.now());
                return lastCompletion.plus(nextInterval);
            }
        );
    }

    public LocalDate getDomainExpiryDate(String domain) {
        if (!expiryDateMap.containsKey(domain)) {
            throw new IllegalArgumentException("Invalid domain: " + domain);
        }
        return this.expiryDateMap.get(domain);
    }

    private void updateData() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = storageProperties.getStaticUrl() + "/domains.json";
            ResponseEntity<Map<String, String>> responseEntity =
                    restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            Map<String, String> response = responseEntity.getBody();
            
            if (response != null) {
                HashMap<String, LocalDate> newMap = new HashMap<>();
                for (Map.Entry<String, String> entry : response.entrySet()) {
                    newMap.put(entry.getKey(), LocalDate.parse(entry.getValue()));
                }
                
                this.expiryDateMap = newMap;
                
                // 성공 시 다음 스케줄 간격을 3시간으로 변경
                this.nextInterval = Duration.ofHours(3);
                log.info("Successfully updated expiration domain dates. Next update in 3 hours.");
            }
        } catch (Exception e) {
            // 실패 시 다음 스케줄 간격을 1분으로 변경하여 재시도 유도
            this.nextInterval = Duration.ofMinutes(1);
            log.error("Failed to update expiration domain dates. Retrying in 1 minute.", e);
        }
    }
}
