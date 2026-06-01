package top.nulldns.subdns.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.repository.MemberRepository;

import java.util.List;
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
                () -> new NoSuchElementException("비정상적인 접근 (없는 계정입니다)")
        );
    }

    public void deletePending(Member member) {
        member.setDeletePending();
        memberRepository.save(member);
    }

    public void delete(Member member) {
        memberRepository.delete(member);
    }

    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    public List<Member> searchMembers(String query) {
        return memberRepository.findByProviderIdContaining(query);
    }

    public void setBanned(Long memberId, boolean banned) {
        Member member = getMemberById(memberId);
        // Admin은 정지 불가능하게 (옵션)
        // if (checkAdminService.isAdmin(memberId)) return; 
        // -> MemberService는 Domain Service라 Facade에서 처리하거나 여기서 직접 확인
        memberRepository.save(Member.builder()
                .id(member.getId())
                .provider(member.getProvider())
                .providerId(member.getProviderId())
                .maxRecords(member.getMaxRecords())
                .banned(banned)
                .status(member.getStatus())
                .build());
    }

    public void updateMaxRecords(Long memberId, int maxRecords) {
        Member member = getMemberById(memberId);
        memberRepository.save(Member.builder()
                .id(member.getId())
                .provider(member.getProvider())
                .providerId(member.getProviderId())
                .maxRecords(maxRecords)
                .banned(member.isBanned())
                .status(member.getStatus())
                .build());
    }

    public long getTotalCount() {
        return memberRepository.count();
    }

    public long getBannedCount() {
        return memberRepository.findByBanned(true).size();
    }
}
