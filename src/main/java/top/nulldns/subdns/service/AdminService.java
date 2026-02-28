package top.nulldns.subdns.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.service.dbservice.HaveSubDomainService;

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

        pdnsService.deleteZone(zone);
        haveSubDomainService.deleteEndsWithDomain(zone);

        return true;
    }
}
