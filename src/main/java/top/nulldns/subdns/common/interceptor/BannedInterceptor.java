package top.nulldns.subdns.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.domain.MemberService;

@Component
@RequiredArgsConstructor
public class BannedInterceptor implements HandlerInterceptor {

    private final MemberService memberService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Long memberId = (Long) session.getAttribute("memberId");
            if (memberId != null) {
                Member member = memberService.getMemberById(memberId);
                if (member.isBanned()) {
                    session.invalidate();
                    response.sendRedirect("/banned");
                    return false;
                }
            }
        }
        return true;
    }
}
