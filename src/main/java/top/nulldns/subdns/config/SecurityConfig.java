package top.nulldns.subdns.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers("/", "/index.html").permitAll()
                        .requestMatchers("/api/available-domains/**", "/api/me").permitAll()
                        .requestMatchers("/robots.txt", "/sitemap.xml", "/ads.txt").permitAll()
                        .requestMatchers("naver114d2366f4787248382a17a44f17b76b.html", "naver114d2366f4787248382a17a44f17b76b").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            String acceptHeader = request.getHeader("Accept");

                            // 브라우저를 통한 직접 접근 (HTML을 요구하는 경우)
                            if (acceptHeader != null && acceptHeader.contains("text/html")) {
                                response.sendRedirect("/?login=required");
                            } else {
                                // API (Fetch/Ajax) 요청인 경우 401 반환
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"로그인이 필요합니다.\"}");
                            }
                        })
                )
                .oauth2Login(oauth2 -> oauth2
                        .defaultSuccessUrl("/run-login", true) // 로그인 성공 시 메인으로
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }
}