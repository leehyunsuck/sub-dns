package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.nulldns.subdns.dto.IdDto;

@RestController
@RequestMapping("/api")
public class AuthRestController {
    @GetMapping("/me")
    public ResponseEntity<IdDto> me(HttpSession session) {
        String id = (String) session.getAttribute("id");

        return id == null ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build() : ResponseEntity.ok(new IdDto(id));
    }
}
