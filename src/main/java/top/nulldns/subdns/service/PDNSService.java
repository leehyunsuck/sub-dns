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
import top.nulldns.subdns.entity.HaveSubDomain;
import top.nulldns.subdns.entity.Member;
import top.nulldns.subdns.repository.HaveSubDomainRepository;
import top.nulldns.subdns.repository.MemberRepository;
import top.nulldns.subdns.util.PDNSRecordValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PDNSService {

    private final MemberRepository memberRepository;
    private final HaveSubDomainRepository haveSubDomainRepository;
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

    /**
     * 레코드 추가
     * @param zone      nulldns.top, example.com 등
     * @param subDomain example, www 등
     * @param type      A, CNAME, TXT 등
     * @param content   레코드 값
     * @param session   HttpSession
     * @return ResponseEntity<Void> 성공 여부
     */
    public ResponseEntity<Void> addRecord(String zone, String subDomain, String type, String content, HttpSession session) {
        // 최대 레코드 수 체크
        Long memberId = (Long) session.getAttribute("memberId");
        Member member = memberRepository.findById(memberId).orElseThrow();

        int maxRecords = member.getMaxRecords(),
            currentRecords = haveSubDomainRepository.countByMemberId(memberId);
        if (currentRecords >= maxRecords) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // CNAME 을 등록하거나, 등록되어있는데 다른 타입을 등록하면 기존 레코드 삭제
        boolean exitsCNAME = false;
        if (type.equalsIgnoreCase("CNAME")) {
            exitsCNAME = true;
        }
        List<PDNSDto.SearchResult> existingRecords = searchResultList(subDomain + "." + zone);
        for (PDNSDto.SearchResult record : existingRecords) {
            if (record.getType().equalsIgnoreCase("CNAME")) {
                exitsCNAME = true;
                break;
            }
        }

        try {
            if (exitsCNAME && deleteAllSubRecords(zone, subDomain, session).getStatusCode().isError()) {
                return ResponseEntity.internalServerError().build();
            }

            // PowerDNS 등록
            if (modifyRecord(zone, subDomain, type, content, "REPLACE").getStatusCode().isError()) {
                throw new Exception("Failed to add record: " + subDomain + "." + zone + " " + type + " " + content);
            }

            // DB 등록
            try {
                HaveSubDomain haveSubDomain = HaveSubDomain.builder()
                        .member(member)
                        .fullDomain(subDomain + "." +  zone)
                        .recordType(type)
                        .content(content)
                        .build();
                haveSubDomainRepository.save(haveSubDomain);

                return ResponseEntity.ok().build();
            } catch (Exception e) {
                // DB 등록 실패 시 PowerDNS에서 삭제
                log.error("DB 등록 중 에러 발생, PowerDNS에서 레코드 삭제 시도", e);
                deleteRecord(zone, subDomain, type, session);
                throw new Exception("저장 실패한 레코드 정보 : " + subDomain + "." + zone + " " + type + " " + content, e);
            }
        } catch (Exception e) {
            log.error("레코드 등록 중 에러 발생", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 서브 도메인의 모든 타입 제거
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param session       HttpSession
     * @return ResponseEntity<Void> 성공 여부
     */
    private ResponseEntity<Void> deleteAllSubRecords(String zone, String subDomain, HttpSession session) {
        List<PDNSDto.SearchResult> records = searchResultList(subDomain + "." + zone);
        for (PDNSDto.SearchResult record : records) {
            ResponseEntity<Void> result = deleteRecord(zone, subDomain, record.getType(), session);
            if (result.getStatusCode().isError()) {
                return result;
            }
        }

        return ResponseEntity.ok().build();
    }


    /**
     * 레코드 삭제
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @return ResponseEntity<Void> 성공 여부
     */
    public ResponseEntity<Void> deleteRecord(String zone, String subDomain, String type, HttpSession session) {
        Member member = memberRepository.findById((Long) session.getAttribute("memberId")).orElseThrow();
        String content = null;

        try {
            modifyRecord(zone, subDomain, type, null, "DELETE");
        } catch (Exception e) {
            log.error("PowerDNS에서 레코드 삭제 중 에러 발생", e);
            return ResponseEntity.internalServerError().build();
        }

        try {
            HaveSubDomain haveSubDomain = haveSubDomainRepository.findByMemberIdAndFullDomain(member.getId(), subDomain + "." + zone).orElseThrow();
            content = haveSubDomain.getContent();
            haveSubDomainRepository.delete(haveSubDomain);

            return ResponseEntity.ok().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("DB에서 레코드 삭제 중 에러 발생", e);
            log.error("삭제한 PowerDNS 레코드 복구 시도");
            try {
                addRecord(zone, subDomain, type, content, session);
                log.error("삭제한 레코드 복구 완료");
            } catch (Exception ex) {
                log.error("[!] 확인 필요 [!]");
                log.error("DB에서 레코드 삭제 중 에러로 인한 레코드 복구 도중 에러 발생", ex);
            }
            return ResponseEntity.internalServerError().build();
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
