package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.repository.HaveSubDomainRepository;
import top.nulldns.subdns.service.PDNSService;
import top.nulldns.subdns.util.PDNSRecordValidator;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PDNSRestController {
    private final PDNSService pdnsService;

    private final HaveSubDomainRepository haveSubDomainRepository;

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("memberId") != null && session.getAttribute("id") != null;
    }

    @GetMapping("/get-records/{fullDomain}")
    public ResponseEntity<List<PDNSDto.SearchResult>> getRecords(@PathVariable String fullDomain, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (haveSubDomainRepository.findByMemberIdAndFullDomain((Long) session.getAttribute("memberId"), fullDomain).isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(pdnsService.searchResultList(fullDomain));
    }

    @GetMapping("/available-domains/{subDomain}")
    public ResponseEntity<PDNSDto.CanAddSubDomainZones> availableDomains(@PathVariable String subDomain) {
        boolean isBlockedDomain = !PDNSRecordValidator.isValidLabel(subDomain);

        List<PDNSDto.ZoneName> zoneNameList = pdnsService.getCachedZoneNames();

        PDNSDto.CanAddSubDomainZones canAddSubDomainZones = PDNSDto.CanAddSubDomainZones.builder()
                .subDomain(subDomain)
                .zones(new ArrayList<>())
                .build();

        for (PDNSDto.ZoneName zone : zoneNameList) {
            String  zoneName = zone.getName(),
                    fullDomain = subDomain + "." + zoneName;

            boolean canAdd = isBlockedDomain ? false : pdnsService.searchResultList(fullDomain).isEmpty();

            canAddSubDomainZones.getZones().add(
                    PDNSDto.ZoneAddCapability.builder()
                            .name(zoneName)
                            .canAdd(canAdd)
                            .build()
            );
        }

        return ResponseEntity.ok(canAddSubDomainZones);
    }
}

/*
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) {
    // id 변수에 URL의 {id} 값이 들어감
}
 */

