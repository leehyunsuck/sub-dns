package top.nulldns.subdns.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import top.nulldns.subdns.service.domain.CheckAdminService;

@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final CheckAdminService checkAdminService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Long memberId = (session != null) ? (Long) session.getAttribute("memberId") : null;

        if (memberId == null || !checkAdminService.isAdmin(memberId)) {
            String acceptHeader = request.getHeader("Accept");
            
            // API 요청인 경우 403 Forbidden 반환
            if (acceptHeader != null && acceptHeader.contains("application/json")) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.getWriter().write("{\"error\": \"Forbidden\", \"message\": \"Access Denied\"}");
            } else {
                // 일반 페이지 요청인 경우 메인으로 리다이렉트
                response.sendRedirect("/");
            }
            return false;
        }

        return true;
    }
}
