package top.nulldns.subdns.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.service.domain.HaveSubDomainService;

import java.util.List;

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

        List<HaveSubDomain> haveSubDomains = haveSubDomainService.getSubDomainsByZone(zone);
        haveSubDomainService.setDeletePending(haveSubDomains);
        pdnsService.deleteZone(zone); // 존 날리면 레코드도 다 날라감 (테이블 삭제)
        haveSubDomainService.deleteSubDomains(haveSubDomains); // HaveSubDomain 테이블에서 따로 삭제

        return true;
    }
}
