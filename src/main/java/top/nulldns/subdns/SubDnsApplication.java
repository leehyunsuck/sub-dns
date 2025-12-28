package top.nulldns.subdns;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SubDnsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubDnsApplication.class, args);
    }

}
