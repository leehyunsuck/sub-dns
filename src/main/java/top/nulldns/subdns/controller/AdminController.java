package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import top.nulldns.subdns.service.AdminService;

@RestController
@AllArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @PostMapping("/admin/deleteZone")
    public ResponseEntity<Void> deleteZone(String zone, HttpSession session) {
        if (zone == null || zone.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!adminService.isAdmin((Long) session.getAttribute("memberId"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean result = adminService.deleteEndsWithZone(zone);
        return result ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
