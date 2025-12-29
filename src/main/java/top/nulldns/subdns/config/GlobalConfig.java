package top.nulldns.subdns.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalConfig {

    @Value("${storage.static-url}")
    private String staticUrl;

    @ModelAttribute("staticUrl")
    public String staticUrl() {
        return staticUrl;
    }
}