package top.nulldns.subdns.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.util.PDNSRecordValidator;

import java.util.Collections;
import java.util.List;

@Service
public class PDNSService {
    private final RestClient restClient;
    public PDNSService(RestClient.Builder builder,
                       @Value("${pdns.url}") String url,
                       @Value("${pdns.api-key}") String apikey) {

        this.restClient = builder
                .baseUrl(url)
                .defaultHeader("X-API-Key", apikey)
                .build();
    }

    public List<PDNSDto.ZoneName> getZoneNameList() {
        return restClient.get()
                .uri("zones")
                .retrieve()
                .body(new ParameterizedTypeReference<List<PDNSDto.ZoneName>>() {});
    }

    public List<PDNSDto.SearchResult> searchResultList(String fullDomain) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search-data")
                        .queryParam("q", fullDomain)  // 혹은 서브도메인만
                        .queryParam("object_type", "record")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<PDNSDto.SearchResult>>() {});
    }

    public ResponseEntity<Void> addRecord(String zone, String subDomain, String type, String content) {
        boolean exitsCNAME = false;
        if (type.equalsIgnoreCase("CNAME")) exitsCNAME = true;
        List<PDNSDto.SearchResult> existingRecords = searchResultList(subDomain + "." + zone);
        for (PDNSDto.SearchResult record : existingRecords) {
            if (record.getType().equalsIgnoreCase("CNAME")) {
                exitsCNAME = true;
                break;
            }
        }

        try {
            if (exitsCNAME) {
                if (deleteAllSubRecords(zone, subDomain).getStatusCode().isError()) {
                    throw new Exception("Failed to delete records before adding new CNAME.");
                }
            }

            return modifyRecord(zone, subDomain, type, content, "REPLACE");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<Void> deleteAllSubRecords(String zone, String subDomain) {
        try {
            List<PDNSDto.SearchResult> records = searchResultList(subDomain + "." + zone);
            for (PDNSDto.SearchResult record : records) {
                if (deleteRecord(zone, subDomain, record.getType()).getStatusCode().isError()) {
                    throw new Exception("Failed to delete record: " + record);
                }
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<Void> deleteRecord(String zone, String subDomain, String type) {
        try {
            return modifyRecord(zone, subDomain, type, null, "DELETE");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 내부에서만 씀 에외처리는 호출하는 쪽에서
    private ResponseEntity<Void> modifyRecord(String zone, String subDomain, String type, String content, String action) {
        zone = zone.toLowerCase();
        subDomain = subDomain.toLowerCase();
        type = type.toUpperCase();
        action = action.toUpperCase();

        if (!action.equals("REPLACE") && !action.equals("DELETE")) throw new IllegalArgumentException("Invalid action: " + action);
        if (!PDNSRecordValidator.isValidLabel(subDomain))         throw new IllegalArgumentException("Invalid subDomain: " + subDomain);

        PDNSDto.Record record = null;
        if (action.equals("REPLACE")) {
            if (content == null || content.isEmpty())          throw new IllegalArgumentException("Content cannot be null or empty for REPLACE action");
            if (!PDNSRecordValidator.validate(type, content)) throw new IllegalArgumentException("Invalid type or content: " + type + ", " + content);
            record = PDNSDto.Record.builder()
                    .content(content)
                    .build();
        } else {
            if (!PDNSRecordValidator.isValidType(type)) throw new IllegalArgumentException("Invalid type: " + type);
        }

        PDNSDto.Rrset rrset = PDNSDto.Rrset.builder()
                .name(subDomain + "." + zone + ".")
                .type(type)
                .changeType(action)
                .records(record == null ? List.of() : List.of(record))
                .build();

        record PatchPayLoad(List<PDNSDto.Rrset> rrsets) {}

        return restClient.patch()
                .uri("/zones/" + zone)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PatchPayLoad(List.of(rrset)))
                .retrieve()
                .toBodilessEntity();
    }
}
