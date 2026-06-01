package top.nulldns.subdns.controller.view;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import top.nulldns.subdns.service.domain.CheckAdminService;

@Controller
@RequiredArgsConstructor
public class AdminController {
    private final CheckAdminService checkAdminService;

    @GetMapping("/admin")
    public String adminPage(HttpSession session, Model model) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null || !checkAdminService.isAdmin(memberId)) {
            return "redirect:/";
        }

        // GlobalConfig(@ControllerAdvice)에서 staticUrl, isLoggedIn, isAdmin, userId를 자동으로 넣어주므로
        // 여기서는 관리자 페이지 전용 데이터인 memberId만 추가하면 됩니다.
        model.addAttribute("memberId", memberId);

        return "admin";
    }
}
