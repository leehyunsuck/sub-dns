package top.nulldns.subdns.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "storage")
@Getter
@Setter
public class StorageProperties {
    /**
     * 정적 리소스 (Object Storage) URL
     */
    private String staticUrl;
}
