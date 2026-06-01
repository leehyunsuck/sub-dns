package top.nulldns.subdns.service.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.domain.HaveSubDomainService;
import top.nulldns.subdns.service.domain.MemberService;

import java.util.*;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.nulldns.subdns.dto.AdminDomainDto;
import top.nulldns.subdns.dto.AdminMemberDto;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {
    private final HaveSubDomainService haveSubDomainService;
    private final MemberService memberService;
    private final PDNSService pdnsService;
    private final top.nulldns.subdns.repository.HaveSubDomainRepository haveSubDomainRepository;

    // --- 통계 ---
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", memberService.getTotalCount());
        stats.put("bannedUsers", memberService.getBannedCount());
        stats.put("totalDomains", haveSubDomainService.getTotalDomainCount());
        stats.put("zones", pdnsService.getCachedZoneNames().size());
        
        List<Object[]> recordStats = haveSubDomainService.getRecordTypeStats();
        Map<String, Long> recordTypeCounts = new HashMap<>();
        for (Object[] row : recordStats) {
            recordTypeCounts.put((String) row[0], (Long) row[1]);
        }
        stats.put("recordTypeStats", recordTypeCounts);
        
        return stats;
    }

    // --- 유저 관리 ---
    public List<AdminMemberDto> searchMembers(String query) {
        List<Member> members;
        if (query == null || query.isBlank()) {
            members = memberService.getAllMembers();
        } else {
            members = memberService.searchMembers(query);
        }

        return members.stream()
                .map(m -> new AdminMemberDto(
                        m.getId(),
                        m.getProvider(),
                        m.getProviderId(),
                        m.getMaxRecords(),
                        m.isBanned(),
                        m.getStatus()
                ))
                .toList();
    }

    public void setMemberBanned(Long memberId, boolean banned) {
        memberService.setBanned(memberId, banned);
    }

    public void updateMemberMaxRecords(Long memberId, int maxRecords) {
        memberService.updateMaxRecords(memberId, maxRecords);
    }

    // --- 도메인 관리 ---
    @Transactional(readOnly = true)
    public Page<AdminDomainDto> searchDomainsPaged(String query, Pageable pageable) {
        Page<HaveSubDomain> domains;
        if (query == null || query.isBlank()) {
            domains = haveSubDomainRepository.findAll(pageable);
        } else {
            domains = haveSubDomainRepository.findByFullDomainContaining(query, pageable);
        }

        return domains.map(d -> new AdminDomainDto(
                d.getId(),
                d.getMember().getId(),
                d.getMember().getProviderId(),
                d.getFullDomain(),
                d.getRecordType(),
                d.getContent(),
                d.getExpiryDate(),
                d.getDomainStatus()
        ));
    }

    @Transactional(readOnly = true)
    public List<AdminDomainDto> getDomainsByMember(Long memberId) {
        return haveSubDomainRepository.findByMemberId(memberId).stream()
                .map(d -> new AdminDomainDto(
                        d.getId(),
                        d.getMember().getId(),
                        d.getMember().getProviderId(),
                        d.getFullDomain(),
                        d.getRecordType(),
                        d.getContent(),
                        d.getExpiryDate(),
                        d.getDomainStatus()
                ))
                .toList();
    }

    @Transactional
    public void updateExpiryDate(Long domainId, java.time.LocalDate newExpiryDate) {
        HaveSubDomain domain = haveSubDomainRepository.findById(domainId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 도메인입니다."));
        
        haveSubDomainRepository.save(HaveSubDomain.builder()
                .id(domain.getId())
                .member(domain.getMember())
                .fullDomain(domain.getFullDomain())
                .recordType(domain.getRecordType())
                .content(domain.getContent())
                .expiryDate(newExpiryDate)
                .domainStatus(domain.getDomainStatus())
                .build());
    }

    @Transactional(readOnly = true)
    public List<AdminDomainDto> searchDomains(String query) {
        List<HaveSubDomain> domains;
        if (query == null || query.isBlank()) {
            domains = haveSubDomainService.getAllSubDomains();
        } else {
            domains = haveSubDomainService.searchSubDomains(query);
        }

        return domains.stream()
                .map(d -> new AdminDomainDto(
                        d.getId(),
                        d.getMember().getId(),
                        d.getMember().getProviderId(),
                        d.getFullDomain(),
                        d.getRecordType(),
                        d.getContent(),
                        d.getExpiryDate(),
                        d.getDomainStatus()
                ))
                .toList();
    }

    public void transferDomainOwnership(String fullDomain, Long newMemberId) {
        Member newOwner = memberService.getMemberById(newMemberId);
        haveSubDomainService.transferOwnership(fullDomain, newOwner);
    }

    public void deleteDomainForce(String fullDomain) {
        List<HaveSubDomain> subDomains = haveSubDomainService.getSubDomainDTOs(fullDomain).stream()
                .map(dto -> haveSubDomainService.getHaveSubDomainByDetailInfo(null, fullDomain, dto.type())) // member 무시하고 찾기 위해 repository 수정 필요할 수도 있음
                .toList();
        // 실제로는 findByFullDomain 사용
        List<HaveSubDomain> targets = haveSubDomainService.getMemberSubDomainsByFullDomain(null, fullDomain);
        pdnsService.deleteSubRecords(targets);
    }

    // --- 존 관리 ---
    public void addZone(String zone) {
        pdnsService.createZone(zone);
    }

    public boolean deleteEndsWithZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return false;
        }

        List<HaveSubDomain> haveSubDomains = haveSubDomainService.getSubDomainsByZone(zone);
        haveSubDomainService.setDeletePending(haveSubDomains);
        pdnsService.deleteZone(zone); // 존 날리면 레코드도 다 날라감 (테이블 삭제)
        haveSubDomainService.deleteSubDomains(haveSubDomains); // HaveSubDomain 테이블에서 따로 삭제

        return true;
    }

    public Set<top.nulldns.subdns.dto.PDNSDto.ZoneName> getZones() {
        return pdnsService.getCachedZoneNames();
    }
}
