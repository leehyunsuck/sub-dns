package top.nulldns.subdns.service.dbservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.dto.ResultMessageDTO;
import top.nulldns.subdns.repository.HaveSubDomainRepository;

import java.time.LocalDate;
import java.util.List;

// DB Service
@Service
@RequiredArgsConstructor
@Slf4j
public class HaveSubDomainService {
    private final HaveSubDomainRepository haveSubDomainRepository;

    @Transactional
    public ResultMessageDTO<Integer> renew(Long memberId, String fullDomain) {
        List<HaveSubDomain> subDomains = haveSubDomainRepository.findByMemberIdAndFullDomain(memberId, fullDomain);

        if (subDomains.isEmpty()) {
            return ResultMessageDTO.<Integer>builder().pass(false).data(404).build();
        }

        if (!subDomains.getFirst().isRenewable()) {
            return ResultMessageDTO.<Integer>builder().pass(false).data(400).build();
        }

        for (HaveSubDomain subDomain : subDomains) {
            subDomain.renewDate();
        }

        return ResultMessageDTO.<Integer>builder().pass(true).data(200).build();
    }

    public void renewDate(Long memberId, String fullDomain) {
        List<HaveSubDomain> subDomains = haveSubDomainRepository.findByMemberIdAndFullDomain(memberId, fullDomain);
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

    public void deleteEndsWithDomain(String domain) {
        int deleteCount = haveSubDomainRepository.deleteByDomain(domain);
        log.info("도메인 및 하위 도메인 삭제 완료: {} (삭제된 레코드 수: {})", domain, deleteCount);
    }

    public void addSubDomain(HaveSubDomain haveSubDomain, boolean isContentUpdate) {
        if (isContentUpdate) {
            HaveSubDomain existing = haveSubDomainRepository.findByMemberIdAndFullDomainAndRecordType(
                    haveSubDomain.getMember().getId(),
                    haveSubDomain.getFullDomain(),
                    haveSubDomain.getRecordType()
            );

            if (existing != null) {
                existing.updateContent(haveSubDomain.getContent());
                haveSubDomain = existing;
            }
        }
        haveSubDomainRepository.save(haveSubDomain);
    }

    public HaveSubDomain buildHaveSubDomainAddForm(Member member, String subDomain, String zone, String type, String content, LocalDate expiryDate) {
        HaveSubDomain.HaveSubDomainBuilder builder = HaveSubDomain.builder()
                .member(member)
                .fullDomain(subDomain + "." + zone)
                .recordType(type)
                .content(content);
        if (expiryDate != null) {
            builder.expiryDate(expiryDate);
        }

        return builder.build();
    }

    public void deleteHaveSubDomain(Long memberId, String fullDomain, String type) {
        haveSubDomainRepository.deleteByMemberIdAndFullDomainAndRecordType(memberId, fullDomain, type);
    }

    public void deleteAllByMember(Long memberId) {
        haveSubDomainRepository.deleteAllByMemberId(memberId);
    }

    public void deleteHaveSubDomains(List<HaveSubDomain> haveSubDomains) {
        haveSubDomainRepository.deleteAll(haveSubDomains);
    }

    public List<HaveSubDomain> getHaveSubDomains(Long memberId) {
        return haveSubDomainRepository.findDistinctByMemberId(memberId);
    }

    public int getOwnedDomainCount(Long memberId) {
        return haveSubDomainRepository.countDistinctFullDomainByMemberId(memberId);
    }

    public LocalDate getExpiryDate(Long memberId, String fullDomain) {
        return haveSubDomainRepository.findByMemberIdAndFullDomain(memberId, fullDomain).getFirst().getExpiryDate();
    }

    public boolean isOwnerOfDomain(Long memberId, String fullDomain) {
        return haveSubDomainRepository.existsByFullDomainAndMemberId(fullDomain, memberId);
    }

    public boolean canAddSubDomain(String fullDomain) {
        return !haveSubDomainRepository.existsByFullDomain(fullDomain);
    }

    public List<HaveSubDomain> getExpiredSubDomains(LocalDate date) {
        return haveSubDomainRepository.findByExpiryDateBefore(date);
    }

    public List<HaveSubDomain> getMemberDomains(Long memberId) {
        return haveSubDomainRepository.findByMemberId(memberId);
    }

    public List<HaveSubDomain> getHaveSubDomainsByMemberIdAndFullDomain(Long memberId, String fullDomain) {
        return haveSubDomainRepository.findByMemberIdAndFullDomain(memberId, fullDomain);
    }
}
