// ============================================================
// [TEST MODE] 백엔드 없는 가짜 데이터 처리 (Mock API)
// ============================================================

// 브라우저 램(RAM)에 임시 저장할 가짜 세션
let mockSession = null;

function mockFetch(url, method, body) {
    return new Promise((resolve) => {
        console.log(`[Mock API] ${method} ${url}`, body);

        setTimeout(() => {
            // 1. 로그인 (POST /api/login)
            if (url === '/api/login') {
                if (body.pw === '1234') {
                    mockSession = { email: body.email, name: '테스트유저' };
                    resolve({ ok: true, json: () => Promise.resolve({ message: "성공", name: mockSession.name }) });
                } else {
                    resolve({ ok: false, json: () => Promise.resolve({ message: "비밀번호는 1234 입니다." }) });
                }
            }
            // 2. 내 정보 (GET /api/me)
            else if (url === '/api/me') {
                if (mockSession) {
                    resolve({ ok: true, json: () => Promise.resolve({ isLoggedIn: true, email: mockSession.email, name: mockSession.name }) });
                } else {
                    resolve({ ok: true, json: () => Promise.resolve({ isLoggedIn: false }) });
                }
            }
            // 3. 도메인 검색 (GET /api/domain/search)
            else if (url.includes('/api/domain/search')) {
                const prefix = url.split('prefix=')[1] || 'unknown';
                const results = [
                    { fullDomain: `${prefix}.nulldns.top`, available: true },
                    { fullDomain: `${prefix}.test.top`, available: false },
                    { fullDomain: `${prefix}.game.server`, available: true }
                ];
                resolve({ ok: true, json: () => Promise.resolve(results) });
            }
                // 4. 도메인 상세 조회 (GET /api/domain/detail)
            // ★ 여기가 핵심: 목록에서 클릭 시 이 데이터를 반환함
            else if (url.includes('/api/domain/detail')) {
                resolve({
                    ok: true,
                    json: () => Promise.resolve({
                        domain: "test.nulldns.top",
                        type: "A", 
                        value: "192.168.0.100",
                        ttl: 3600
                    })
                });
            }
            // 5. 도메인 등록/수정 (POST /api/domain/register)
            else if (url === '/api/domain/register') {
                resolve({ ok: true, json: () => Promise.resolve({ message: "성공적으로 저장되었습니다." }) });
            }
            // 6. 로그아웃
            else if (url === '/api/logout') {
                mockSession = null;
                resolve({ ok: true, json: () => Promise.resolve({ message: "로그아웃" }) });
            }
            // 기타
            else {
                resolve({ ok: true, json: () => Promise.resolve({ message: "성공" }) });
            }

        }, 300); // 0.3초 로딩 딜레이
    });
}


// ============================================================
// [UI Logic] 실제 프론트엔드 코드
// ============================================================

let currentUser = null;

document.addEventListener('DOMContentLoaded', () => {
    checkAuth();
    showPage('domainSearch');
});

function showPage(page) {
    document.querySelectorAll('main').forEach(m => m.classList.add('hidden'));
    const target = document.getElementById(page + 'Page');
    if (target) target.classList.remove('hidden');
}

// 요청 헬퍼
async function sendRequest(url, method, body, btn, msgEl) {
    if (btn) btn.disabled = true;
    if (msgEl) { msgEl.style.color = "#333"; msgEl.textContent = "요청 중..."; }

    try {
        const res = await mockFetch(url, method, body); // 테스트용 mockFetch 사용
        const data = await res.json();

        if (res.ok) {
            if (msgEl) { msgEl.style.color = "green"; msgEl.textContent = data.message || "성공"; }
            return { success: true, data: data };
        } else {
            if (msgEl) { msgEl.style.color = "red"; msgEl.textContent = data.message || "실패"; }
            return { success: false, data: data };
        }
    } catch (e) {
        console.error(e);
        return { success: false };
    } finally {
        if (btn) btn.disabled = false;
    }
}

// [1] 로그인 확인
async function checkAuth() {
    const result = await sendRequest('/api/me', 'GET');
    const authSection = document.getElementById('authSection');

    if (result.success && result.data.isLoggedIn) {
        currentUser = result.data;
        authSection.innerHTML = `
            <span id="userMenu" onclick="showPage('profile')">${currentUser.name}님</span>
            <a href="#" onclick="logout()" class="btn-logout">(로그아웃)</a>
        `;
        const emailField = document.getElementById('profileEmail');
        if(emailField) emailField.value = currentUser.email;
    } else {
        currentUser = null;
        authSection.innerHTML = `<a href="#" onclick="showPage('login')">로그인</a> <a href="#" onclick="showPage('register')">회원가입</a>`;
    }
}

// [2] 도메인 검색
async function searchDomain() {
    const prefix = document.getElementById('searchInput').value.trim();
    const resultBox = document.getElementById('searchResult');

    if (!prefix) { alert("도메인 이름을 입력하세요"); return; }
    resultBox.innerHTML = '<p style="text-align:center">검색 중...</p>';

    const res = await sendRequest(`/api/domain/search?prefix=${prefix}`, 'GET');

    if (res.success) {
        let html = '';
        res.data.forEach(item => {
            const isAvailable = item.available;
            const statusHtml = isAvailable ?
                `<span class="status-ok">[사용가능]</span>` : `<span class="status-no">[사용불가]</span>`;

            let btnProps = isAvailable ?
                (currentUser ? `onclick="registerDomain('${item.fullDomain}')"` : 'disabled') : 'disabled';

            let btnText = isAvailable ? (currentUser ? '등록하기' : '로그인 필요') : '등록불가';
            let btnClass = isAvailable && currentUser ? '' : 'style="background:#aaa"';

            html += `
            <div class="result-row">
                <span>${item.fullDomain} ${statusHtml}</span>
                <button class="btn-sm" ${btnClass} ${btnProps}>${btnText}</button>
            </div>`;
        });
        resultBox.innerHTML = html;
    }
}

// [3] 도메인 신규 등록 화면 열기
function registerDomain(fullDomain) {
    if (!currentUser) { alert("로그인이 필요합니다."); showPage('login'); return; }

    document.getElementById('domainTitle').innerText = fullDomain;
    document.getElementById('recordValue').value = '';
    document.getElementById('saveBtn').innerText = "등록 완료"; // 버튼 글씨

    showPage('domainDetail');
}

// [4] ★ 보유 도메인 상세(수정) 화면 열기 ★
/* API 응답 형식 (GET /api/domain/detail?domain=...):
 * {
 *   "domain": "test.nulldns.top",
 *   "type": "A",
 *   "value": "192.168.0.100"
 * }
 */
async function openDomainDetail(domainName) {
    // 기존 alert 함수가 사라졌으므로 이 로직이 정상 실행됩니다.
    const res = await sendRequest(`/api/domain/detail?domain=${domainName}`, 'GET');

    if (res.success) {
        // 데이터를 화면에 채워넣음
        document.getElementById('domainTitle').innerText = domainName;
        document.getElementById('recordType').value = res.data.type;
        document.getElementById('recordValue').value = res.data.value;

        // 버튼 글씨를 '수정 저장'으로 변경
        document.getElementById('saveBtn').innerText = "수정 저장";
        
        updateInputFields(); // 필드에 맞는 플레이스홀더 업데이트
        showPage('domainDetail');
    } else {
        alert("상세 정보를 불러오지 못했습니다.");
    }
}



// [6] 로그인
async function login() {
    const email = document.getElementById('loginEmail').value.trim();
    const pw = document.getElementById('loginPw').value.trim();
    if (!email || !pw) return;

    const result = await sendRequest('/api/login', 'POST', { email, pw }, document.getElementById('loginBtn'), document.getElementById('loginMsg'));
    if (result.success) {
        await checkAuth();
        setTimeout(() => showPage('domainSearch'), 500);
    }
}

// [7] 로그아웃
async function logout() {
    await sendRequest('/api/logout', 'POST');
    currentUser = null;
    checkAuth();
    showPage('login');
}

// 기타 기능
function register() { alert("가입 기능 테스트"); }
function updateProfile() { alert("프로필 저장 테스트"); }

// 타입 선택 시 입력 필드 업데이트
function updateInputFields() {
    const type = document.getElementById('recordType').value;
    const input = document.getElementById('recordValue');
    input.value = ''; // 타입 변경 시 값 초기화

    switch(type) {
        case 'A':
            input.placeholder = '예: 192.168.1.1';
            input.disabled = false;
            break;
        case 'AAAA':
            input.placeholder = '예: 2001:0db8:85a3:0000:0000:8a2e:0370:7334';
            input.disabled = false;
            break;
        case 'CNAME':
            input.placeholder = '예: example.com';
            input.disabled = false;
            break;
        case 'TXT':
            input.placeholder = '예: verification=abcd1234';
            input.disabled = false;
            break;
    }
}

// 초기 로드 시 A 레코드 활성화
document.addEventListener('DOMContentLoaded', () => {
    updateInputFields();
});

// submitRegistration() 수정: 타입과 값 포함
/* API 요청 형식 (POST /api/domain/register):
 * {
 *   "domain": "test.nulldns.top",
 *   "type": "A",
 *   "value": "192.168.0.100"
 * }
 */
async function submitRegistration() {
    const domain = document.getElementById('domainTitle').innerText;
    const type = document.getElementById('recordType').value;
    const value = document.getElementById('recordValue').value.trim();

    if (!value) { alert(`${type} 값 입력해주세요.`); return; }

    const res = await sendRequest('/api/domain/register', 'POST', { domain, type, value, ttl: 60 });
    if (res.success) {
        alert(res.data.message);
        showPage('domainList'); // 저장 후 목록으로 이동
    } else {
        alert("실패했습니다.");
    }
}
