package top.nulldns.subdns.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.repository.HaveSubDomainRepository;
import top.nulldns.subdns.repository.MemberRepository;
import top.nulldns.subdns.util.PDNSRecordValidator;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PDNSService {
    private final MemberRepository memberRepository;
    private final HaveSubDomainRepository haveSubDomainRepository;
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
    private List<PDNSDto.ZoneName> cachedZoneNames = List.of();

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
            this.cachedZoneNames = getZoneNameList();
        } catch (Exception e) {
            log.error("초기 Zone Name 목록 갱신 중 에러 발생", e);
            this.cachedZoneNames = new ArrayList<>();
            cachedZoneNames.add(PDNSDto.ZoneName.builder().name("nulldns.top").build());
        }
    }

    /**
     * 정기 Zone Name 목록 갱신
     */
    @Scheduled(cron = "0 0 0 * * ?")
    private void scheduledRefresh() {
        try {
            this.cachedZoneNames = getZoneNameList();
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
     * 멤버의 최대 레코드 수 초과 체크
     * @param member   멤버 정보
     * @return boolean 최대 레코드 수 초과 여부
     */
    private boolean checkMaxRecords(Member member) {
        int currentRecords = haveSubDomainRepository.countDistinctFullDomainByMemberId(member.getId());

        return currentRecords < member.getMaxRecords();
    }

    /**
     * 특정 서브 도메인에 CNAME 레코드 존재 여부 체크
     * @param subDomain example, www 등
     * @param zone      nulldns.top, example.com 등
     * @return boolean  CNAME 레코드 존재 여부
     */
    private boolean checkAlreadyCNAME(String subDomain, String zone) {
        for (PDNSDto.SearchResult record : searchResultList(subDomain + "." + zone)) {
            if (record.getType().equalsIgnoreCase("CNAME")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 특정 서브 도메인의 첫 레코드 만료일 조회
     * @param memberId   멤버 ID
     * @param subDomain  example, www 등
     * @param zone       nulldns.top, example.com 등
     * @return LocalDate 첫 레코드 만료일
     */
    private LocalDate getExpiryDate(Long memberId, String subDomain, String zone) {
        return haveSubDomainRepository.findAllByMemberIdAndFullDomain(memberId, subDomain + "." + zone).getFirst().getExpiryDate();
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

        boolean isAdmin = checkAdminService.isAdmin(memberId);

        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new NoSuchElementException("해당 memberId의 멤버가 존재하지 않음")
        );

        boolean existsFullDomain = haveSubDomainRepository.existsByFullDomain(subDomain + "." + zone),
                isTypeCNAME      = type.equalsIgnoreCase("CNAME"),
                alreadyCNAME     = checkAlreadyCNAME(subDomain, zone);

        // 신규 등록이 아닌 상황
        if (existsFullDomain && !checkDomainOwner(memberId, subDomain, zone)) {
            throw new SecurityException("보유 도메인이 아님에도 수정하려는 절차가 진행중임");
        }

        // 최대 레코드수 체크
        if (!isAdmin) {
            if (!checkMaxRecords(member)) {
                throw new IllegalStateException("최대 레코드 수 초과");
            }
        }

        LocalDate expiryDate = isAdmin ? LocalDate.now().plusYears(999)
                                        : existsFullDomain ? getExpiryDate(memberId, subDomain, zone)
                                                 : null;

        // CNAME 레코드는 단독으로만 존재 가능
        if ( (isTypeCNAME && existsFullDomain) || (!isTypeCNAME && alreadyCNAME)) {
            deleteAllSubRecords(subDomain, zone, memberId);
        }

        // PowerDNS 등록
        modifyRecord(zone, subDomain, type, content, "REPLACE", isAdmin);

        // DB 등록
        HaveSubDomain.HaveSubDomainBuilder builder = HaveSubDomain.builder()
                .member(member)
                .fullDomain(subDomain + "." +  zone)
                .recordType(type)
                .content(content);
        if (expiryDate != null) {
            builder.expiryDate(expiryDate);
        }
        haveSubDomainRepository.save(builder.build());
    }

    /**
     * 특정 서브 도메인의 모든 타입 제거
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param memberId      memberId
     */
    public void deleteAllSubRecords(String subDomain, String zone, Long memberId) {
        List<HaveSubDomain> haveSubDomainList = haveSubDomainRepository.findAllByMemberIdAndFullDomain(memberId, subDomain + "." + zone);
        haveSubDomainRepository.deleteAll(haveSubDomainList);
    }

    /**
     * 멤버의 모든 서브 도메인 레코드 삭제
     * @param memberId  memberId
     */
    public void deleteAllSubRecords(Long memberId) {
        for (HaveSubDomain haveSubDomain : haveSubDomainRepository.findByMemberId(memberId)) {
            String[] domainParts = haveSubDomain.getFullDomain().split("\\.");

            String subDomain = getSubDomainPart(domainParts),
                   zone = getZonePart(domainParts);

            deleteRecord(zone, subDomain, haveSubDomain.getRecordType(), memberId);
        }
    }

    /**
     * 존 부분 반환 (존 부분은 항상 도메인의 마지막 두 부분이라고 가정)
     * @param domainParts 도메인 부분 배열
     * @return 존 부분 문자열
     */
    private String getZonePart(String[] domainParts) {
        int len = domainParts.length;
        return domainParts[len - 2] + "." + domainParts[len - 1];
    }

    /**
     * 서브 도메인 부분 반환 (존 부분을 제외한 나머지 부분) (존 부분은 항상 도메인의 마지막 두 부분이라고 가정)
     * @param domainParts 도메인 부분 배열
     * @return 서브 도메인 부분 문자열
     */
    private String getSubDomainPart(String[] domainParts) {
        StringBuilder subDomain = new StringBuilder();
        for (int i = 0; i < domainParts.length - 2; i++) {
            if (i > 0) {
                subDomain.append(".");
            }
            subDomain.append(domainParts[i]);
        }
        return subDomain.toString();
    }

    /**
     * 도메인 소유 여부 체크
     * @param memberId  memberId
     * @param subdomain example, www 등
     * @param zone      nulldns.top, example.com 등
     * @return boolean  도메인 소유 여부
     */
    private boolean checkDomainOwner(Long memberId, String subdomain, String zone) {
        return haveSubDomainRepository.existsHaveSubDomainByMemberIdAndFullDomain(memberId, subdomain + "." + zone);
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
        if (!checkDomainOwner(memberId, subDomain, zone)) {
            throw new SecurityException("보유 도메인 아님");
        }

        // 세션에서 멤버 정보 조회
        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new NoSuchElementException("해당 memberId의 멤버가 존재하지 않음")
        );
        
        // PowerDNS에서 삭제 진행
        boolean isAdmin = checkAdminService.isAdmin(member.getId());
        modifyRecord(zone, subDomain, type, null, "DELETE", isAdmin);

        // HaveSubDomain에서 삭제 진행
        HaveSubDomain haveSubDomain = haveSubDomainRepository.findByMemberIdAndFullDomainAndRecordType(member.getId(), subDomain + "." + zone, type).orElseThrow(
                () -> new NoSuchElementException("삭제하려는 레코드가 DB에 존재하지 않음")
        );
        haveSubDomainRepository.delete(haveSubDomain);
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
     * 레코드 수정/삭제 공통 메서드
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @param content       레코드 값 (삭제 시 null)
     * @param action        REPLACE / DELETE
     */
    private void modifyRecord(String zone, String subDomain, String type, String content, String action, boolean isAdmin) {
        if (zone.isEmpty() || subDomain.isEmpty() || type.isEmpty() || action.isEmpty()) {
            throw new IllegalArgumentException("필수 파라미터 누락");
        }
        action = action.toUpperCase();

        if (!action.equals("REPLACE") && !action.equals("DELETE")) {
            throw new IllegalArgumentException("옳바르지 않은 액션");
        }

        // SubDomain 라벨 체크
        if (isAdmin && !PDNSRecordValidator.isValidLabelAdmin(subDomain)) {
            throw new IllegalArgumentException("옳바르지 않은 서브 도메인");
        } else if (!isAdmin && !PDNSRecordValidator.isValidLabel(subDomain)) {
            throw new IllegalArgumentException("옳바르지 않은 서브 도메인");
        }

        List<PDNSDto.Record> records;
        if (action.equals("DELETE")) {
            if (!PDNSRecordValidator.isValidType(type)) { // 삭제는 type 값만 유효성 체크
                throw new IllegalArgumentException("옳바르지 않은 타입");
            }
            records = List.of();
        } else { // REPLACE (등록, 수정)
            if (content == null || content.isEmpty()) {
                throw new IllegalArgumentException("등록/수정에 필요한 정보가 누락되었습니다.");
            }
            if (!PDNSRecordValidator.validate(type, content)) {
                throw new IllegalArgumentException("옳바르지 않은 내용");
            }
            content = modifyContentByType(type, content);

            records = List.of(PDNSDto.Record.builder().content(content).build());
        }

        String fullDomain = subDomain + "." + zone;
        if (fullDomain.charAt(fullDomain.length() - 1) != '.') {
            fullDomain = fullDomain + ".";
        }

        // 중복 실행 방지 락 설정
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(fullDomain, "LOCKED", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            log.error("modifyRecord 중복 실행 방지: {}.{}", subDomain, zone);
            throw new IllegalStateException("해당 도메인에 대한 작업이 이미 진행 중입니다. 잠시 후 다시 시도해주세요.");
        }

        // 요청 데이터 생성
        PDNSDto.Rrset rrset = PDNSDto.Rrset.builder()
                .name(fullDomain)
                .type(type)
                .changeType(action)
                .records(records)
                .build();

        // 요청 진행
        restClient.patch().uri("/zones/" + zone)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("rrsets", List.of(rrset)))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException("PowerDNS API 통신 중 에러 발생");
                })
                .body(Void.class);

        // 락 해제
        redisTemplate.delete(fullDomain);
    }

    /**
     * Zone Name 목록 반환
     * @return List<PDNSDto.ZoneName> {name}
     */
    private List<PDNSDto.ZoneName> getZoneNameList() {
        List<PDNSDto.ZoneName> zones = restClient.get()
                .uri("zones")
                .retrieve()
                .body(new ParameterizedTypeReference<List<PDNSDto.ZoneName>>() {});

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
     * 도메인 소유 여부 체크 (풀 도메인)
     * @param memberId   memberId
     * @param fullDomain example.nulldns.top, www.example.com 등
     * @return boolean   도메인 소유 여부
     */
    public boolean isDomainOwner(Long memberId, String fullDomain) {
        return haveSubDomainRepository.existsHaveSubDomainByMemberIdAndFullDomain(memberId, fullDomain);
    }

    /**
     * 서브 도메인 만료 삭제 스케줄러용 레코드 삭제 메서드
     * @param haveSubDomain    삭제할 서브 도메인 정보
     * @return boolean         삭제 성공 여부
     */
    public boolean deleteSubRecordSchedule(HaveSubDomain haveSubDomain) {
        Long memberId = haveSubDomain.getMember().getId();
        String[] domainInfo = haveSubDomain.getFullDomain().split("\\.", 2);
        String subDomain = getSubDomainPart(domainInfo),
               zone = getZonePart(domainInfo),
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
}
