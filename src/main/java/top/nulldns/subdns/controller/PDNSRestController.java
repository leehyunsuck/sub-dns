package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.nulldns.subdns.dto.HaveDomainsDto;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.dto.ResultMessageDTO;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.repository.HaveSubDomainRepository;
import top.nulldns.subdns.service.PDNSService;
import top.nulldns.subdns.util.PDNSRecordValidator;

import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api")
public class PDNSRestController {
    private final PDNSService pdnsService;

    private final HaveSubDomainRepository haveSubDomainRepository;

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("memberId") != null && session.getAttribute("id") != null;
    }

    @DeleteMapping("/delete-record/{subDomain}/{zone}")
    public ResponseEntity<Void> deleteRecord(HttpSession session, @PathVariable String subDomain, @PathVariable String zone) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isHaveDomain = haveSubDomainRepository.existsHaveSubDomainByMemberIdAndFullDomain((Long) session.getAttribute("memberId"), subDomain + "." + zone);
        if (!isHaveDomain) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            if (!pdnsService.deleteAllSubRecords(subDomain, zone, session).isPass()) {
                throw new Exception("도메인 삭제 실패");
            }

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            log.error("도메인 삭제 요청 중 에러 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/my-domains")
    public ResponseEntity<List<HaveDomainsDto>> myDomains(HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<HaveDomainsDto> haveDomains = new ArrayList<>();

        try {
            Long id = (Long) session.getAttribute("memberId");
            List<HaveSubDomain> haveSubDomains = haveSubDomainRepository.findDistinctByMemberId(id);

            if (haveSubDomains.isEmpty()) {
                throw new NoSuchElementException();
            }

            for (HaveSubDomain subDoamin: haveSubDomains) {
                String[] domainInfo = subDoamin.getFullDomain().split("\\.", 2);
                haveDomains.add(
                        new HaveDomainsDto(
                                domainInfo[0],
                                domainInfo[1],
                                subDoamin.getExpiryDate()
                        )
                );
            }

            return ResponseEntity.ok(haveDomains);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/get-records/{fullDomain}")
    public ResponseEntity<List<PDNSDto.SearchResult>> getRecords(@PathVariable String fullDomain, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ResultMessageDTO<List<PDNSDto.SearchResult>> searchResult = pdnsService.searchResultList(fullDomain);
        if (!searchResult.isPass()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok(searchResult.getData());
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

            boolean canAdd = !isBlockedDomain && !haveSubDomainRepository.existsByFullDomain(fullDomain);

            canAddSubDomainZones.getZones().add(
                    PDNSDto.ZoneAddCapability.builder()
                            .name(zoneName)
                            .canAdd(canAdd)
                            .build()
            );
        }

        return ResponseEntity.ok(canAddSubDomainZones);
    }

    @PostMapping("/add-record")
    public ResponseEntity<String> addRecord(@RequestBody PDNSDto.AddRecordRequest request, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ResultMessageDTO<Void> result = pdnsService.addRecord(request.getSubDomain(), request.getZone(), request.getType(), request.getContent(), session);
        if (result.isPass()) {
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.getMessage());
    }
}

