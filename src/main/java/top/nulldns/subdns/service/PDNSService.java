package top.nulldns.subdns.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import top.nulldns.subdns.dto.PDNSDto;

import java.util.Collections;
import java.util.List;

@Service
public class PDNSService {
    private final RestClient restClient;

    private String serverId = "localhost";

    public PDNSService(RestClient.Builder builder,
                       @Value("${pdns.url}") String url,
                       @Value("${pdns.api-key}") String apikey) {

        this.restClient = builder.baseUrl(url)
                .defaultHeader("X-API-Key", apikey)
                .build();
    }

    public List<PDNSDto.ZoneName> getZoneNameList() {
        List<PDNSDto.ZoneName> zones = restClient.get()
                .uri("zones")
                .retrieve()
                .body(new ParameterizedTypeReference<List<PDNSDto.ZoneName>>() {});

        if (zones == null) {
            zones = Collections.emptyList();
        }

        return zones;
    }
}
