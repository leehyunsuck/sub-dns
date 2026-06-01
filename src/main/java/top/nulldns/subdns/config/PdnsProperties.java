package top.nulldns.subdns.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "pdns")
@Getter
@Setter
public class PdnsProperties {
    private String url;
    private String apiKey;
}
