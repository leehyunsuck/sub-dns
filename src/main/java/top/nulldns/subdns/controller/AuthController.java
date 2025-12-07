package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.AuthService;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @GetMapping("/login-success")
    public String loginSuccess(OAuth2AuthenticationToken token, HttpSession session) {
        String provider = token.getAuthorizedClientRegistrationId();
        String providerId = token.getPrincipal().getAttribute("id").toString();

        Member member = authService.loginOrSignup(provider, providerId);
        session.setAttribute("id", member.getProvider() + member.getProviderId());
        session.setAttribute("memberId", member.getId());

        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}
