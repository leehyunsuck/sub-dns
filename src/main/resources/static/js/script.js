// 페이지 조각 변경
async function loadPage(page) {
  try {
    const response = await fetch(`/pages/${page}.html`);
    if (!response.ok) {
      document.getElementById('content').innerHTML = `<p>오류: 페이지를 찾을 수 없습니다.</p>`;
      return;
    }
    const html = await response.text();
    document.getElementById('content').innerHTML = html;

    // 콘텐츠 로드 후 페이지별 로직 실행
    if (page === 'domainList') {
      loadUserDomains();
    }
    // 'profile' 페이지 관련 로직 제거됨
  } catch (error) {
    console.error(`페이지 로드 중 오류 발생: ${page}:`, error);
    document.getElementById('content').innerHTML = `<p>페이지 로드 중 오류 발생. 자세한 내용은 콘솔을 참조하세요.</p>`;
  }
}

// 도메인 검색
async function searchDomain() {
  const query = document.getElementById('searchInput').value.trim();
  const resultDiv = document.getElementById('searchResult');

  if (!query) {
    resultDiv.innerHTML = '<p>검색할 도메인 이름을 입력해주세요.</p>';
    return;
  }

  try {
    const response = await fetch(`/api/available-domains/${query}`);
    const result = await response.json();
    /*
      result 형태:
      {
        "subDomain": "example",
        "zoneNames": [
            {"name": "nulldns.top", "canAdd": true/false},
            {"name": "anotherdomain.com", "canAdd": true/false}
        ]
      }
    */
    console.log('available-domains response:', result);

    let auth = false;
    const authResponse = await fetch('/api/me');
    if (authResponse.ok) {
        auth = true;
    }

    let html = '';
    for (const zone of result.zones) {

      const fullDomain = `${query}.${zone.name}`;

      if (zone.canAdd) {
        html += `
          <div class="item status-ok" ${auth ? `onclick="openDomainDetail('${fullDomain}', true)"` : ''}>
            ${fullDomain} 사용 가능 합니다. ${auth ? '[클릭하여 등록 가능]' : '[로그인 후 등록 가능]'}
          </div>
        `;
      } else {
        html += `
          <div class="item status-no">
            ${fullDomain} 는 이미 등록되었거나 제한된 도메인 입니다.
          </div>
        `;
      }
    }

    resultDiv.innerHTML = html;

  } catch (error) {
    console.error('도메인 검색 중 오류 발생:', error);
    resultDiv.innerHTML = '<p>도메인 검색 중 오류 발생</p>';
  }
}

let recordMap = {};
async function openDomainDetail(fullDomain, isNew) {
  await loadPage('domainDetail');
  document.getElementById('domainTitle').innerText = fullDomain;

  if (isNew) {
    recordMap = {};
    updateInputFields();
    return;
  }

  try {
    const response = await fetch(`/api/get-records/${fullDomain}`);
    if (response.status == 401) {
        alert("로그인이 필요합니다.");
        loadPage('auth');
        return;
    }

    if (response.status == 403) {
        alert("해당 도메인에 대한 접근 권한이 없습니다.");
        loadPage('domainList');
        return;
    }

    if (!response.ok) {
      console.warn("기존 레코드 없음. 새로 생성 가능.");
      recordMap = {};
      updateInputFields();
      return;
    }

    const recordList = await response.json();
    console.log("기존 레코드 불러오기:", recordList);
    /*
      recordList 형태:
      [
        { type: "A", content: "1.1.1.1" },
        { type: "TXT", content: "something..." }
      ]
    */

    recordMap = {};
    for (const record of recordList) {
      recordMap[record.type] = record.content;
    }

    applyRecordToInput();
    updateInputFields();
  } catch (err) {
    console.error("레코드 불러오기 오류:", err);
  }
}

// 레코드 타입 변경 시
function onRecordTypeChange() {
  applyRecordToInput();
  updateInputFields();
}

// 현재 선택된 타입의 기존 값 적용
function applyRecordToInput() {
  const recordType = document.getElementById('recordType').value;
  const recordValueInput = document.getElementById('recordValue');

  if (recordMap[recordType]) {
    recordValueInput.value = recordMap[recordType];
  } else {
    recordValueInput.value = "";
  }
}

// placeholder 처리
function updateInputFields() {
  const recordType = document.getElementById('recordType').value;
  const recordValueInput = document.getElementById('recordValue');

  const placeholders = {
    'A': '예: 192.168.1.1',
    'AAAA': '예: 2001:db8::1',
    'CNAME': '예: example.com',
    'TXT': '예: "v=spf1 include:_spf.google.com ~all"'
  };

  recordValueInput.placeholder = placeholders[recordType] || '값을 입력하세요.';
}

// 로그인 상태 확인
async function checkAuth() {
  const authSection = document.getElementById('authSection');
  try {
    const response = await fetch('/api/me', { credentials: 'include' });
    if (response.ok) {
      const idDto = await response.json();
      authSection.innerHTML = `
        <span>${ idDto.id || '사용자'}님</span>
        <a href="#" onclick="logout()">로그아웃</a>
      `;
    } else {
      authSection.innerHTML = `<a href="#" onclick="loadPage('auth')">로그인</a>`;
    }
  } catch (error) {
    console.error('인증 상태 확인 중 오류 발생:', error);
    authSection.innerHTML = `<a href="#" onclick="loadPage('auth')">로그인</a>`;
  }
}

function logout() {
  fetch('/logout', { method: 'POST' })
      .then(() => {
        window.location.reload();
      })
      .catch(error => console.error('로그아웃 실패:', error));
}

function loginWithGitHub() {
  window.location.href = "/oauth2/authorization/github";
}

document.addEventListener('DOMContentLoaded', () => {
  checkAuth();
  loadPage('domainSearch');
});

// =================== 하단은 AI 작성 (나에 맞게 수정 전) ===============================

// 도메인 제거도 필요


/**
 * 사용자가 소유한 도메인 목록을 가져와 표시합니다.
 */
async function loadUserDomains() {
  const listDiv = document.getElementById('domainList');
  if (!listDiv) return;

  try {
    // 사용자 도메인을 가져오는 API 엔드포인트
    const response = await fetch('/api/pdns/list');
    if (!response.ok) throw new Error('도메인 목록 로드에 실패했습니다.');
    
    const domains = await response.json();
    if (domains.length > 0) {
      listDiv.innerHTML = domains.map(d => 
        `<div class="item" onclick="openDomainDetail('${d.subDomain}')">${d.subDomain}.nulldns.top</div>`
      ).join('');
    } else {
      listDiv.innerHTML = '<p>보유한 도메인이 없습니다. 도메인을 검색하여 추가해보세요.</p>';
    }
  } catch (error) {
    console.error('사용자 도메인 로드 중 오류 발생:', error);
    listDiv.innerHTML = '<p>도메인 목록을 불러오는 중 오류가 발생했습니다.</p>';
  }
}

/**
 * 새 도메인 또는 업데이트된 도메인 레코드를 백엔드에 제출합니다.
 */
async function submitRegistration() {
  const type = document.getElementById('recordType').value;
  const content = document.getElementById('recordValue').value.trim();

  if (!currentDomain || !type || !content) {
    alert('레코드 타입과 값을 모두 입력해주세요.');
    return;
  }
  
  try {
    // 레코드를 생성 또는 업데이트하는 API 엔드포인트
    const response = await fetch('/api/pdns', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ subDomain: currentDomain, type, content }),
    });

    if (response.ok) {
      alert('도메인 정보가 성공적으로 업데이트되었습니다.');
      loadPage('domainList');
    } else {
      const errorData = await response.json();
      alert(`오류: ${errorData.message || '알 수 없는 오류가 발생했습니다.'}`);
    }
  } catch (error) {
    console.error('도메인 등록 제출 중 오류 발생:', error);
    alert('도메인 등록/수정 중 오류가 발생했습니다.');
  }
}


