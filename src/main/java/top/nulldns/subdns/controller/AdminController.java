package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import top.nulldns.subdns.service.AdminService;
import top.nulldns.subdns.service.CheckAdminService;

@Slf4j
@RestController
@AllArgsConstructor
public class AdminController {
    private final CheckAdminService checkAdminService;
    private final AdminService adminService;

    @PostMapping("/admin/deleteZone")
    public ResponseEntity<Void> deleteZone(String zone, HttpSession session) {
        if (zone == null || zone.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!checkAdminService.isAdmin((Long) session.getAttribute("memberId"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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
