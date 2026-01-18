package top.nulldns.subdns;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.repository.HaveSubDomainRepository;
import top.nulldns.subdns.service.AdminService;
import top.nulldns.subdns.service.CheckAdminService;
import top.nulldns.subdns.service.PDNSService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class SubDNSScheduler {
    private final HaveSubDomainRepository haveSubDomainRepository;
    private final PDNSService pdnsService;
    private final StringRedisTemplate redisTemplate;
    private final CheckAdminService checkAdminService;

    private static final String LOCK_KEY = "scheduler:delete-expiry-domain";
    private static final Duration LOCK_TTL = Duration.ofSeconds(55);

    @Scheduled(cron = "0 5 0 * * *")
    //@Scheduled(cron = "0 * * * * *")
    public void deleteExpiryDomain() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "LOCKED", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("만료된 서브도메인 삭제 스케줄러가 이미 실행 중입니다. 중복 실행을 방지합니다.");
            return;
        }

        try {
            runDeleteExpiryDomain();
        } catch (Exception e) {
            log.error("만료된 서브도메인 삭제 스케줄러 실행 중 오류 발생", e);
        }
    }

    private void runDeleteExpiryDomain() {
        List<HaveSubDomain> deleteList = haveSubDomainRepository.findByExpiryDateBefore(LocalDate.now());

        log.info("만료된 서브도메인 삭제 스케줄러 실행: 삭제 대상 {}개", deleteList.size());
        if (deleteList.isEmpty()) {
            return;
        }

        Long successCount = 0L;
        Long adminCount = 0L;
        log.info("만료된 서브도메인 {}개 삭제 시작", deleteList.size());
        for (HaveSubDomain domain : deleteList) {

            if (checkAdminService.isAdmin(domain.getMember().getId())) {
                log.info("관리자 계정의 서브도메인 {}은 삭제하지 않음", domain.getFullDomain());
                adminCount++;
                continue;
            }

            boolean success = pdnsService.deleteSubRecordSchedule(domain);
            if (success) {
                successCount++;
            }
        }
        log.info("만료된 서브도메인 삭제 완료: {}개 중 {}개 삭제 성공", deleteList.size(), successCount);
        log.info("관리자 계정의 서브도메인 {}개는 삭제하지 않음", adminCount);
    }
}
