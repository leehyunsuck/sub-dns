package top.nulldns.subdns.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalConfig {

    @Value("${storage.static-url}")
    private String staticUrl;

    @ModelAttribute("staticUrl")
    public String staticUrl() {
        return staticUrl;
    }

    @ModelAttribute("isLoggedIn")
    public boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("memberId") != null;
    }

    @ModelAttribute("userId")
    public String userId(HttpSession session) {
        return (String) session.getAttribute("id");
    }
}