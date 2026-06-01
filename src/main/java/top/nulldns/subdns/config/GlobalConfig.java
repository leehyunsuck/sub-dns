package top.nulldns.subdns.config;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import top.nulldns.subdns.service.domain.CheckAdminService;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalConfig {

    private final CheckAdminService checkAdminService;
    private final StorageProperties storageProperties;

    @ModelAttribute("staticUrl")
    public String staticUrl() {
        return storageProperties.getStaticUrl();
    }

    @ModelAttribute("isLoggedIn")
    public boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("memberId") != null;
    }

    @ModelAttribute("userId")
    public String userId(HttpSession session) {
        return (String) session.getAttribute("id");
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            return false;
        }
        return checkAdminService.isAdmin(memberId);
    }
}