package top.nulldns.subdns.controller.view;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.domain.HaveSubDomainService;
import top.nulldns.subdns.service.domain.MemberService;

@Controller
@RequiredArgsConstructor
public class DomainController {

    private final MemberService memberService;
    private final HaveSubDomainService haveSubDomainService;

    @GetMapping("/domains")
    public String domains() {
        return "domainList";
    }

    @GetMapping("/domains/detail")
    public String domainDetail(@RequestParam(required = false) String subDomain,
                               @RequestParam(required = false) String zone,
                               HttpSession session,
                               Model model) {

        boolean isNew = true;

        // 도메인 상세 조회 시 권한 체크
        if (subDomain != null && zone != null) {
            Long memberId = (Long) session.getAttribute("memberId");
            if (memberId == null) {
                return "redirect:/login";
            }

            Member member = memberService.getMemberById(memberId);
            String fullDomain = subDomain + "." + zone;

            boolean isOwner = haveSubDomainService.isOwnerOfDomain(member, fullDomain);
            boolean canAdd = haveSubDomainService.canAddSubDomain(fullDomain);

            if (!isOwner && !canAdd) {
                return "redirect:/domains";
            }

            isNew = !isOwner;
        }

        model.addAttribute("subDomain", subDomain);
        model.addAttribute("zone", zone);
        model.addAttribute("isNew", isNew);
        return "domainDetail";
    }
}
