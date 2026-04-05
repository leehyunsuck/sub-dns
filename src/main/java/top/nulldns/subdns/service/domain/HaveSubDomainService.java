package top.nulldns.subdns.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.nulldns.subdns.config.finalconfig.Status;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.dto.SubDomainDto;
import top.nulldns.subdns.repository.HaveSubDomainRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// DB Service
@Service
@RequiredArgsConstructor
@Slf4j
public class HaveSubDomainService {
    private final HaveSubDomainRepository haveSubDomainRepository;

    public void renewDate(Member member, String fullDomain) {
        List<HaveSubDomain> subDomains = haveSubDomainRepository.findByMemberAndFullDomain(member, fullDomain);
        if (subDomains.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "도메인 소유자가 아닙니다.");
        }

        boolean canRenew = subDomains.getFirst().isRenewable();
        if (!canRenew) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "도메인을 갱신할 수 없습니다. 갱신 가능 날짜가 아직 도래하지 않았습니다.");
        }

        for (HaveSubDomain subDomain : subDomains) {
            subDomain.renewDate();
        }
    }

    public void updateContentAndSetPending(HaveSubDomain haveSubDomain, String content) {
        haveSubDomain.changeToUpdatePending();
        haveSubDomain.updateContent(content);
        haveSubDomainRepository.save(haveSubDomain);
    }

    public void setStatusActivity(List<HaveSubDomain> haveSubDomains) {
        for (HaveSubDomain haveSubDomain : haveSubDomains) {
            haveSubDomain.changeToActive();
        }

        haveSubDomainRepository.saveAll(haveSubDomains);
    }

    public void setStatusActivity(HaveSubDomain haveSubDomain) {
        haveSubDomain.changeToActive();
        haveSubDomainRepository.save(haveSubDomain);
    }

    public void setDeletePending(List<HaveSubDomain> haveSubDomains) {
        for (HaveSubDomain haveSubDomain : haveSubDomains) {
            haveSubDomain.changeToDeletePending();
        }
        haveSubDomainRepository.saveAll(haveSubDomains);
    }

    public void deleteSubDomains(List<HaveSubDomain> haveSubDomains) {
        haveSubDomainRepository.deleteAll(haveSubDomains);
    }

    public int getOwnedDomainCount(Long memberId) {
        return haveSubDomainRepository.countDistinctFullDomainByMemberId(memberId);
    }

    public LocalDate getExpiryDate(Member member, String fullDomain) {
        return haveSubDomainRepository.findByMemberAndFullDomain(member, fullDomain).getFirst().getExpiryDate();
    }

    public boolean isOwnerOfDomain(Member member, String fullDomain) {
        return haveSubDomainRepository.existsByFullDomainAndMember(fullDomain, member);
    }

    public boolean canAddSubDomain(String fullDomain) {
        return !haveSubDomainRepository.existsByFullDomain(fullDomain);
    }

    public HaveSubDomain getHaveSubDomainByDetailInfo(Member member, String fullDomain, String recordType) {
        return haveSubDomainRepository.findByMemberAndFullDomainAndRecordType(member, fullDomain, recordType);
    }

    public HaveSubDomain newHaveSubDomain(Member member, String fullDomain, String recordType, String content, LocalDate expiryDate) {
        return haveSubDomainRepository.save(
                HaveSubDomain.builder()
                        .member(member)
                        .fullDomain(fullDomain)
                        .recordType(recordType)
                        .content(content)
                        .expiryDate(expiryDate)
                        .domainStatus(Status.ADD_PENDING)
                        .build()
        );
    }

    public List<HaveSubDomain> getDistinctSubDomainsByMemberId(Long memberId) {
        return haveSubDomainRepository.findDistinctByMemberId(memberId);
    }

    public List<HaveSubDomain> getAvailableSubDomains(Status status) {
        return haveSubDomainRepository.findByDomainStatusAndExpiryDateAfter(status, LocalDate.now());
    }

    public List<HaveSubDomain> getMemberSubDomains(Member member) {
        return haveSubDomainRepository.findByMember(member);
    }

    public List<HaveSubDomain> getMemberSubDomainsByFullDomain(Member member, String fullDomain) {
        return haveSubDomainRepository.findByMemberAndFullDomain(member, fullDomain);
    }

    public List<HaveSubDomain> getSubDomainsByZone(String zone) {
        return haveSubDomainRepository.findByZoneIncludingFullDomain(zone);
    }

    public List<HaveSubDomain> getExpiredSubDomains(LocalDate date, int limit) {
        return haveSubDomainRepository.findExpired(date, Status.ACTIVE, PageRequest.of(0, limit));
    }

    public List<SubDomainDto> getSubDomainDTOs(String fullDomain) {
        List<HaveSubDomain> haveSubDomains = haveSubDomainRepository.findByFullDomain(fullDomain);

        List<SubDomainDto> subDomains = new ArrayList<>();
        for (HaveSubDomain haveSubDomain : haveSubDomains) {
            subDomains.add(
                    new SubDomainDto(fullDomain, haveSubDomain.getRecordType(), haveSubDomain.getContent())
            );
        }

        return subDomains;
    }
}
