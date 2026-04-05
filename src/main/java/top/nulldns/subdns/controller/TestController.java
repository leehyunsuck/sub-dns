package top.nulldns.subdns.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.service.facade.AuthService;
import top.nulldns.subdns.service.facade.PDNSService;
import top.nulldns.subdns.service.domain.CheckAdminService;
import top.nulldns.subdns.service.domain.MemberService;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.NoSuchElementException;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final MemberService memberService;
    private final PDNSService pdnsService;
    private final AuthService authService;
    private final CheckAdminService checkAdminService;

    private static final String TEST_PROVIDER = "test_provider";
    private static final String TEST_ZONE = "nulldns.top"; // Assuming this is one of the cachedZoneNames
    private static final int MAX_TEST_RECORDS = 10;

    @GetMapping("/test")
    @ResponseBody
    public String test(HttpSession session) {
        if (session.getAttribute("memberId") == null || session.getAttribute("id") == null) {
            return "<script>alert('로그인이 필요합니다.'); history.back();</script>";
        }

        boolean isAdmin = checkAdminService.isAdmin((Long) session.getAttribute("memberId"));
        if (!isAdmin) {
            return "<script>alert('관리자 권한이 없습니다.'); history.back();</script>";
        }

        StringBuilder resultBuilder = new StringBuilder();
        
        // 전체 스타일 컨테이너 시작
        resultBuilder.append("<div style=\"font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; padding: 30px; line-height: 1.6; color: #333; background-color: #f8f9fa; min-height: 100vh;\">");
        resultBuilder.append("<div style=\"max-width: 900px; margin: 0 auto; background: white; padding: 40px; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.08);\">");
        resultBuilder.append("<h1 style=\"color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 15px; margin-bottom: 30px;\">DNS 서비스 통합 테스트 리포트</h1>");

        String testProviderId = UUID.randomUUID().toString();
        Long testMemberId = null;
        List<String> registeredFullDomains = new ArrayList<>();

        log.info("통합 테스트 시작");

        try {
            // 1. 사용자 등록
            appendHeader(resultBuilder, "1. 테스트 사용자 등록");
            Member testMember = memberService.loginOrSignup(TEST_PROVIDER, testProviderId);
            testMemberId = testMember.getId();
            appendResult(resultBuilder, true, String.format("사용자 등록 성공 (MemberId: %d)", testMemberId));
            Thread.sleep(100);

            // 2. 도메인 등록 (최대 개수까지)
            appendHeader(resultBuilder, "2. 도메인 대량 등록 테스트 (최대 " + MAX_TEST_RECORDS + "개)");
            for (int i = 0; i < MAX_TEST_RECORDS; i++) {
                String subDomainLabel = "test-" + generateRandomString(5);
                String fullDomain = subDomainLabel + "." + TEST_ZONE;
                String content = "1.1.1." + (i + 1);
                try {
                    pdnsService.addRecord(subDomainLabel, TEST_ZONE, "A", content, testMemberId);
                    registeredFullDomains.add(fullDomain);
                    appendResult(resultBuilder, true, String.format("도메인 등록 성공: %s (%s)", fullDomain, content));
                } catch (Exception e) {
                    appendResult(resultBuilder, false, String.format("도메인 등록 실패: %s - 에러: %s", fullDomain, e.getMessage()));
                }
                Thread.sleep(50);
            }
            appendResult(resultBuilder, true, String.format("대량 등록 테스트 완료 (총 %d개 등록됨)", registeredFullDomains.size()));

            // 3. 도메인 등록 (최대 개수 초과 시도)
            appendHeader(resultBuilder, "3. 최대 레코드 수 초과 검증");
            String overflowSubDomain = "overflow-" + generateRandomString(5);
            try {
                pdnsService.addRecord(overflowSubDomain, TEST_ZONE, "A", "9.9.9.9", testMemberId);
                appendResult(resultBuilder, false, "최대 개수 초과 시도 - 예상치 못한 성공 (버그 의심)");
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("최대 레코드 수 초과")) {
                    appendResult(resultBuilder, true, "최대 개수 초과 시도 - 예상대로 실패: " + e.getMessage());
                } else {
                    appendResult(resultBuilder, false, "최대 개수 초과 시도 - 다른 에러 발생: " + e.getMessage());
                }
            }
            Thread.sleep(100);

            // 4. 도메인 삭제 시도 (2개)
            appendHeader(resultBuilder, "4. 도메인 삭제 기능 테스트");
            if (registeredFullDomains.size() >= 2) {
                for (int i = 0; i < 2; i++) {
                    String domainToDelete = registeredFullDomains.remove(0);
                    String[] parts = pdnsService.splitZoneAndSubDomain(domainToDelete);
                    try {
                        pdnsService.deleteSubRecord(testMember, parts[0], parts[1]);
                        appendResult(resultBuilder, true, "도메인 삭제 성공: " + domainToDelete);
                    } catch (Exception e) {
                        appendResult(resultBuilder, false, "도메인 삭제 실패: " + domainToDelete + " - 에러: " + e.getMessage());
                    }
                    Thread.sleep(100);
                }
            } else {
                appendResult(resultBuilder, false, "도메인 삭제 테스트 건너뜀 (등록된 도메인 부족)");
            }

            // 5. DNS 레코드 타입 변경 (왕복 테스트)
            appendHeader(resultBuilder, "5. 레코드 타입 변경 및 왕복(Round-trip) 테스트");

            if (!registeredFullDomains.isEmpty()) {
                Map<String, String> recordTypeContents = new LinkedHashMap<>();
                recordTypeContents.put("A", "192.168.1.1");
                recordTypeContents.put("CNAME", "testnull.nulldns.top");
                recordTypeContents.put("TXT", "\"test-txt\"");
                recordTypeContents.put("AAAA", "::1");

                String baseFullDomain = registeredFullDomains.get(0);
                String[] baseParts = pdnsService.splitZoneAndSubDomain(baseFullDomain);
                String baseZone = baseParts[1];

                for (Map.Entry<String, String> initialRecord : recordTypeContents.entrySet()) {
                    String initialType = initialRecord.getKey();
                    String initialContent = initialRecord.getValue();

                    String sub = "chg-" + initialType.toLowerCase() + "-" + generateRandomString(3);

                    // 초기 레코드 생성
                    try {
                        pdnsService.addRecord(sub, baseZone, initialType, initialContent, testMemberId);
                        appendResult(resultBuilder, true, String.format("[%s] 초기 레코드 생성 성공", initialType));
                    } catch (Exception e) {
                        appendResult(resultBuilder, false, String.format("[%s] 초기 레코드 생성 실패 - 에러: %s", initialType, e.getMessage()));
                        continue;
                    }

                    // 타겟 타입으로 왕복 테스트
                    for (Map.Entry<String, String> targetRecord : recordTypeContents.entrySet()) {
                        String targetType = targetRecord.getKey();
                        if (initialType.equals(targetType)) continue;

                        try {
                            // 변경
                            pdnsService.addRecord(sub, baseZone, targetType, targetRecord.getValue(), testMemberId);
                            appendResult(resultBuilder, true, String.format("타입 변경 성공: %s → %s", initialType, targetType));
                            // 복구
                            pdnsService.addRecord(sub, baseZone, initialType, initialContent, testMemberId);
                            appendResult(resultBuilder, true, String.format("타입 복구 성공: %s → %s", targetType, initialType));
                        } catch (Exception e) {
                            appendResult(resultBuilder, false, String.format("왕복 테스트 실패 (%s <-> %s) - 에러: %s", initialType, targetType, e.getMessage()));
                        }
                    }
                    
                    // 정리
                    pdnsService.deleteSubRecord(testMember, sub, baseZone);
                }
            } else {
                appendResult(resultBuilder, false, "타입 변경 테스트 건너뜀 (기본 도메인 없음)");
            }

            // 6. 회원 탈퇴
            appendHeader(resultBuilder, "6. 사용자 및 데이터 완전 삭제 (회원 탈퇴)");
            if (testMemberId != null) {
                authService.deleteUserAndData(testMemberId);
                try {
                    memberService.getMemberById(testMemberId);
                    appendResult(resultBuilder, false, "사용자 삭제 실패 (데이터가 남아있음)");
                } catch (NoSuchElementException e) {
                    appendResult(resultBuilder, true, "사용자 및 모든 도메인 데이터 삭제 성공");
                }
            }

        } catch (Exception e) {
            appendHeader(resultBuilder, "CRITICAL ERROR");
            appendResult(resultBuilder, false, "테스트 중 예상치 못한 치명적 에러 발생: " + e.getMessage());
            log.error("테스트 에러", e);
            if (testMemberId != null) {
                try { authService.deleteUserAndData(testMemberId); } catch (Exception ignored) {}
            }
        } finally {
            resultBuilder.append("<div style=\"margin-top: 40px; padding-top: 20px; border-top: 1px solid #eee; text-align: center; color: #7f8c8d; font-size: 0.9em;\">");
            resultBuilder.append("테스트 완료 시간: " + java.time.LocalDateTime.now());
            resultBuilder.append("</div>");
            resultBuilder.append("</div></div>"); // 컨테이너 닫기
        }

        return resultBuilder.toString();
    }

    private void appendHeader(StringBuilder sb, String title) {
        log.info("[STEP] " + title);
        sb.append("<div style=\"margin-top: 25px; margin-bottom: 10px; padding: 10px 15px; background-color: #f1f3f5; border-left: 5px solid #2c3e50; font-weight: bold; font-size: 1.1em; color: #2c3e50;\">")
          .append(title)
          .append("</div>");
    }

    private void appendResult(StringBuilder sb, boolean success, String message) {
        if (success) log.info("결과: " + message);
        else log.error("결과: " + message);

        // '성공'과 '실패' 단어에만 색상 적용
        String color = success ? "#2ecc71" : "#e74c3c";
        String styledMessage = message
                .replace("성공", "<b style=\"color: " + color + ";\">성공</b>")
                .replace("실패", "<b style=\"color: " + color + ";\">실패</b>");

        sb.append("<div style=\"padding: 4px 20px; color: #555; font-size: 0.95em; border-bottom: 1px inset #f9f9f9;\">")
          .append("<span style=\"color: #95a5a6; margin-right: 8px;\">└</span>")
          .append(styledMessage)
          .append("</div>");
    }

    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
