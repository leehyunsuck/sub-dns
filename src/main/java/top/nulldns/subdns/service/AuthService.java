package top.nulldns.subdns.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.entity.Member;
import top.nulldns.subdns.repository.MemberRepository;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;

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
}
