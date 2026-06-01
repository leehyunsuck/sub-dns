package top.nulldns.subdns.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.nulldns.subdns.common.interceptor.AdminInterceptor;
import top.nulldns.subdns.common.interceptor.BannedInterceptor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final BannedInterceptor bannedInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(bannedInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/banned", "/login", "/run-login", "/logout", "/css/**", "/js/**", "/assets/**", "/*.txt", "/*.xml", "/*.html");

        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**");
    }
}
