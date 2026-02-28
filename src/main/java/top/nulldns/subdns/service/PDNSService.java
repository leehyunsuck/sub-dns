package top.nulldns.subdns.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.dbservice.CheckAdminService;
import top.nulldns.subdns.service.dbservice.HaveSubDomainService;
import top.nulldns.subdns.service.dbservice.MemberService;
import top.nulldns.subdns.util.PDNSRecordValidator;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PDNSService {
    private final MemberService memberService;
    private final HaveSubDomainService haveSubDomainService;

    private final RestClient.Builder restClientBuilder;
    private final CheckAdminService checkAdminService;
    private final StringRedisTemplate redisTemplate;

    private static final Duration LOCK_TTL = Duration.ofSeconds(55);

    @Value("${pdns.url}")
    private String pdnsUrl;
    @Value("${pdns.api-key}")
    private String pdnsApiKey;
    private RestClient restClient;
    @Getter
    private Set<PDNSDto.ZoneName> cachedZoneNames = Set.of();

    /**
     * 초기화 메서드
     */
    @PostConstruct
    private void init() {
        this.restClient = restClientBuilder
                .baseUrl(pdnsUrl)
                .defaultHeader("X-API-Key", pdnsApiKey)
                .build();

        try {
            this.cachedZoneNames = this.getZoneNamesSet();
        } catch (Exception e) {
            log.error("초기 Zone Name 목록 갱신 중 에러 발생", e);
            this.cachedZoneNames = new HashSet<>();
            cachedZoneNames.add(PDNSDto.ZoneName.builder().name("nulldns.top").build());
        }
    }

    /**
     * 정기 Zone Name 목록 갱신
     */
    @Scheduled(cron = "0 0 0 * * ?")
    private void scheduledRefresh() {
        try {
            this.cachedZoneNames = this.getZoneNamesSet();
        } catch (Exception e) {
            log.error("정기 Zone Name 목록 갱신 중 에러 발생", e);
        }
    }

    /**
     * 특정 풀 도메인(서브 + 존) 레코드 검색 (모든 타입)
     * @param fullDomain example.nulldns.top, www.example.com 등
     * @return List<PDNSDto.SearchResult> {name, type, content}
     */
    public List<PDNSDto.SearchResult> searchResultList(String fullDomain) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search-data")
                        .queryParam("q", fullDomain)  // 혹은 서브도메인만
                        .queryParam("object_type", "record")
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    /**
     * 레코드 추가
     * @param subDomain example, www 등
     * @param zone      nulldns.top, example.com 등
     * @param type      A, CNAME, TXT 등
     * @param content   레코드 값
     * @param memberId  memberId
     */
    public void addRecord(String subDomain, String zone, String type, String content, Long memberId) {
        zone = zone.toLowerCase();
        subDomain = subDomain.toLowerCase();
        type = type.toUpperCase();

        Member member = memberService.getMemberById(memberId);

        String fullDomain = this.buildFullDomain(subDomain, zone);
        Set<String> existingRecordTypes = this.getAllRecordTypes(fullDomain);

        boolean isAdmin          = checkAdminService.isAdmin(memberId),
                existsFullDomain = !existingRecordTypes.isEmpty(),
                isTypeCNAME      = type.equalsIgnoreCase("CNAME"),
                alreadyCNAME     = existingRecordTypes.contains("CNAME"),
                isContentUpdate  = existingRecordTypes.contains(type);

        // 신규 등록이 아닌 상황
        if (existsFullDomain && !haveSubDomainService.isOwnerOfDomain(memberId, fullDomain)) {
            throw new SecurityException("보유 도메인이 아님에도 수정하려는 절차가 진행중임");
        }

        // 최대 레코드수 체크
        if (!isAdmin && !isContentUpdate) {
            if (!checkMaxRecords(member)) {
                throw new IllegalStateException("최대 레코드 수 초과");
            }
        }

        LocalDate expiryDate = isAdmin ? LocalDate.now().plusYears(999)
                                        : existsFullDomain ? haveSubDomainService.getExpiryDate(memberId, fullDomain)
                                                 : null;

        // CNAME 레코드는 단독으로만 존재 가능
        if ( (isTypeCNAME && existsFullDomain) || (!isTypeCNAME && alreadyCNAME)) {
            List<HaveSubDomain> haveSubDomainList = haveSubDomainService.getHaveSubDomainsByMemberIdAndFullDomain(memberId, fullDomain);
            this.deleteAllSubRecords(haveSubDomainList);
        }

        modifyRecord(zone, subDomain, type, content, "REPLACE", isAdmin);

        // DB 등록
        haveSubDomainService.addSubDomain(
                haveSubDomainService.buildHaveSubDomainAddForm(member, subDomain, zone, type, content, expiryDate), isContentUpdate
        );
    }

    /**
     * 여러 서브 도메인 레코드 일괄 삭제
     * @param haveSubDomainList 삭제할 서브 도메인 리스트
     */
    public void deleteAllSubRecords(List<HaveSubDomain> haveSubDomainList) {
        this.deleteAllSubRecordsInPDNS(haveSubDomainList);
        haveSubDomainService.deleteHaveSubDomains(haveSubDomainList);
    }

    public void deleteAllSubRecords(Long memberId) {
        List<HaveSubDomain> haveSubDomainList = haveSubDomainService.getMemberDomains(memberId);
        this.deleteAllSubRecords(haveSubDomainList);
        haveSubDomainService.deleteHaveSubDomains(haveSubDomainList);
    }

    public void deleteAllSubRecords(Long memberId, String subDomain, String zone) {
        String fullDomain = this.buildFullDomain(subDomain, zone);
        List<HaveSubDomain> haveSubDomainList = haveSubDomainService.getHaveSubDomainsByMemberIdAndFullDomain(memberId, fullDomain);
        if (haveSubDomainList.isEmpty()) {
            throw new NoSuchElementException("삭제하려는 도메인 보유 기록 없음");
        }

        this.deleteAllSubRecords(haveSubDomainList);
    }

    /**
     * 서브 도메인 만료 삭제 스케줄러용 레코드 삭제 메서드
     * @param haveSubDomain    삭제할 서브 도메인 정보
     * @return boolean         삭제 성공 여부
     */
    public boolean deleteSubRecordSchedule(HaveSubDomain haveSubDomain) {
        Long memberId = haveSubDomain.getMember().getId();
        String[] splitDomain = this.splitZoneAndSubDomain(haveSubDomain.getFullDomain());

        String subDomain = splitDomain[0],
                zone = splitDomain[1],
                type = haveSubDomain.getRecordType();

        try {
            deleteRecord(zone, subDomain, type, memberId);
        } catch (Exception e) {
            log.error("관리자에 의한 서브 도메인 만료 삭제 중 에러 발생\n도메인 정보: {}\nmemberId: {}", haveSubDomain, memberId);
            return false;
        }

        return true;
    }

    /**
     * 존 삭제
     * @param zoneName 존 이름
     */
    public void deleteZone(String zoneName) {
        restClient.delete()
                .uri("/servers/localhost/zones/{zone}.", zoneName)
                .header("X-API-Key", pdnsApiKey)
                .retrieve()
                .toBodilessEntity();
        log.info("존 삭제 완료: {}", zoneName);
    }

    /**
     * 풀 도메인에서 존과 서브 도메인 부분 분리
     * @param fullDomain example.nulldns.top, www.example.com 등
     * @return String[] {subDomain, zone}
     */
    public String[] splitZoneAndSubDomain(String fullDomain) {
        String[] parts = fullDomain.split("\\.");

        if (parts.length == 3) {
            return new String[] {parts[0], parts[1] + "." + parts[2]};
        }

        // 서브 도메인 파트가 여러 개일 수 있는 경우
        boolean find = false;
        int firstZoneIndex = -1;

        int i = parts.length - 1;
        StringBuilder zoneBuilder = new StringBuilder(parts[i]);
        for (i = i - 1; i > 0 && !find; i--) {
            zoneBuilder.insert(0, ".");
            zoneBuilder.insert(0, parts[i]);

            if (cachedZoneNames.contains(PDNSDto.ZoneName.builder().name(zoneBuilder.toString()).build())) {
                find = true;
                firstZoneIndex = i;
            }
        }

        if (find) {
            String zone = zoneBuilder.toString();
            String subDomain = String.join(".", Arrays.copyOfRange(parts, 0, firstZoneIndex));
            return new String[] {subDomain, zone};
        }

        // 존 부분을 찾지 못한 경우 (존 부분이 항상 도메인의 마지막 두 부분이라고 가정)
        String zone = parts[parts.length - 2] + "." + parts[parts.length - 1];
        String subDomain = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 2));
        return new String[] {subDomain, zone};
    }

    /**
     * 여러 서브 도메인 레코드 일괄 삭제
     * @param haveSubDomainList 삭제할 서브 도메인 리스트
     */
    private void deleteAllSubRecordsInPDNS(List<HaveSubDomain> haveSubDomainList) {
        Map<String, List<PDNSDto.Rrset>> rrsetMap = new HashMap<>();

        for (HaveSubDomain haveSubDomain : haveSubDomainList) {
            String[] splitDomain = this.splitZoneAndSubDomain(haveSubDomain.getFullDomain());
            String zone = splitDomain[1];

            rrsetMap.putIfAbsent(zone, new ArrayList<>());
            rrsetMap.get(zone).add(
                    PDNSDto.Rrset.builder()
                            .name(buildFqdnForPowerDns(haveSubDomain.getFullDomain()))
                            .type(haveSubDomain.getRecordType())
                            .changeType("DELETE")
                            .build()
            );
        }

        for (String zone : rrsetMap.keySet()) {
            patchModifyRecord(rrsetMap.get(zone), zone);
        }
    }

    /**
     * 레코드 삭제
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @return ResultMessageDTO<Void> {boolean pass, String message, T data}
     */
    private void deleteRecord(String zone, String subDomain, String type, Long memberId) {
        zone = zone.toLowerCase();
        subDomain = subDomain.toLowerCase();
        type = type.toUpperCase();

        // 보유 도메인 체크
        if (!haveSubDomainService.isOwnerOfDomain(memberId, subDomain + "." + zone)) {
            throw new SecurityException("보유 도메인 아님");
        }
        
        // PowerDNS에서 삭제 진행
        boolean isAdmin = checkAdminService.isAdmin(memberId);
        modifyRecord(zone, subDomain, type, null, "DELETE", isAdmin);

        // DB에서 삭제 진행
        String fullDomain = this.buildFqdnForPowerDns(subDomain, zone);
        haveSubDomainService.deleteHaveSubDomain(memberId, fullDomain, type);
    }

    /**
     * 타입에 따른 content 수정
     * @param type      A, CNAME, TXT 등
     * @param content   레코드 값
     * @return String   수정된 content 값
     */
    private String modifyContentByType(String type, String content) {
        if (type.equals("TXT")) {
            if (content.charAt(0) != '"') content = "\"" + content;
            if (content.charAt(content.length() - 1) != '"') content = content + "\"";
        } else if (type.equals("CNAME")) {
            if (content.charAt(content.length() - 1) != '.') content = content + ".";
        }

        return content;
    }

    /**
     * 공통 파라미터 체크 메서드
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @param action        REPLACE / DELETE
     * @return boolean      파라미터 유효성 여부
     */
    private boolean isValidArguments(String zone, String subDomain, String type, String action, String content) {
        if (zone.isEmpty() || subDomain.isEmpty() || type.isEmpty() || action.isEmpty()) {
            return false;
        }
        action = action.toUpperCase();

        if (action.equals("DELETE")) {
            if (!PDNSRecordValidator.isValidType(type)) {
                return false;
            }
        } else if (action.equals("REPLACE")) {
            if (content == null || content.isEmpty()) {
                return false;
            }
            if (!PDNSRecordValidator.validate(type, content)) {
                return false;
            }
        } else {
            return false;
        }

        return true;
    }

    /**
     * 서브 도메인 라벨 유효성 체크
     * @param subDomain     example, www 등
     * @param isAdmin       관리자 여부
     * @return boolean      라벨 유효성 여부
     */
    private boolean isValidLable(String subDomain, boolean isAdmin) {
        if (isAdmin) {
            return PDNSRecordValidator.isValidLabelAdmin(subDomain);
        } else {
            return PDNSRecordValidator.isValidLabel(subDomain);
        }
    }

    /**
     * PowerDNS용 FQDN 생성
     * @param subDomain example, www 등
     * @param zone      nulldns.top, example.com 등
     * @return String   FQDN 문자열
     */
    private String buildFqdnForPowerDns(String subDomain, String zone) {
        String fqdn = this.buildFullDomain(subDomain, zone);

        if (fqdn.charAt(fqdn.length() - 1) != '.') {
            fqdn = fqdn + ".";
        }

        return fqdn;
    }

    /**
     * 풀 도메인 생성
     * @param subDomain    example, www 등
     * @param zone         nulldns.top, example.com 등
     * @return String        풀 도메인 문자열
     */
    private String buildFullDomain(String subDomain, String zone) {
        return subDomain + "." + zone;
    }

    private String buildFqdnForPowerDns(String fullDomain) {
        if (fullDomain.charAt(fullDomain.length() - 1) != '.') {
            fullDomain = fullDomain + ".";
        }

        return fullDomain;
    }

    /**
     * 레코드 수정/삭제 공통 메서드
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @param content       레코드 값 (삭제 시 null)
     * @param action        REPLACE / DELETE
     */
    private void modifyRecord(String zone, String subDomain, String type, String content, String action, boolean isAdmin) {
        action = action.toUpperCase();
        if (!isValidArguments(zone, subDomain, type, action, content)) {
            throw new IllegalArgumentException("옳바르지 않은 파라미터");
        }

        if (!isValidLable(subDomain, isAdmin)) {
            throw new IllegalArgumentException("옳바르지 않은 서브 도메인");
        }

        List<PDNSDto.Record> records;
        if (action.equals("DELETE")) {
            records = List.of();
        } else { // REPLACE (등록, 수정)
            content = this.modifyContentByType(type, content);
            records = List.of(PDNSDto.Record.builder().content(content).build());
        }

        String fullDomain = buildFqdnForPowerDns(subDomain, zone);

        // 중복 실행 방지 락 설정
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(fullDomain, "LOCKED", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new IllegalStateException("해당 도메인에 대한 작업이 이미 진행 중");
        }

        // 요청 데이터 생성
        PDNSDto.Rrset rrset = PDNSDto.Rrset.builder()
                .name(fullDomain)
                .type(type)
                .changeType(action)
                .records(records)
                .build();

        // 요청 진행
        patchModifyRecord(List.of(rrset), zone);

        // 락 해제
        redisTemplate.delete(fullDomain);
    }

    /**
     * 레코드 수정/삭제 공통 패치 메서드
     * @param rrsets
     * @param zone
     */
    private void patchModifyRecord(List<PDNSDto.Rrset> rrsets, String zone) {
        restClient.patch().uri("/zones/" + zone)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("rrsets", rrsets))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException("PowerDNS API 통신 중 에러 발생");
                })
                .body(Void.class);
    }

    private Set<PDNSDto.ZoneName> getZoneNamesSet() {
        Set<PDNSDto.ZoneName> zones = restClient.get()
                .uri("zones")
                .retrieve()
                .body(new ParameterizedTypeReference<Set<PDNSDto.ZoneName>>() {});

        // PowerDNS API에서 Zone 정보 가져오면 마지막 문자가 . 으로 끝남
        for (PDNSDto.ZoneName zone : zones) {
            String name = zone.getName();
            if (name.endsWith(".")) {
                zone.setName(name.substring(0, name.length() - 1));
            }
        }

        return zones;
    }

    /**
     * 멤버의 최대 레코드 수 초과 체크
     * @param member   멤버 정보
     * @return boolean 최대 레코드 수 초과 여부
     */
    private boolean checkMaxRecords(Member member) {
        int currentRecords = haveSubDomainService.getOwnedDomainCount(member.getId());

        return currentRecords < member.getMaxRecords();
    }

    /**
     * 특정 풀 도메인(서브 + 존) 레코드 타입 전체 반환
     * @param fullDomain example.nulldns.top, www.example.com 등
     * @return Set<String> 레코드 타입 집합
     */
    private Set<String> getAllRecordTypes(String fullDomain) {
        Set<String> recordTypes = new HashSet<>();
        for (PDNSDto.SearchResult record : searchResultList(fullDomain)) {
            recordTypes.add(record.getType());
        }

        return recordTypes;
    }
}
