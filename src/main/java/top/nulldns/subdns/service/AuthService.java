package top.nulldns.subdns.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.nulldns.subdns.service.dbservice.MemberService;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final PDNSService pdnsService;
    private final MemberService memberService;

    public void deleteUserAndData(Long memberId) {
        pdnsService.deleteAllSubRecords(memberId);
        memberService.deleteMemberById(memberId);
    }
}