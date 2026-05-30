package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.nulldns.subdns.service.facade.AdminService;
import top.nulldns.subdns.service.domain.CheckAdminService;

@Slf4j
@RestController("/admin")
@RequiredArgsConstructor
public class AdminRestController {
    private final CheckAdminService checkAdminService;
    private final AdminService adminService;

    @PostMapping("/deleteZone/{zone}/{code}")
    public ResponseEntity<Void> deleteZone(@PathVariable String zone, HttpSession session) {
        if (zone == null || zone.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!checkAdminService.isAdmin((Long) session.getAttribute("memberId"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.status(HttpStatus.PROCESSING).build();

        // 추 후 검증 코드 로직 추가해서 기능 사용 가능하게 하기
//
//        try {
//            if (adminService.deleteEndsWithZone(zone)) {
//                return ResponseEntity.ok().build();
//            }
//        } catch (Exception e) {
//            log.error("Error deleting zone and subdomains for zone {}: {}", zone, e.getMessage());
//        }
//
//        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    // 도메인 검색
    //@GetMapping("/searchDomains/{domain}")
    //public ResponseEntity<Void> searchDomains(@PathVariable String domain) {
    //    return ResponseEntity.ok().build();
    //}

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
