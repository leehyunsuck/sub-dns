package top.nulldns.subdns.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.dao.Admin;
import top.nulldns.subdns.repository.AdminRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {
    private final AdminRepository adminRepository;
    private Set<Long> adminSet;

    @PostConstruct
    private void init() {
        adminSet = new HashSet<>();
        refreshAdminSet();
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void sheduledRefresh() {
        refreshAdminSet();
    }

    public boolean isAdmin(Long memberId) {
        return memberId != null && adminSet.contains(memberId);
    }

    private void refreshAdminSet() {
        try {
            List<Admin> admins = adminRepository.findAll();

            Set<Long> newAdminSet = new HashSet<>();
            for (Admin admin : admins) {
                newAdminSet.add(admin.getMember().getId());
            }
            adminSet = Set.copyOf(newAdminSet);
        } catch (Exception e) {
            log.error("관리자 목록 갱신 중 에러 발생", e);
        }
    }
}
