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
    private final HaveSubDomainService haveSubDomainService;
    private final PDNSService pdnsService;

    public boolean deleteEndsWithZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return false;
        }

        haveSubDomainService.deleteEndsWithDomain(zone);
        pdnsService.deleteZone(zone);

        return true;
    }
}
