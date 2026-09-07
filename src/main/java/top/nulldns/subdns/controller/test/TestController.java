package top.nulldns.subdns.controller.test;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import top.nulldns.subdns.SubDNSScheduler;
import top.nulldns.subdns.dao.Member;
import top.nulldns.subdns.repository.HaveSubDomainRepository;
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
    private final SubDNSScheduler subDNSScheduler;
    private final HaveSubDomainRepository haveSubDomainRepository;

    private static final String TEST_PROVIDER = "test_provider";
    private static final String TEST_ZONE = "nulldns.top"; // Assuming this is one of the cachedZoneNames
    private static final int MAX_TEST_RECORDS = 10;

    @GetMapping("/test")
    @ResponseBody
    public String testUi(HttpSession session) {
        if (session.getAttribute("memberId") == null || session.getAttribute("id") == null) {
            return "<script>alert('로그인이 필요합니다.'); loadPage('auth');</script>";
        }

        boolean isAdmin = checkAdminService.isAdmin((Long) session.getAttribute("memberId"));
        if (!isAdmin) {
            return "<script>alert('관리자 권한이 없습니다.'); loadPage('domainSearch');</script>";
        }

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; padding: 30px; line-height: 1.6; color: #333; background-color: #f8f9fa; min-height: 100vh;\">");
        html.append("<div style=\"max-width: 900px; margin: 0 auto; background: white; padding: 40px; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.08);\">");
        html.append("<h1 style=\"color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 15px; margin-bottom: 30px;\">DNS 서비스 통합 테스트</h1>");
        
        html.append("<p style=\"color: #7f8c8d; margin-bottom: 20px;\">원하는 테스트 항목을 선택하여 실행할 수 있습니다. (일부 연계 테스트는 이전 단계의 데이터가 필요할 수 있습니다.)</p>");
        
        html.append("<form id=\"testForm\" style=\"background: #f1f3f5; padding: 20px; border-radius: 8px; margin-bottom: 30px;\">");
        String[] stepNames = {
            "테스트 사용자 등록 (필수 기초 데이터)",
            "도메인 대량 등록 테스트 (최대 " + MAX_TEST_RECORDS + "개)",
            "최대 레코드 수 초과 검증",
            "도메인 삭제 기능 테스트",
            "레코드 타입 변경 및 왕복(Round-trip) 테스트",
            "스케줄러(Scheduler) 기능 통합 테스트",
            "CNAME 단독 존재 보장 테스트",
            "사용자 및 데이터 완전 삭제 (회원 탈퇴)"
        };
        for (int i = 0; i < stepNames.length; i++) {
            html.append("<label style=\"display: block; margin-bottom: 10px; cursor: pointer;\">")
                .append("<input type=\"checkbox\" name=\"steps\" value=\"").append(i + 1).append("\" checked style=\"margin-right: 10px; transform: scale(1.2);\"> ")
                .append((i + 1)).append(". ").append(stepNames[i])
                .append("</label>");
        }
        
        html.append("<div style=\"margin-top: 20px; padding-top: 20px; border-top: 1px solid #ddd; text-align: right;\">");
        html.append("<button type=\"button\" onclick=\"runTests()\" style=\"background: #3498db; color: white; border: none; padding: 10px 20px; border-radius: 5px; font-weight: bold; cursor: pointer; margin-right: 10px; transition: background 0.2s;\">선택 항목 실행</button>");
        html.append("<button type=\"button\" onclick=\"runAllTests()\" style=\"background: #2ecc71; color: white; border: none; padding: 10px 20px; border-radius: 5px; font-weight: bold; cursor: pointer; transition: background 0.2s;\">전체 실행</button>");
        html.append("</div>");
        html.append("</form>");
        
        html.append("<div id=\"loadingIndicator\" style=\"display: none; text-align: center; padding: 20px; color: #3498db; font-weight: bold;\">테스트 진행 중... 잠시만 기다려주세요.</div>");
        html.append("<div id=\"testResult\"></div>");

        html.append("<script>");
        html.append("function runTests() {");
        html.append("  document.getElementById('loadingIndicator').style.display = 'block';");
        html.append("  document.getElementById('testResult').innerHTML = '';");
        html.append("  const formData = new FormData(document.getElementById('testForm'));");
        html.append("  const searchParams = new URLSearchParams(formData);");
        html.append("  fetch('/test/execute?' + searchParams.toString())");
        html.append("    .then(r => r.text())");
        html.append("    .then(html => {");
        html.append("      document.getElementById('loadingIndicator').style.display = 'none';");
        html.append("      document.getElementById('testResult').innerHTML = html;");
        html.append("    })");
        html.append("    .catch(e => {");
        html.append("      document.getElementById('loadingIndicator').style.display = 'none';");
        html.append("      document.getElementById('testResult').innerHTML = '<div style=\"color:red;\">오류 발생: ' + e + '</div>';");
        html.append("    });");
        html.append("}");
        html.append("function runAllTests() {");
        html.append("  document.querySelectorAll('input[name=\"steps\"]').forEach(cb => cb.checked = true);");
        html.append("  runTests();");
        html.append("}");
        html.append("</script>");
        
        html.append("</div></div>");
        
        return html.toString();
    }

    @GetMapping("/test/execute")
    @ResponseBody
    public String executeTests(HttpSession session, @RequestParam(required = false) List<Integer> steps) {
        if (session.getAttribute("memberId") == null || session.getAttribute("id") == null) {
            return "<script>alert('로그인이 필요합니다.'); loadPage('auth');</script>";
        }

        boolean isAdmin = checkAdminService.isAdmin((Long) session.getAttribute("memberId"));
        if (!isAdmin) {
            return "<script>alert('관리자 권한이 없습니다.'); loadPage('domainSearch');</script>";
        }

        boolean runAll = steps == null || steps.isEmpty();

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("<div style=\"background: white; border: 1px solid #ddd; border-radius: 8px; overflow: hidden;\">");
        resultBuilder.append("<div style=\"background: #2c3e50; color: white; padding: 15px 20px; font-weight: bold;\">테스트 실행 결과</div>");
        resultBuilder.append("<div style=\"padding: 20px;\">");

        String testProviderId = UUID.randomUUID().toString();
        Long testMemberId = null;
        List<String> registeredFullDomains = new ArrayList<>();
        Member testMember = null;

        log.info("통합 테스트 실행 시작 (선택된 스텝: {})", runAll ? "전체" : steps);

        try {
            // 기초 설정 (테스트 사용자 등록) - 1번 스텝이 아니더라도 항상 실행하여 컨텍스트 유지, 단 출력은 1번이 선택된 경우만.
            boolean showStep1 = runAll || steps.contains(1);
            if (showStep1) appendHeader(resultBuilder, "1. 테스트 사용자 등록");
            
            testMember = memberService.findMember(TEST_PROVIDER, testProviderId);
            if (testMember == null) {
                testMember = memberService.signup(TEST_PROVIDER, testProviderId);
            }
            testMemberId = testMember.getId();
            
            if (showStep1) appendResult(resultBuilder, true, String.format("사용자 등록 성공 (MemberId: %d)", testMemberId));
            Thread.sleep(100);

            // 2. 도메인 등록 (최대 개수까지)
            if (runAll || steps.contains(2)) {
                appendHeader(resultBuilder, "2. 도메인 대량 등록 테스트 (최대 " + MAX_TEST_RECORDS + "개)");
                for (int i = 0; i < MAX_TEST_RECORDS; i++) {
                    String subDomainLabel = "test-" + generateRandomString(5);
                    String fullDomain = subDomainLabel + "." + TEST_ZONE;
                    String content = "1.1.1." + (i + 1);
                    try {
                        pdnsService.addRecord(subDomainLabel, TEST_ZONE, "A", content, testMemberId);
                        registeredFullDomains.add(fullDomain);
                        appendResult(resultBuilder, true, String.format("도메인 등록 성공: %s (%s)", fullDomain, content));
                        
                        // 첫 번째 도메인 등록 시 만료일(동적 갱신 로직) 적용 검증
                        if (i == 0) {
                            top.nulldns.subdns.dao.HaveSubDomain createdDomain = haveSubDomainRepository.findByMemberAndFullDomainAndRecordType(testMember, fullDomain, "A");
                            if (createdDomain != null && createdDomain.getExpiryDate() != null) {
                                appendResult(resultBuilder, true, "만료일 동적 설정 로직 확인 성공 (설정된 만료일: " + createdDomain.getExpiryDate() + ")");
                            } else {
                                appendResult(resultBuilder, false, "만료일 동적 설정 로직 확인 실패 (만료일이 null 이거나 조회 실패)");
                            }
                        }
                    } catch (Exception e) {
                        appendResult(resultBuilder, false, String.format("도메인 등록 실패: %s - 에러: %s", fullDomain, e.toString()));
                    }
                    Thread.sleep(50);
                }
                appendResult(resultBuilder, true, String.format("대량 등록 테스트 완료 (총 %d개 등록됨)", registeredFullDomains.size()));
            }

            // 3. 도메인 등록 (최대 개수 초과 시도)
            if (runAll || steps.contains(3)) {
                appendHeader(resultBuilder, "3. 최대 레코드 수 초과 검증");
                String overflowSubDomain = "overflow-" + generateRandomString(5);
                try {
                    pdnsService.addRecord(overflowSubDomain, TEST_ZONE, "A", "9.9.9.9", testMemberId);
                    appendResult(resultBuilder, false, "최대 개수 초과 시도 - 예상치 못한 성공 (버그 의심)");
                } catch (IllegalStateException e) {
                    if (e.toString().contains("최대 레코드 수 초과")) {
                        appendResult(resultBuilder, true, "최대 개수 초과 시도 - 예상대로 실패: " + e.toString());
                    } else {
                        appendResult(resultBuilder, false, "최대 개수 초과 시도 - 다른 에러 발생: " + e.toString());
                    }
                }
                Thread.sleep(100);
            }

            // 4. 도메인 삭제 시도 (2개)
            if (runAll || steps.contains(4)) {
                appendHeader(resultBuilder, "4. 도메인 삭제 기능 테스트");
                if (registeredFullDomains.size() >= 2) {
                    for (int i = 0; i < 2; i++) {
                        String domainToDelete = registeredFullDomains.remove(0);
                        String[] parts = pdnsService.splitZoneAndSubDomain(domainToDelete);
                        try {
                            pdnsService.deleteSubRecord(testMember, parts[0], parts[1]);
                            appendResult(resultBuilder, true, "도메인 삭제 성공: " + domainToDelete);
                        } catch (Exception e) {
                            appendResult(resultBuilder, false, "도메인 삭제 실패: " + domainToDelete + " - 에러: " + e.toString());
                        }
                        Thread.sleep(100);
                    }
                } else {
                    appendResult(resultBuilder, false, "도메인 삭제 테스트 건너뜀 (등록된 도메인 부족 - 2번 스텝을 함께 실행해주세요)");
                }
            }

            // 5. DNS 레코드 타입 변경 (왕복 테스트)
            if (runAll || steps.contains(5)) {
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
                            appendResult(resultBuilder, false, String.format("[%s] 초기 레코드 생성 실패 - 에러: %s", initialType, e.toString()));
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
                                appendResult(resultBuilder, false, String.format("왕복 테스트 실패 (%s <-> %s) - 에러: %s", initialType, targetType, e.toString()));
                            }
                        }
                        
                        // 정리
                        pdnsService.deleteSubRecord(testMember, sub, baseZone);
                    }
                } else {
                    appendResult(resultBuilder, false, "타입 변경 테스트 건너뜀 (기본 도메인 없음 - 2번 스텝을 함께 실행해주세요)");
                }
            }

            // 6. 스케줄러 기능 테스트
            if (runAll || steps.contains(6)) {
                appendHeader(resultBuilder, "6. 스케줄러(Scheduler) 기능 통합 테스트");
                
                // 6-1. 만료 도메인 삭제 테스트
                appendResult(resultBuilder, true, "[6-1] 만료 도메인 자동 삭제 테스트 시작");
                String expiredSub = "expired-" + generateRandomString(5);
                String expiredFull = expiredSub + "." + TEST_ZONE;
                top.nulldns.subdns.dao.HaveSubDomain expiredDomain = top.nulldns.subdns.dao.HaveSubDomain.builder()
                        .member(testMember)
                        .fullDomain(expiredFull)
                        .recordType("A")
                        .content("8.8.8.8")
                        .expiryDate(java.time.LocalDate.now().minusDays(1)) // 어제로 만료 설정
                        .domainStatus(top.nulldns.subdns.config.finalconfig.Status.ACTIVE)
                        .build();
                haveSubDomainRepository.save(expiredDomain);
                appendResult(resultBuilder, true, "만료된 테스트 도메인 생성 완료: " + expiredFull);
                
                subDNSScheduler.deleteExpiryDomain();
                Thread.sleep(200);
                
                boolean isStillExist = haveSubDomainRepository.existsByFullDomain(expiredFull);
                if (!isStillExist) {
                    appendResult(resultBuilder, true, "만료 도메인 삭제 확인 성공");
                } else {
                    appendResult(resultBuilder, false, "만료 도메인 삭제 확인 실패 (여전히 존재함)");
                }

                // 6-2. 보류 중인(Pending) 레코드 처리 테스트 (ADD, UPDATE, DELETE 전체)
                appendResult(resultBuilder, true, "[6-2] 모든 Pending 상태(ADD, UPDATE, DELETE) 자동 처리 테스트 시작");
                
                top.nulldns.subdns.config.finalconfig.Status[] pendingStatuses = {
                    top.nulldns.subdns.config.finalconfig.Status.ADD_PENDING,
                    top.nulldns.subdns.config.finalconfig.Status.UPDATE_PENDING,
                    top.nulldns.subdns.config.finalconfig.Status.DELETE_PENDING
                };

                List<String> pendingFullDomains = new ArrayList<>();
                for (top.nulldns.subdns.config.finalconfig.Status status : pendingStatuses) {
                    String sub = "pending-" + status.name().toLowerCase().split("_")[0] + "-" + generateRandomString(3);
                    String full = sub + "." + TEST_ZONE;
                    top.nulldns.subdns.dao.HaveSubDomain domain = top.nulldns.subdns.dao.HaveSubDomain.builder()
                            .member(testMember)
                            .fullDomain(full)
                            .recordType("A")
                            .content("7.7.7.7")
                            .expiryDate(java.time.LocalDate.now().plusMonths(6))
                            .domainStatus(status)
                            .build();
                    haveSubDomainRepository.save(domain);
                    pendingFullDomains.add(full);
                    appendResult(resultBuilder, true, status.name() + " 테스트 데이터 생성 완료: " + full);
                }

                appendResult(resultBuilder, true, "스케줄러 순차 실행 시작 (총 5회 호출)");
                for (int i = 0; i < 5; i++) {
                    subDNSScheduler.pendingDomain();
                    Thread.sleep(100);
                }

                // 결과 검증
                for (int i = 0; i < pendingStatuses.length; i++) {
                    top.nulldns.subdns.config.finalconfig.Status originalStatus = pendingStatuses[i];
                    String full = pendingFullDomains.get(i);
                    
                    if (originalStatus == top.nulldns.subdns.config.finalconfig.Status.DELETE_PENDING) {
                        boolean exists = haveSubDomainRepository.existsByFullDomain(full);
                        appendResult(resultBuilder, !exists, "DELETE_PENDING 처리 확인: " + (exists ? "실패 (삭제되지 않음)" : "성공 (삭제됨)"));
                    } else {
                        top.nulldns.subdns.dao.HaveSubDomain check = haveSubDomainRepository.findByMemberAndFullDomainAndRecordType(testMember, full, "A");
                        boolean isSuccess = (check != null && check.getDomainStatus() == top.nulldns.subdns.config.finalconfig.Status.ACTIVE);
                        appendResult(resultBuilder, isSuccess, originalStatus.name() + " -> ACTIVE 처리 확인: " + (isSuccess ? "성공" : "실패"));
                    }
                }

                for (int i = 0; i < pendingStatuses.length; i++) {
                    if (pendingStatuses[i] != top.nulldns.subdns.config.finalconfig.Status.DELETE_PENDING) {
                        try {
                            String full = pendingFullDomains.get(i);
                            String sub = full.substring(0, full.indexOf('.'));
                            pdnsService.deleteSubRecord(testMember, sub, TEST_ZONE);
                        } catch (Exception ignored) {}
                    }
                }
            }

            // 7. CNAME과 다른 레코드 타입의 단독 존재(Co-existence 방지) 테스트
            if (runAll || steps.contains(7)) {
                appendHeader(resultBuilder, "7. CNAME 단독 존재 보장 테스트");
                String coexistSub = "coexist-" + generateRandomString(4);
                String coexistFull = coexistSub + "." + TEST_ZONE;
                
                try {
                    // 1) A 레코드 추가
                    pdnsService.addRecord(coexistSub, TEST_ZONE, "A", "1.1.1.1", testMemberId);
                    appendResult(resultBuilder, true, "A 레코드 최초 추가 성공");
                    
                    // 2) 동일 도메인에 CNAME 레코드 추가 -> 기존 A 레코드는 삭제 처리(DELETE_PENDING) 되어야 함
                    pdnsService.addRecord(coexistSub, TEST_ZONE, "CNAME", "test.nulldns.top", testMemberId);
                    
                    top.nulldns.subdns.dao.HaveSubDomain deletedA = haveSubDomainRepository.findByMemberAndFullDomainAndRecordType(testMember, coexistFull, "A");
                    top.nulldns.subdns.dao.HaveSubDomain activeCname = haveSubDomainRepository.findByMemberAndFullDomainAndRecordType(testMember, coexistFull, "CNAME");
                    
                    boolean cnameSuccess = (activeCname != null && activeCname.getDomainStatus() == top.nulldns.subdns.config.finalconfig.Status.ACTIVE);
                    boolean aDeleted = (deletedA == null || deletedA.getDomainStatus() == top.nulldns.subdns.config.finalconfig.Status.DELETE_PENDING);
                    
                    if (cnameSuccess && aDeleted) {
                        appendResult(resultBuilder, true, "CNAME 추가 시 기존 A 레코드 자동 삭제 확인 성공");
                    } else {
                        appendResult(resultBuilder, false, "CNAME 단독 존재 로직 확인 실패 (CNAME 상태: " + cnameSuccess + ", A 삭제 상태: " + aDeleted + ")");
                    }
                    
                    // 정리
                    pdnsService.deleteSubRecord(testMember, coexistSub, TEST_ZONE);
                } catch (Exception e) {
                    appendResult(resultBuilder, false, "CNAME 단독 존재 테스트 중 에러 발생: " + e.toString());
                }
            }

            // 8. 회원 탈퇴
            if (runAll || steps.contains(8)) {
                appendHeader(resultBuilder, "8. 사용자 및 데이터 완전 삭제 (회원 탈퇴)");
                if (testMemberId != null) {
                    authService.deleteUserAndData(testMemberId);
                    try {
                        memberService.getMemberById(testMemberId);
                        appendResult(resultBuilder, false, "사용자 삭제 실패 (데이터가 남아있음)");
                    } catch (NoSuchElementException e) {
                        appendResult(resultBuilder, true, "사용자 및 모든 도메인 데이터 삭제 성공");
                    }
                }
            }

        } catch (Exception e) {
            appendHeader(resultBuilder, "CRITICAL ERROR");
            appendResult(resultBuilder, false, "테스트 중 예상치 못한 치명적 에러 발생: " + e.toString());
            log.error("테스트 에러: {}", e.toString());
            if (testMemberId != null) {
                try { authService.deleteUserAndData(testMemberId); } catch (Exception ignored) {}
            }
        } finally {
            resultBuilder.append("<div style=\"margin-top: 30px; padding-top: 15px; border-top: 1px dashed #ccc; text-align: right; color: #7f8c8d; font-size: 0.9em;\">");
            resultBuilder.append("테스트 완료 시간: " + java.time.LocalDateTime.now());
            resultBuilder.append("</div>");
            resultBuilder.append("</div></div>");
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
        else log.error("결과: {}", message);

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
