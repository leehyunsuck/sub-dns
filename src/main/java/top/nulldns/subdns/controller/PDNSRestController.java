package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.dto.ResultMessageDTO;
import top.nulldns.subdns.entity.HaveSubDomain;
import top.nulldns.subdns.repository.HaveSubDomainRepository;
import top.nulldns.subdns.service.PDNSService;
import top.nulldns.subdns.util.PDNSRecordValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PDNSRestController {
    private final PDNSService pdnsService;

    private final HaveSubDomainRepository haveSubDomainRepository;

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("memberId") != null && session.getAttribute("id") != null;
    }

    @GetMapping("/my-domains")
    public ResponseEntity<List<PDNSDto.HaveDomain>> myDomains(HttpSession session) {
        if (!isLoggedIn(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<PDNSDto.HaveDomain> haveDomains = new ArrayList<>();

        try {
            List<HaveSubDomain> haveSubDomainList = haveSubDomainRepository.findByMemberId((Long) session.getAttribute("memberId"));
            if (haveSubDomainList.isEmpty()) {
                throw new NoSuchElementException();
            }

            for (HaveSubDomain haveSubDomain : haveSubDomainList) {
                String[] split = haveSubDomain.getFullDomain().split("\\.", 2);

                String subDomain = split[0],
                       zone  = split[1];

                haveDomains.add(
                        PDNSDto.HaveDomain.builder()
                                .subDomain(subDomain)
                                .zone(zone)
                                .build()
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

        if (haveSubDomainRepository.findByMemberIdAndFullDomain((Long) session.getAttribute("memberId"), fullDomain).isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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

            boolean canAdd = !isBlockedDomain && pdnsService.searchResultList(fullDomain).getData().isEmpty();

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

