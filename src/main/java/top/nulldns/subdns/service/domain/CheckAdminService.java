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
            stringRedisTemplate.convertAndSend(PubSubChannel.REFRESH.name(), Event.REFRESH_ADMINS.name());
        }
        try {
            List<Admin> admins = adminRepository.findAll();

            Set<Long> newAdminSet = new HashSet<>();
            for (Admin admin : admins) {
                newAdminSet.add(admin.getMember().getId());
            }
            adminSet = Set.copyOf(newAdminSet);
        } catch (Exception e) {
            log.error("관리자 목록 갱신 중 에러 발생: {}", e.toString());
        }
    }
}
