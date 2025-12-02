package top.nulldns.subdns.service;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.repository.MemberRepository;
import top.nulldns.subdns.util.PDNSRecordValidator;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PDNSService {

    private final MemberRepository memberRepository;
    private final RestClient.Builder restClientBuilder;

    @Value("${pdns.url}")
    private String pdnsUrl;

    @Value("${pdns.api-key}")
    private String pdnsApiKey;

    private RestClient restClient;

    @Getter
    private List<PDNSDto.ZoneName> cachedZoneNames;

    @PostConstruct
    private void init() {
        this.restClient = restClientBuilder
                .baseUrl(pdnsUrl)
                .defaultHeader("X-API-Key", pdnsApiKey)
                .build();

        this.cachedZoneNames = getZoneNameList();
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void sheduledRefresh() {
        this.cachedZoneNames = this.getZoneNameList();
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

    public ResponseEntity<Void> addRecord(String zone, String subDomain, String type, String content, HttpSession session) {
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
            if (exitsCNAME && deleteAllSubRecords(zone, subDomain).getStatusCode().isError()) {
                return ResponseEntity.internalServerError().build();
            }

            // 최대 레코드 개수 체크 필요 --------------------------------------

            return modifyRecord(zone, subDomain, type, content, "REPLACE");
        } catch (Exception e) {
            log.error("Error adding record", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<Void> deleteAllSubRecords(String zone, String subDomain) {
        List<PDNSDto.SearchResult> records = searchResultList(subDomain + "." + zone);

        try {
            for (PDNSDto.SearchResult record : records) {
                if (deleteRecord(zone, subDomain, record.getType()).getStatusCode().isError()) {
                    throw new Exception("Failed to delete record: " + record);
                }
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // 일부 데이터만 삭제하다 멈춘 경우를 대비하여
            // 기존 데이터 다시 덮어쓰기
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 레코드 삭제
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @return ResponseEntity<Void> 성공 여부
     */
    public ResponseEntity<Void> deleteRecord(String zone, String subDomain, String type) {
        try {
            return modifyRecord(zone, subDomain, type, null, "DELETE");
        } catch (Exception e) {
            log.warn("레코드 삭제중 에러 발생", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 레코드 수정/삭제 공통 메서드
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @param content       레코드 값 (삭제 시 null)
     * @param action        REPLACE / DELETE
     * @return ResponseEntity<Void> 성공 여부
     */
    private ResponseEntity<Void> modifyRecord(String zone, String subDomain, String type, String content, String action) {
        if (zone.isEmpty() || subDomain.isEmpty() || type.isEmpty() || action.isEmpty()) {  // 필수 파라미터 체크
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        zone      = zone.toLowerCase();
        subDomain = subDomain.toLowerCase();
        type      = type.toUpperCase();
        action    = action.toUpperCase();

        if (!action.equals("REPLACE") && !action.equals("DELETE")) { // REPLACE, DELETE 외 다른 경우 들어올 수 없지만 혹시 모르니
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!PDNSRecordValidator.isValidLabel(subDomain)) { // subDomain 유효성 체크
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        PDNSDto.Record record = null;

        if (action.equals("REPLACE")) {
            if (content == null || content.isEmpty()) { // 등록/수정은 content 값이 반드시 필요
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (!PDNSRecordValidator.validate(type, content)) { // type 별 content 유효성 체크
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            record = PDNSDto.Record.builder()
                    .content(content)
                    .build();
        } else {
            if (!PDNSRecordValidator.isValidType(type)) { // 삭제는 type 값만 유효성 체크
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
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

    /**
     * Zone Name 목록 반환
     * @return List<PDNSDto.ZoneName>
     */
    private List<PDNSDto.ZoneName> getZoneNameList() {
        List<PDNSDto.ZoneName> zones = new ArrayList<>();
        try {
            zones = restClient.get()
                    .uri("zones")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PDNSDto.ZoneName>>() {});
        } catch (Exception e) {
            log.error("Zone name 조회 중 에러 발생", e);
            return this.cachedZoneNames;
        }

        // PowerDNS API에서 Zone 정보 가져오면 마지막 문자가 . 으로 끝남
        for (PDNSDto.ZoneName zone : zones) {
            String name = zone.getName();
            if (name.endsWith(".")) {
                zone.setName(name.substring(0, name.length() - 1));
            }
        }

        return zones;
    }
}
