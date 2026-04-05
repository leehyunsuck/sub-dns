package top.nulldns.subdns.service.facade;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.domain.MemberService;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final PDNSService pdnsService;
    private final MemberService memberService;

    public void deleteUserAndData(Long memberId) {
        Member member = memberService.getMemberById(memberId);
        memberService.deletePending(member);
        pdnsService.deleteSubRecordsByMemberId(memberId);
        memberService.delete(member);
    }
}