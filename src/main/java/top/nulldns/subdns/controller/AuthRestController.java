package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.nulldns.subdns.dto.IdDto;
import top.nulldns.subdns.service.AuthService;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
@Slf4j
public class AuthRestController {
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<IdDto> me(HttpSession session) {
        String id = (String) session.getAttribute("id");

        return id == null ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build() : ResponseEntity.ok(new IdDto(id));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/leave")
    public ResponseEntity<Void> leave(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            authService.deleteUserAndData(memberId);
        } catch (Exception e) {
            log.error("회원 탈퇴 중 오류 발생", e);
            log.error("탈퇴 시도한 회원 ID: {}", memberId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        session.invalidate();

        return ResponseEntity.ok().build();
    }
}
