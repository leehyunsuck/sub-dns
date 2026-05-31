package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.domain.HaveSubDomainService;
import top.nulldns.subdns.service.domain.MemberService;

@Controller
@RequiredArgsConstructor
public class IndexController {

    private final MemberService memberService;
    private final HaveSubDomainService haveSubDomainService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/domains")
    public String domains() {
        return "domainList";
    }

    @GetMapping("/domains/detail")
    public String domainDetail(@RequestParam(required = false) String subDomain,
                               @RequestParam(required = false) String zone,
                               HttpSession session,
                               Model model) {
        
        // 도메인 상세 조회 시 권한 체크
        if (subDomain != null && zone != null) {
            Long memberId = (Long) session.getAttribute("memberId");
            if (memberId == null) {
                return "redirect:/login";
            }

            Member member = memberService.getMemberById(memberId);
            String fullDomain = subDomain + "." + zone;

            if (!haveSubDomainService.isOwnerOfDomain(member, fullDomain)) {
                return "redirect:/domains";
            }
        }

        model.addAttribute("subDomain", subDomain);
        model.addAttribute("zone", zone);
        return "domainDetail";
    }

    @GetMapping("/banned")
    public String banned() {
        return "banned";
    }
}
