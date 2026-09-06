package top.nulldns.subdns.service.domain;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.config.finalconfig.Event;
import top.nulldns.subdns.config.finalconfig.PubSubChannel;
import top.nulldns.subdns.dao.Admin;
import top.nulldns.subdns.repository.AdminRepository;
import top.nulldns.subdns.service.infra.InstanceIdProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// DB Service
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckAdminService {
    private final AdminRepository adminRepository;
    private volatile Set<Long> adminSet;

    private final StringRedisTemplate stringRedisTemplate;
    private final InstanceIdProvider instanceIdProvider;

    @PostConstruct
    private void init() {
        adminSet = new HashSet<>();
        refreshAdmins(false);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void scheduledRefresh() {
        refreshAdmins(false);
    }

    public boolean isAdmin(Long memberId) {
        return memberId != null && adminSet.contains(memberId);
    }

    public void refreshAdmins(boolean isSendEvent) {
        if (isSendEvent) {
            stringRedisTemplate.convertAndSend(PubSubChannel.REFRESH.name(), Event.REFRESH_ADMINS.name() + ":" + instanceIdProvider.getInstanceId());
        }
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                List<Admin> admins = adminRepository.findAll();
    
                Set<Long> newAdminSet = new HashSet<>();
                for (Admin admin : admins) {
                    newAdminSet.add(admin.getMember().getId());
                }
                adminSet = Set.copyOf(newAdminSet);
                return;
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.error("관리자 목록 갱신 최종({}회) 실패: {}", maxRetries, e.toString());
                } else {
                    log.warn("관리자 목록 갱신 중 에러 발생 (재시도 {}/{}): {}", i + 1, maxRetries, e.toString());
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }
}
