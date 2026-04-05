package top.nulldns.subdns.service.facade;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import top.nulldns.subdns.config.finalconfig.Action;
import top.nulldns.subdns.config.finalconfig.Status;
import top.nulldns.subdns.dto.PDNSDto;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.dto.SubDomainDto;
import top.nulldns.subdns.service.domain.CheckAdminService;
import top.nulldns.subdns.service.domain.HaveSubDomainService;
import top.nulldns.subdns.service.domain.MemberService;
import top.nulldns.subdns.service.infra.LockService;
import top.nulldns.subdns.util.PDNSRecordValidator;

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
    private final LockService lockService;

    private static final String LOCK_KEY_PREFIX = "pdns:";

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
        content = this.modifyContentByType(type, content);

        Member member = memberService.getMemberById(memberId);

        String fullDomain = this.buildFullDomain(subDomain, zone);
        Set<String> existingRecordTypes = this.getAllRecordTypes(fullDomain);

        boolean isAdmin         = checkAdminService.isAdmin(memberId),
                isNewDomain     = existingRecordTypes.isEmpty(),
                isContentUpdate = existingRecordTypes.contains(type),
                coexistCNAMEAndOtherType    // isTypeCNAME XOR alreadyCNAME
                                = type.equals("CNAME") ^ existingRecordTypes.contains("CNAME");

        // 도메인 소유주 체크
        if (!isNewDomain && !haveSubDomainService.isOwnerOfDomain(member, fullDomain)) {
            throw new SecurityException("보유 도메인이 아님에도 수정하려는 절차가 진행중임");
        }
        // 최대 레코드수 체크
        if (!isAdmin && !isContentUpdate && !isNotOverMaxRecords(member)) {
            throw new IllegalStateException("최대 레코드 수 초과");
        }
        // 파라미터 체크 (
        if (!isValidArguments(zone, subDomain, type, content, isAdmin)) {
            throw new IllegalArgumentException("옳바르지 않은 파라미터");
        }
        // 도메인(라벨) 체크
        if (isNewDomain && !isValidLabel(subDomain, isAdmin)) {
            throw new IllegalArgumentException("옳바르지 않은 서브 도메인");
        }

        String lockKey = LOCK_KEY_PREFIX + fullDomain;
        String lockValue = lockService.lock(lockKey);

        try {
            LocalDate expiryDate = isAdmin
                    ? LocalDate.now().plusYears(999)
                    : isNewDomain
                    ? null // 신규 등록이면 값 지정 X -> HaveSubDoamin prePersist() 에서 설정함
                    :haveSubDomainService.getExpiryDate(member, fullDomain);

            // CNAME 레코드는 단독으로만 존재 가능 -> 기존 도메인 지우기
            if (coexistCNAMEAndOtherType) {
                this.deleteSubRecords(haveSubDomainService.getMemberSubDomainsByFullDomain(member, fullDomain));
            }

            // Entity 등록 or 상태 수정
            // 동일 타입 업데이트 아니면 다 새로 생성임
            HaveSubDomain haveSubDomain = null;
            if (isContentUpdate) {
                haveSubDomain = haveSubDomainService.getHaveSubDomainByDetailInfo(member, fullDomain, type);
                haveSubDomainService.updateContentAndSetPending(haveSubDomain, content);
            } else {
                haveSubDomain = haveSubDomainService.newHaveSubDomain(member, fullDomain, type, content, expiryDate); // 여기서 ADD_PENDING 으로 설정함
            }

            // PDNS 반영 - 여기선 1개만 수정하니까 반환값 사용 안함
            // 익셉션 터지면 여기서 안잡고 추 후 스케줄링에서 처리함
            this.actionSubRecordsInPDNS(List.of(haveSubDomain), Action.REPLACE);

            // db 상태 수정
            haveSubDomainService.setStatusActivity(haveSubDomain);
        } finally {
            lockService.unlock(lockKey, lockValue);
        }
    }

    public void deleteSubRecord(Member member, String subDomain, String zone) {
        String fullDomain = this.buildFullDomain(subDomain, zone);

        String key = LOCK_KEY_PREFIX + fullDomain;
        String value = lockService.lock(key);

        try {
            List<HaveSubDomain> haveSubDomains = haveSubDomainService.getMemberSubDomainsByFullDomain(member, fullDomain);
            if (haveSubDomains.isEmpty()) {
                throw new NoSuchElementException("삭제하려는 도메인 보유 기록 없음");
            }

            this.deleteSubRecords(haveSubDomains);
        } finally {
            lockService.unlock(key, value);
        }
    }

    public void modifyPendingRecords(List<HaveSubDomain> haveSubDomains, Status status) {
        if (haveSubDomains.isEmpty()) {
            throw new IllegalArgumentException("추가하려는 도메인 목록이 비어있음");
        }

        if ( !(status.equals(Status.ADD_PENDING) || status.equals(Status.DELETE_PENDING) || status.equals(Status.UPDATE_PENDING)) ){
            throw new IllegalArgumentException("옳바르지 않은 상태 파라미터 넘어옴");
        }

        Action action = status.equals(Status.DELETE_PENDING) ? Action.DELETE : Action.REPLACE;
        Map<String, String> keyAndValues = new HashMap<>();

        try {
            // 처리하기전에 락 시작
            List<HaveSubDomain> canProcessHaveSubDomains = new ArrayList<>();
            for (HaveSubDomain haveSubDomain : haveSubDomains) {
                String key = LOCK_KEY_PREFIX + haveSubDomain.getFullDomain();
                String value = null;
                try {
                    value = lockService.lock(key);
                } catch (ConcurrencyFailureException e) {
                    // 이미 다른 작업에서 처리중
                    continue;
                }
                canProcessHaveSubDomains.add(haveSubDomain);
                keyAndValues.put(key, value);
            }

            // 이미 DB에 저장된거임 - PDNS에 반영하고 상태 수정하면 됨
            List<HaveSubDomain> successSubDomains = this.actionSubRecordsInPDNS(canProcessHaveSubDomains, action);

            if (action.equals(Action.REPLACE)) {
                haveSubDomainService.setStatusActivity(successSubDomains);
                log.info("{} 레코드 {}개 PDNS 반영 완료", status.name(), successSubDomains.size());
                log.info(successSubDomains.toString());
            } else {
                haveSubDomainService.deleteSubDomains(successSubDomains);
            }
        } finally {
            for (Map.Entry<String, String> entry : keyAndValues.entrySet()) {
                lockService.unlock(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 레코드 삭제 (내부용)
     * @param haveSubDomains
     */
    public void deleteSubRecords(List<HaveSubDomain> haveSubDomains) {
        haveSubDomainService.setDeletePending(haveSubDomains);

        List<HaveSubDomain> deleteSuccess = this.actionSubRecordsInPDNS(haveSubDomains, Action.DELETE);
        haveSubDomainService.deleteSubDomains(deleteSuccess);
    }

    public void deleteSubRecordsByMemberId(Long memberId) {
        List<HaveSubDomain> haveSubDomains = haveSubDomainService.getMemberSubDomains(memberService.getMemberById(memberId));
        this.deleteSubRecords(haveSubDomains);
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
     * PDNS에 레코드 반영하는 공통 메서드
     * @param haveSubDomains
     * @param action
     * @return List<HaveSubDomain> PDNS 반영 성공한 엔티티 리스트
     */
    private List<HaveSubDomain> actionSubRecordsInPDNS(List<HaveSubDomain> haveSubDomains, Action action) {
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }

        // 성공한 엔티티만 담아서 반환함
        List<HaveSubDomain> successSubDomains = new ArrayList<>();

        boolean actionIsReplace = Action.REPLACE.equals(action);

        Map<String, List<HaveSubDomain>> zoneSubDomainsMap = new HashMap<>();   // 반환 용도
        Map<String, List<PDNSDto.Rrset>> zoneRrsetsMap = new HashMap<>();       // PDNS 요청 용도

        for (HaveSubDomain haveSubDomain : haveSubDomains) {
            String[] splitDomain = this.splitZoneAndSubDomain(haveSubDomain.getFullDomain());
            String zone = splitDomain[1];

            PDNSDto.Rrset.RrsetBuilder builder = PDNSDto.Rrset.builder()
                    .name(buildFqdnForPowerDns(haveSubDomain.getFullDomain()))
                    .type(haveSubDomain.getRecordType())
                    .changeType(action.name());

            if (actionIsReplace) {
                builder = builder.records(List.of(PDNSDto.Record.builder().content(haveSubDomain.getContent()).build()));
            }

            zoneRrsetsMap.putIfAbsent(zone, new ArrayList<>());
            zoneRrsetsMap.get(zone).add(builder.build());

            zoneSubDomainsMap.putIfAbsent(zone, new ArrayList<>());
            zoneSubDomainsMap.get(zone).add(haveSubDomain);
        }

        for (String zone : zoneRrsetsMap.keySet()) {
            try {
                patchModifyRecord(zoneRrsetsMap.get(zone), zone);
            } catch (Exception e) {
                log.error("존 {} 에 대한 레코드 {} 중 {} 작업 실패", zone, zoneRrsetsMap.get(zone).size(), action.name(), e);
                continue;
            }
            successSubDomains.addAll(zoneSubDomainsMap.get(zone));
        }

        return successSubDomains;
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
     * Record REPLACE 에 사용되는 파라미터 체크
     * @param zone          nulldns.top, example.com 등
     * @param subDomain     example, www 등
     * @param type          A, CNAME, TXT 등
     * @return boolean      파라미터 유효성 여부
     */
    private boolean isValidArguments(String zone, String subDomain, String type, String content, boolean isAdmin) {
        if (zone == null || subDomain == null || type == null || content == null) {
            return false;
        }
        if (zone.isEmpty() || subDomain.isEmpty() || type.isEmpty() || content.isEmpty()) {
            return false;
        }

        return PDNSRecordValidator.validate(type, content, zone, isAdmin);
    }

    /**
     * 서브 도메인 라벨 유효성 체크
     * @param subDomain     example, www 등
     * @param isAdmin       관리자 여부
     * @return boolean      라벨 유효성 여부
     */
    private boolean isValidLabel(String subDomain, boolean isAdmin) {
        if (isAdmin) {
            return PDNSRecordValidator.isValidLabelAdmin(subDomain);
        } else {
            return PDNSRecordValidator.isValidLabel(subDomain);
        }
    }

    /**
     * PowerDNS API는 FQDN 형태로 도메인 입력 필요 -> 풀 도메인에 . 붙여주는 메서드
     * @param fullDomain example.nulldns.top, www.example.com 등
     * @return String    FQDN 형태의 도메인 문자열
     */
    private String buildFqdnForPowerDns(String fullDomain) {
        if (fullDomain.charAt(fullDomain.length() - 1) != '.') {
            fullDomain = fullDomain + ".";
        }

        return fullDomain;
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
    private boolean isNotOverMaxRecords(Member member) {
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

        List<SubDomainDto> subDomains = haveSubDomainService.getSubDomainDTOs(fullDomain);
        for (SubDomainDto subDomain : subDomains) {
            recordTypes.add(subDomain.type());
        }

        return recordTypes;
    }
}
