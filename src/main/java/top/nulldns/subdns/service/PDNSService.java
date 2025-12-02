package top.nulldns.subdns.service;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.dto.ResultMessageDTO;
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

        ResultMessageDTO<List<PDNSDto.ZoneName>> result = getZoneNameList();
        if (result.isPass()) {
            this.cachedZoneNames = getZoneNameList().getData();
        } else {
            log.error("초기 Zone Name 목록 갱신 중 에러 발생: " + result.getMessage());
            this.cachedZoneNames = new ArrayList<>();
            cachedZoneNames.add(PDNSDto.ZoneName.builder().name("nulldns.top").build());
        }

    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void sheduledRefresh() {
        ResultMessageDTO<List<PDNSDto.ZoneName>> result = getZoneNameList();
        if (result.isPass()) {
            this.cachedZoneNames = result.getData();
        } else {
            log.error("Zone Name 목록 갱신 중 에러 발생: " + result.getMessage());
        }
    }


    /**
     * 특정 풀 도메인(서브 + 존) 레코드 검색 (모든 타입)
     * @param fullDomain example.nulldns.top, www.example.com 등
     * @return ResultMessageDTO<List<PDNSDto.SearchResult>> {boolean pass, String message, T data}
     */
    public ResultMessageDTO<List<PDNSDto.SearchResult>> searchResultList(String fullDomain) {
        try {
            List<PDNSDto.SearchResult> result = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search-data")
                            .queryParam("q", fullDomain)  // 혹은 서브도메인만
                            .queryParam("object_type", "record")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PDNSDto.SearchResult>>() {});

            return ResultMessageDTO.<List<PDNSDto.SearchResult>>builder().pass(true).data(result).build();
        } catch (Exception e) {
            return ResultMessageDTO.<List<PDNSDto.SearchResult>>builder().pass(false).message("PowerDNS API 통신 중 에러 발생").build();
        }
    }

    /**
     * 레코드 추가
     * @param subDomain example, www 등
     * @param zone      nulldns.top, example.com 등
     * @param type      A, CNAME, TXT 등
     * @param content   레코드 값
     * @param session   HttpSession
     * @return ResultMessageDTO<Void> {boolean pass, String message, T data}
     */
    public ResultMessageDTO<Void> addRecord(String subDomain, String zone, String type, String content, HttpSession session) {
        // 최대 레코드 수 체크
        Long memberId = (Long) session.getAttribute("memberId");
        Member member = null;
        try {
            member = memberRepository.findById(memberId).orElseThrow();
        } catch (NoSuchElementException e) {
            return ResultMessageDTO.<Void>builder().pass(false).message("세션에 저장된 유저 정보가 DB에 존재하지 않음").build();
        }

        int maxRecords = member.getMaxRecords(),
            currentRecords = 0;

        try {
            currentRecords = haveSubDomainRepository.countByMemberId(memberId);
        } catch (Exception e) {
            return ResultMessageDTO.<Void>builder().pass(false).message("유저의 현재 등록된 전체 레코드 수 조회 중 에러 발생").build();
        }

        // 여기서 수정이냐 추가냐 판단 필요
        // -> 수정이면 개수 판단 X
        //
        //


        if (currentRecords >= maxRecords) {
            return ResultMessageDTO.<Void>builder().pass(false).message("최대 레코드 수 초과").build();
        }

        // CNAME 을 등록하거나, 등록되어있는데 다른 타입을 등록하면 기존 레코드 삭제
        boolean exitsCNAME = false;
        if (type.equalsIgnoreCase("CNAME")) {
            exitsCNAME = true;
        }
        ResultMessageDTO<List<PDNSDto.SearchResult>> searchResultDTO = searchResultList(subDomain + "." + zone);
        if (!searchResultDTO.isPass()) {
            return ResultMessageDTO.<Void>builder().pass(false).message("기존 레코드 검색 중 에러 발생").build();
        }
        for (PDNSDto.SearchResult record : searchResultDTO.getData()) {
            if (record.getType().equalsIgnoreCase("CNAME")) {
                exitsCNAME = true;
                break;
            }
        }

        try {
            if (exitsCNAME && !deleteAllSubRecords(zone, subDomain, session).isPass()) {
                return ResultMessageDTO.<Void>builder().pass(false).message("기존 CNAME 레코드 삭제 중 에러 발생").build();
            }

            // PowerDNS 등록
            ResultMessageDTO<Void> pdnsResult = modifyRecord(zone, subDomain, type, content, "REPLACE");
            if (!pdnsResult.isPass()) {
                throw new Exception(pdnsResult.getMessage());
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

                return ResultMessageDTO.<Void>builder().pass(true).build();
            } catch (Exception e) {
                // DB 등록 실패 시 PowerDNS에서 삭제
                log.error("DB 등록 중 에러 발생, PowerDNS에서 레코드 삭제 시도", e);
                deleteRecord(zone, subDomain, type, session);
                throw new Exception("저장 실패한 레코드 정보 : " + subDomain + "." + zone + " " + type + " " + content, e);
            }
        } catch (Exception e) {
            log.error("레코드 등록 중 에러 발생", e);
            return ResultMessageDTO.<Void>builder().pass(false).message(e.getMessage()).build();
        }
    }

    /**
     * 특정 서브 도메인의 모든 타입 제거
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param session       HttpSession
     * @return ResultMessageDTO<Void> {boolean pass, String message, T data}
     */
    private ResultMessageDTO<Void> deleteAllSubRecords(String zone, String subDomain, HttpSession session) {
        ResultMessageDTO<List<PDNSDto.SearchResult>> searchResultDTO = searchResultList(subDomain + "." + zone);
        if (!searchResultDTO.isPass()) {
            return ResultMessageDTO.<Void>builder().pass(false).message("레코드 검색 중 에러 발생").build();
        }

        for (PDNSDto.SearchResult record : searchResultDTO.getData()) {
            ResultMessageDTO<Void> result = deleteRecord(zone, subDomain, record.getType(), session);

            if (!result.isPass()) {
                return result;
            }
        }

        return ResultMessageDTO.<Void>builder().pass(true).build();
    }


    /**
     * 레코드 삭제
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @return ResultMessageDTO<Void> {boolean pass, String message, T data}
     */
    public ResultMessageDTO<Void> deleteRecord(String zone, String subDomain, String type, HttpSession session) {
        Member member;
        String content = null;
        try {
            member = memberRepository.findById((Long) session.getAttribute("memberId")).orElseThrow();
        } catch (NoSuchElementException e) {
            log.error("세션에 저장된 유저 정보가 DB에 존재하지 않음", e);
            return ResultMessageDTO.<Void>builder().pass(false).message("세션에 저장된 유저 정보가 DB에 존재하지 않음").build();
        }

        ResultMessageDTO<Void> pdnsResult = modifyRecord(zone, subDomain, type, null, "DELETE");
        if (!pdnsResult.isPass()) {
            return pdnsResult;
        }

        try {
            HaveSubDomain haveSubDomain = haveSubDomainRepository.findByMemberIdAndFullDomain(member.getId(), subDomain + "." + zone).orElseThrow();
            content = haveSubDomain.getContent();
            haveSubDomainRepository.delete(haveSubDomain);

            return ResultMessageDTO.<Void>builder().pass(true).build();
        } catch (NoSuchElementException e) {
            return ResultMessageDTO.<Void>builder().pass(true).message("삭제하려는 레코드가 DB에 존재하지 않음").build(); // PDNS에서는 삭제됐으니 pass = true
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
            return ResultMessageDTO.<Void>builder().pass(false).message("DB에서 레코드 삭제 중 에러 발생").build();
        }
    }

    /**
     * 레코드 수정/삭제 공통 메서드
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @param content       레코드 값 (삭제 시 null)
     * @param action        REPLACE / DELETE
     * @return ResultMessageDTO<Void> {boolean pass, String message, T data}
     */
    private ResultMessageDTO<Void> modifyRecord(String zone, String subDomain, String type, String content, String action) {
        if (zone.isEmpty() || subDomain.isEmpty() || type.isEmpty() || action.isEmpty()) {
            return ResultMessageDTO.<Void>builder().pass(false).message("필수 파라미터 누락").build();
        }

        zone      = zone.toLowerCase();
        subDomain = subDomain.toLowerCase();
        type      = type.toUpperCase();
        action    = action.toUpperCase();

        if (!action.equals("REPLACE") && !action.equals("DELETE")) {
            return ResultMessageDTO.<Void>builder().pass(false).message("옳바르지 않은 action").build();
        }
        if (!PDNSRecordValidator.isValidLabel(subDomain)) { // subDomain 유효성 체크
            return ResultMessageDTO.<Void>builder().pass(false).message("옳바르지 않은 subDomain").build();
        }

        PDNSDto.Record record = null;

        if (action.equals("REPLACE")) {
            if (content == null || content.isEmpty()) { // 등록/수정은 content 값이 반드시 필요
                return ResultMessageDTO.<Void>builder().pass(false).message("등록/수정에 필요한 content 누락").build();
            }
            if (!PDNSRecordValidator.validate(type, content)) { // type 별 content 유효성 체크
                return ResultMessageDTO.<Void>builder().pass(false).message("옳바르지 않은 content").build();
            }

            if (type.equals("TXT")) {
                if (content.charAt(0) != '"') {
                    content = "\"" + content;
                }
                if (content.charAt(content.length() - 1) != '"') {
                    content = content + "\"";
                }
            }

            if (type.equals("CNAME")) {
                // CNAME 은 반드시 . 으로 끝나야 함
                if (content.charAt(content.length() - 1) != '.') {
                    content = content + ".";
                }
            }

            record = PDNSDto.Record.builder()
                    .content(content)
                    .build();
        } else {
            if (!PDNSRecordValidator.isValidType(type)) { // 삭제는 type 값만 유효성 체크
                return ResultMessageDTO.<Void>builder().pass(false).message("옳바르지 않은 type").build();
            }
        }

        PDNSDto.Rrset rrset = PDNSDto.Rrset.builder()
                .name(subDomain + "." + zone + ".")
                .type(type)
                .changeType(action)
                .records(record == null ? List.of() : List.of(record))
                .build();

        record PatchPayLoad(List<PDNSDto.Rrset> rrsets) {}

        try {
            restClient.patch()
                    .uri("/zones/" + zone)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PatchPayLoad(List.of(rrset)))
                    .retrieve()
                    .toBodilessEntity();

            return ResultMessageDTO.<Void>builder().pass(true).build();
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResultMessageDTO.<Void>builder().pass(false).message("PowerDNS API 통신 중 에러 발생").build();
        }
    }

    /**
     * Zone Name 목록 반환
     * @return List<PDNSDto.ZoneName> {name}
     */
    private ResultMessageDTO<List<PDNSDto.ZoneName>> getZoneNameList() {
        List<PDNSDto.ZoneName> zones = new ArrayList<>();
        try {
            zones = restClient.get()
                    .uri("zones")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PDNSDto.ZoneName>>() {});
        } catch (Exception e) {
            return ResultMessageDTO.<List<PDNSDto.ZoneName>>builder().pass(false).message("PowerDNS API 통신 중 에러 발생").build();
        }

        // PowerDNS API에서 Zone 정보 가져오면 마지막 문자가 . 으로 끝남
        for (PDNSDto.ZoneName zone : zones) {
            String name = zone.getName();
            if (name.endsWith(".")) {
                zone.setName(name.substring(0, name.length() - 1));
            }
        }

        return ResultMessageDTO.<List<PDNSDto.ZoneName>>builder().pass(true).data(zones).build();
    }
}
