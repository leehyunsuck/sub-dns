package top.nulldns.subdns.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.dto.ResultMessageDTO;
import top.nulldns.subdns.repository.HaveSubDomainRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HaveSubDomainService {
    private final HaveSubDomainRepository haveSubDomainRepository;

    @Transactional
    public ResultMessageDTO<Integer> renew(Long memberId, String fullDomain) {
        List<HaveSubDomain> subDomains = haveSubDomainRepository.findAllByMemberIdAndFullDomain(memberId, fullDomain);

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

    public void deleteEndsWithDomain(String domain) {
        int deleteCount = haveSubDomainRepository.deleteByDomain(domain);
        log.info("도메인 및 하위 도메인 삭제 완료: {} (삭제된 레코드 수: {})", domain, deleteCount);
    }

    public void addSubDomain(HaveSubDomain haveSubDomain) {
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

    public int getOwnedDomainCount(Long memberId) {
        return haveSubDomainRepository.countDistinctFullDomainByMemberId(memberId);
    }

    public LocalDate findExpiryDate(Long memberId, String fullDomain) {
        return haveSubDomainRepository.findAllByMemberIdAndFullDomain(memberId, fullDomain).getFirst().getExpiryDate();
    }

    public boolean isExistFullDomain(String fullDomain) {
        return haveSubDomainRepository.existsByFullDomain(fullDomain);
    }
}
