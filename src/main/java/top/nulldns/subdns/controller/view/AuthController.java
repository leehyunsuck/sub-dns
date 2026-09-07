package top.nulldns.subdns.controller.view;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.domain.MemberService;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final MemberService memberService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/run-login")
    public String loginSuccess(OAuth2AuthenticationToken token, HttpSession session, Model model) {
        String provider = token.getAuthorizedClientRegistrationId();
        String providerId = token.getName();

        Member member = memberService.findMember(provider, providerId);

        if (member != null) {
            if (member.isBanned()) {
                return "redirect:/banned";
            }
            session.setAttribute("id", member.getProvider() + member.getProviderId());
            session.setAttribute("memberId", member.getId());
            return "redirect:/";
        } else {
            model.addAttribute("provider", provider);
            model.addAttribute("providerId", providerId);
            return "signup";
        }
    }

    @PostMapping("/signup")
    public String signupPost(OAuth2AuthenticationToken token, HttpSession session) {
        if (token == null) {
            return "redirect:/login";
        }

        String provider = token.getAuthorizedClientRegistrationId();
        String providerId = token.getName();

        // 기존 가입 여부 재확인 (새로고침 중복 가입 방지)
        Member member = memberService.findMember(provider, providerId);
        if (member == null) {
            member = memberService.signup(provider, providerId);
        }

        if (member.isBanned()) {
            return "redirect:/banned";
        }

        session.setAttribute("id", member.getProvider() + member.getProviderId());
        session.setAttribute("memberId", member.getId());

        return "redirect:/";
    }


}
