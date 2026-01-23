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
import top.nulldns.subdns.service.AdminService;
import top.nulldns.subdns.service.CheckAdminService;
import top.nulldns.subdns.service.HaveSubDomainService;
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
    private final HaveSubDomainService haveSubDomainService;
    private final CheckAdminService checkAdminService;

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("memberId") != null && session.getAttribute("id") != null;
    }

    @PatchMapping("/update-record/{subDomain}/{zone}")
    public ResponseEntity<Void> updateRecord(HttpSession session, @PathVariable String subDomain, @PathVariable String zone) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // 로그인 필요
        }

        Long memberId = (Long) session.getAttribute("memberId");
        String fullDomain = subDomain + "." + zone;

        boolean isOwner = haveSubDomainRepository.existsHaveSubDomainByMemberIdAndFullDomain(memberId, fullDomain);
        if (!isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 권한 없음
        }

        ResultMessageDTO<Integer> renewResult = haveSubDomainService.renew(memberId, fullDomain);
        int code = renewResult.getData();
        log.debug("도메인 갱신 요청 결과 코드: {}", code);

        if (code == 404) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // 도메인 없음
        } else if (code == 400) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build(); // 갱신 불가
        }

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete-record/{subDomain}/{zone}")
    public ResponseEntity<Void> deleteRecord(HttpSession session, @PathVariable String subDomain, @PathVariable String zone) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long memberId = (Long) session.getAttribute("memberId");
        if (!pdnsService.isDomainOwner(memberId, subDomain + "." + zone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            pdnsService.deleteAllSubRecords(subDomain, zone, memberId);
        } catch (Exception e) {
            log.error("서브도메인 삭제 요청 중 에러 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
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

        List<PDNSDto.SearchResult> searchResult = pdnsService.searchResultList(fullDomain);
        if (searchResult.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok(searchResult);
    }

    @GetMapping("/available-domains/{subDomain}")
    public ResponseEntity<PDNSDto.CanAddSubDomainZones> availableDomains(@PathVariable String subDomain, HttpSession session) {
        boolean isAdmin = checkAdminService.isAdmin((Long) session.getAttribute("memberId"));

        boolean isAllowDomain = isAdmin ? PDNSRecordValidator.isValidLabelAdmin(subDomain)
                                        : PDNSRecordValidator.isValidLabel(subDomain);

        List<PDNSDto.ZoneName> zoneNameList = pdnsService.getCachedZoneNames();

        PDNSDto.CanAddSubDomainZones canAddSubDomainZones = PDNSDto.CanAddSubDomainZones.builder()
                .subDomain(subDomain)
                .zones(new ArrayList<>())
                .build();

        for (PDNSDto.ZoneName zone : zoneNameList) {
            String  zoneName = zone.getName(),
                    fullDomain = subDomain + "." + zoneName;

            boolean canAdd = isAllowDomain && !haveSubDomainRepository.existsByFullDomain(fullDomain);

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
    public ResponseEntity<Void> addRecord(@RequestBody PDNSDto.AddRecordRequest request, HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long memberId = (Long) session.getAttribute("memberId");

        try {
            pdnsService.addRecord(request.getSubDomain(), request.getZone(), request.getType(), request.getContent(), memberId);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // 레코드 수 제한 초과
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

