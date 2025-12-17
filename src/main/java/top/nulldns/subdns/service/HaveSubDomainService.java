package top.nulldns.subdns.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.nulldns.subdns.dao.HaveSubDomain;
import top.nulldns.subdns.dto.ResultMessageDTO;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HaveSubDomainService {
    @Transactional
    public ResultMessageDTO<Void> renew(List<HaveSubDomain> haveSubDomainList) {
        if (!haveSubDomainList.getFirst().isRenewable()) {
            return ResultMessageDTO.<Void>builder().pass(false).message("갱신 가능한 날짜가 아닙니다").build();
        }

        for (HaveSubDomain haveSubDomain : haveSubDomainList) {
            haveSubDomain.renewDate();
        }

        return ResultMessageDTO.<Void>builder().pass(true).build();
    }
}
