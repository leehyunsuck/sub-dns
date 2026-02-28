package top.nulldns.subdns.service.dbservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.repository.MemberRepository;

import java.util.NoSuchElementException;

// DB Service
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {
    private final MemberRepository memberRepository;

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

    public Member getMemberById(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(
                () -> new NoSuchElementException("해당 memberId의 멤버가 존재하지 않음")
        );
    }

    public void deleteMemberById(Long memberId) {
        memberRepository.deleteById(memberId);
    }

}
