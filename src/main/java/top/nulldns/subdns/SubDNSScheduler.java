package top.nulldns.subdns;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.nulldns.subdns.config.finalconfig.Status;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.service.domain.HaveSubDomainService;
import top.nulldns.subdns.service.facade.PDNSService;
import top.nulldns.subdns.service.infra.LockService;
import top.nulldns.subdns.service.infra.StatusRegistryService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;


@Component
@AllArgsConstructor
@Slf4j
public class SubDNSScheduler {
    private final HaveSubDomainService haveSubDomainService;
    private final PDNSService pdnsService;
    private final LockService lockService;
    private final StatusRegistryService statusRegistryService;

    private static final Status[] STATUSES = { Status.ADD_PENDING, Status.UPDATE_PENDING, Status.DELETE_PENDING };

    private static final String LOCK_KEY_PREFIX = "scheduler:";
    private static final String STATUS_IDX = "scheduler:statusIdx";

    @Scheduled(cron = "0 5 0 * * *")
    public void deleteExpiryDomain() {
        log.info("Processing expired domain delete");
        String lockKey = LOCK_KEY_PREFIX + "expiry";
        String value = lockService.lock(lockKey);

        try {
            runDeleteExpiryDomain();
        } catch (Exception e) {
            log.error("만료 도메인 제거 에러" + e);
        } finally {
            lockService.unlock(lockKey, value);
        }
    }

    @Scheduled(cron = "0 0/1 * * * *")
    public void pendingDomain() {
        // LOCK
        String lockKey = LOCK_KEY_PREFIX + "pending";
        String lockValue = null;
        try {
            lockValue = lockService.lock(lockKey, Duration.ofDays(3600));
        } catch (ConcurrencyFailureException e) {   // 다른 VM or 이전 작업 점유중
            return;
        }

        // 매 회차마다 다음 PENDING 작업 진행
        statusRegistryService.newOrKeepStatus(STATUS_IDX, "0");
        String value = statusRegistryService.getStatus(STATUS_IDX);

        int idx = (int) Math.floor(Math.random() * STATUSES.length);
        try {
            idx = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }

        // 이건 버그임
        if (idx >= STATUSES.length) {
            idx = 0;
        }

        Status status = STATUSES[idx];

        // 실제 작업 시작
        try {
            List<HaveSubDomain> targetSubDomains = haveSubDomainService.getAvailableSubDomains(status);
            if (!targetSubDomains.isEmpty()) {
                pdnsService.modifyPendingRecords(targetSubDomains, status);
            }
        } finally {
            // 다음 작업을 위해 STATUS_IDX 증가시키고 LOCK 해제
            if (idx + 1 >= STATUSES.length) {
                statusRegistryService.setStatus(STATUS_IDX, "0");
            } else {
                statusRegistryService.increment(STATUS_IDX);
            }
            lockService.unlock(lockKey, lockValue);
        }
    }

    private void runDeleteExpiryDomain() {
        log.info("만료된 서브도메인 삭제 시작");

        LocalDate localDate = LocalDate.now();
        int deleted = 0;

        while(true) {
            List<HaveSubDomain> deleteDomains = haveSubDomainService.getExpiredSubDomains(localDate, 500);

            if (deleteDomains.isEmpty()) {
                break;
            }

            pdnsService.deleteSubRecords(deleteDomains);
            deleted += deleteDomains.size();
        }

        log.info("만료된 서브도메인 삭제 완료: {}개", deleted);
    }
}
