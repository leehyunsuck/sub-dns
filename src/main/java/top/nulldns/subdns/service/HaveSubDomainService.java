package top.nulldns.subdns.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dto.ResultMessageDTO;
import top.nulldns.subdns.repository.HaveSubDomainRepository;

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
}
