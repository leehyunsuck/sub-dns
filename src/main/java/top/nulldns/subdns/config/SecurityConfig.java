package top.nulldns.subdns.config;

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
                        // [1] 정적 리소스 허용 (css, js, images, webjars, favicon 등 자동 포함)
                        // resources/static/css/** -> /css/** 로 자동 매핑됨
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()

                        // [2] 메인 페이지 및 로그인 관련 경로 허용
                        .requestMatchers("/", "/index.html", "/login/**", "/test/**", "/pages/**", "/assets/**").permitAll()

                        // [3] API는 상황에 따라 선택 (지금은 다 열어둠, 필요 시 .authenticated()로 변경)
                        .requestMatchers("/api/**").permitAll()

                        // [4] 그 외 모든 요청은 인증(로그인) 필요
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .defaultSuccessUrl("/login-success", true) // 로그인 성공 시 메인으로
                );

        return http.build();
    }
}