package top.nulldns.subdns.controller.api;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.facade.AdminService;
import top.nulldns.subdns.service.domain.CheckAdminService;

import java.util.List;
import java.util.Map;
import java.util.Set;

import top.nulldns.subdns.dto.AdminDomainDto;
import top.nulldns.subdns.dto.AdminMemberDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminRestController {
    private final AdminService adminService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(adminService.getStatistics());
    }

    // --- 유저 관리 ---
    @GetMapping("/users")
    public ResponseEntity<List<AdminMemberDto>> getUsers(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(adminService.searchMembers(query));
    }

    @GetMapping("/users/{memberId}/domains")
    public ResponseEntity<List<AdminDomainDto>> getUserDomains(@PathVariable Long memberId) {
        return ResponseEntity.ok(adminService.getDomainsByMember(memberId));
    }

    @PostMapping("/users/{memberId}/ban")
    public ResponseEntity<Void> banUser(@PathVariable Long memberId, @RequestParam boolean banned) {
        adminService.setMemberBanned(memberId, banned);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{memberId}/maxRecords")
    public ResponseEntity<Void> updateMaxRecords(@PathVariable Long memberId, @RequestParam int maxRecords) {
        adminService.updateMemberMaxRecords(memberId, maxRecords);
        return ResponseEntity.ok().build();
    }

    // --- 도메인 관리 ---
    @GetMapping("/domains")
    public ResponseEntity<Page<AdminDomainDto>> getDomains(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 10, sort = "expiryDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(adminService.searchDomainsPaged(query, pageable));
    }

    @PostMapping("/domains/{domainId}/expiry")
    public ResponseEntity<Void> updateExpiry(@PathVariable Long domainId, @RequestParam String expiryDate) {
        adminService.updateExpiryDate(domainId, java.time.LocalDate.parse(expiryDate));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/domains/transfer")
    public ResponseEntity<Void> transferDomain(@RequestParam String fullDomain, @RequestParam Long newMemberId) {
        adminService.transferDomainOwnership(fullDomain, newMemberId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/domains")
    public ResponseEntity<Void> deleteDomain(@RequestParam String fullDomain) {
        adminService.deleteDomainForce(fullDomain);
        return ResponseEntity.ok().build();
    }

    // --- 존 관리 ---
    @GetMapping("/zones")
    public ResponseEntity<Set<top.nulldns.subdns.dto.PDNSDto.ZoneName>> getZones() {
        return ResponseEntity.ok(adminService.getZones());
    }

    @PostMapping("/zones")
    public ResponseEntity<Void> addZone(@RequestParam String zone) {
        adminService.addZone(zone);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/deleteZone/{zone}/{code}")
    public ResponseEntity<Void> deleteZone(@PathVariable String zone, @PathVariable String code) {
        // 간단한 검증 (실제론 code 검증 로직 추가 필요)
        if (!"DELETE".equals(code)) return ResponseEntity.badRequest().build();

        try {
            if (adminService.deleteEndsWithZone(zone)) {
                return ResponseEntity.ok().build();
            }
        } catch (Exception e) {
            log.error("Error deleting zone and subdomains for zone {}: {}", zone, e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}

/*
필요 기능

- 도메인 검색 (사용자 번호, 도메인 등으로) 및 제거 수정
- 존 제거
- 존 등록
- PENDING 상태 도메인들 확인 및 일부 또는 전체 즉시 처리
- 삭제 예정 도메인들 확인 및 일부 또는 전체 즉시 처리
- 계정 정지
- 계정 복구
- 특정 도메인 제거
- 특정 사용자가 보유한 도메인 전체 제거

 */
