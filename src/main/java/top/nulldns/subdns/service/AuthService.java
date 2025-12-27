package top.nulldns.subdns.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.repository.HaveSubDomainRepository;
import top.nulldns.subdns.repository.MemberRepository;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;
    private final HaveSubDomainRepository haveSubDomainRepository;
    private final PDNSService pdnsService;

    @Transactional
    public Member loginOrSignup(String provider, String providerId) {
        return memberRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    Member newMember = Member.builder()
                            .provider(provider)
                            .providerId(providerId)
                            .build();
                    return memberRepository.save(newMember);
                });
    }

    @Transactional
    public void deleteUserAndData(Long memberId) {
        pdnsService.deleteAllSubRecordsByMemberId(memberId);
        haveSubDomainRepository.deleteAllByMemberId(memberId);
        memberRepository.deleteById(memberId);
    }
}