let recordMap = {};
let selectedDomain = '';
let selectedZone = '';

// 도메인 검색
async function searchDomain() {
  const subDomainInput = document.getElementById('searchInput');
  if (!subDomainInput) return;

  const subDomain = subDomainInput.value.trim();
  const resultDiv = document.getElementById('searchResult');

  if (!subDomain) {
    resultDiv.innerHTML = '<p>검색할 도메인 이름을 입력해주세요.</p>';
    return;
  }

  try {
    const response = await fetch(`/api/available-domains/${subDomain}`);
    const result = await response.json();

    if (!response.ok) {
      throw new Error(result.message);
    }

    let auth = false;
    const authResponse = await fetch('/api/me');
    if (authResponse.ok) {
      auth = true;
    }

    let html = '';
    for (const zone of result.zones) {
      const fullDomain = `${subDomain}.${zone.name}`;

      if (zone.canAdd) {
        if (auth) {
          html += `
            <div class="item status-ok" onclick="location.href='/domains/detail?subDomain=${subDomain}&zone=${zone.name}'">
              ${fullDomain} 사용 가능 합니다. [클릭하여 등록 가능]
            </div>
          `;
        } else {
          html += `
            <div class="item status-ok" onclick="location.href='/login'">
              ${fullDomain} 사용 가능 합니다. [로그인 후 등록 가능]
            </div>
          `;
        }
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
    resultDiv.innerHTML = '<p>도메인 검색 중 오류 발생</p>';
  }
}

async function openDomainDetail(subDomain, zone, isNew) {
  selectedDomain = subDomain;
  selectedZone = zone;

  if (isNew) {
    recordMap = {};
    updateInputFields();
    return;
  }

  const fullDomain = `${subDomain}.${zone}`;

  try {
    const response = await fetch(`/api/get-records/${fullDomain}`);
    if (response.status === 401) {
      alert("로그인이 필요합니다.");
      location.href = '/login';
      return;
    }

    if (response.status === 403) {
      alert("해당 도메인에 대한 접근 권한이 없습니다.");
      location.href = '/domains';
      return;
    }

    if (!response.ok) {
      recordMap = {};
      updateInputFields();
      return;
    }

    const recordList = await response.json();

    recordMap = {};
    for (const record of recordList) {
      recordMap[record.type] = record.content;
    }

    applyRecordToInput();
    updateInputFields();
  } catch (err) {
    alert("도메인 정보를 불러오는 중 오류가 발생했습니다.");
    location.href = '/domains';
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
  const recordTypeInput = document.getElementById('recordType');
  const recordValueInput = document.getElementById('recordValue');
  if (!recordTypeInput || !recordValueInput) return;

  const recordType = recordTypeInput.value;

  const placeholders = {
    'A': '예: 192.168.1.1',
    'AAAA': '예: 2001:db8::1',
    'CNAME': '예: nulldns.top',
    'TXT': '예: "v=spf1 include:_spf.google.com ~all"'
  };

  recordValueInput.placeholder = placeholders[recordType] || '값을 입력하세요.';
}

async function logout() {
  try {
    const result = await fetch('/logout', {
      method: 'POST',
      credentials: 'include'
    });

    if (!result.ok) {
      throw new Error();
    }

    window.location.href = '/';
  } catch (e) {
    alert('로그아웃 처리 중 오류가 발생했습니다.');
  }
}

function loginWithGitHub() {
  const agreeCheckbox = document.getElementById('agree');

  if (agreeCheckbox && !agreeCheckbox.checked) {
    alert("이용약관에 동의해야 로그인이 가능합니다.");
    return;
  }

  window.location.href = "/oauth2/authorization/github";
}

// 도메인 불러오기
async function loadUserDomains() {
  const listDiv = document.getElementById('domainList');
  if (!listDiv) return;

  try {
    const response = await fetch('/api/my-domains');

    const statusCode = response.status;
    if (statusCode === 401) {
      listDiv.innerHTML = '<p>로그인이 필요합니다. <a href="/login">로그인 페이지로 이동</a></p>';
      return;
    }
    if (statusCode === 404) {
      listDiv.innerHTML = '<p>보유한 도메인이 없습니다.</p>';
      return;
    }
    if (!response.ok) {
      listDiv.innerHTML = '<p>서버 오류가 발생했습니다. 나중에 다시 시도해주세요.</p>';
      return;
    }

    const domains = await response.json();

    if (domains.length > 0) {
      listDiv.innerHTML = domains.map(haveDomain =>
        `
        <div class="item" onclick="location.href='/domains/detail?subDomain=${haveDomain.subDomain}&zone=${haveDomain.zone}'">
            ${haveDomain.subDomain}.${haveDomain.zone}
        </div>
        <div class="${getExpirationClass(haveDomain.expirationDate)} status-clickable"
            onclick="renewDate('${haveDomain.subDomain}', '${haveDomain.zone}')">
            ${
              haveDomain.expirationDate
                ? `만료일: ${new Date(haveDomain.expirationDate).toLocaleDateString()}`
                : '만료일 정보 없음'
            }
        </div>
        `
      ).join('');
    } else {
      listDiv.innerHTML = '<p>보유한 도메인이 없습니다. 도메인을 검색하여 추가해보세요.</p>';
    }
  } catch (error) {
    listDiv.innerHTML = '<p>도메인 목록을 불러오는 중 오류가 발생했습니다.</p>';
  }
}

// 도메인 등록/수정 제출
async function submitRegistration() {
  const typeInput = document.getElementById('recordType');
  const contentInput = document.getElementById('recordValue');
  if (!typeInput || !contentInput) return;

  const type = typeInput.value;
  const content = contentInput.value.trim();

  if (!selectedZone || !selectedDomain || !type || !content) {
    alert('레코드 타입과 값을 모두 입력해주세요.');
    return;
  }

  try {
    const response = await fetch('/api/add-record', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ subDomain: selectedDomain, zone: selectedZone, type: type, content: content }),
    });

    const statusCode = response.status;
    switch (statusCode) {
      case 201:
        alert('도메인 정보가 성공적으로 업데이트되었습니다.');
        location.href = '/domains';
        break;
      case 400:
        alert('옳바르지 않은 내용을 입력하였습니다.');
        location.href = '/';
        break;
      case 401:
        alert("로그인이 필요합니다.");
        location.href = '/login';
        break;
      case 403:
        alert("해당 도메인에 대한 접근 권한이 없습니다.");
        location.href = '/domains';
        break;
      case 409:
        alert("최대 도메인 수 초과 혹은 이미 작업중인 도메인 입니다.");
        location.href = '/domains';
        break;
      default:
        alert('도메인 정보 업데이트가 실패하였습니다.');
        break;
    }
  } catch (error) {
    alert('도메인 등록/수정 과정 중 서버 통신과 오류가 발생했습니다.');
  }
}

// 도메인 삭제
async function deleteDomain() {
  if (!selectedZone || !selectedDomain) {
    alert('도메인이 선택되지 않은 오류가 발생했습니다. 도메인 선택부터 다시 진행해주세요.');
    return;
  }

  const result = confirm(`${selectedDomain}.${selectedZone} 도메인을 정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다. (선택시 바로 삭제됩니다)`);

  if (!result) {
    return;
  }

  try {
    const response = await fetch(`/api/delete-record/${selectedDomain}/${selectedZone}`, {
      method: 'DELETE',
    });
    const statusCode = response.status;

    if (statusCode === 401) {
      alert("로그인이 필요합니다.");
      location.href = '/login';
      return;
    }

    if (statusCode === 403) {
      alert("해당 도메인에 대한 접근 권한이 없습니다.");
      location.href = '/domains';
      return;
    }

    if (response.ok) {
      alert('도메인이 성공적으로 삭제되었습니다.');
      location.href = '/domains';
    } else {
      alert('알 수 없는 오류가 발생했습니다.');
    }

  } catch (error) {
    alert('도메인 삭제 과정 중 서버 통신과 오류가 발생했습니다.');
  }
}

// 회원탈퇴
async function leave() {
  const pass = prompt("정말로 회원탈퇴를 진행하시겠습니까? 탈퇴를 원하시면 '탈퇴' 를 입력해주세요.");
  if (pass !== "탈퇴") {
    alert("회원탈퇴가 취소되었습니다.");
    return;
  }

  try {
    const response = await fetch('/api/leave', {
      method: 'DELETE',
    });

    if (response.ok) {
      alert('회원탈퇴가 성공적으로 처리되었습니다.');
      window.location.href = '/';
    } else {
      alert('회원탈퇴 중 오류가 발생했습니다. 다시 시도해주세요.');
    }
  } catch (error) {
      alert('서버와의 통신 중 오류가 발생했습니다. 다시 시도해주세요.');
  }
}

// 만료일 갱신
async function renewDate(subDomain, zone) {
  try {
    const response = await fetch(`/api/update-record/${subDomain}/${zone}`, {
      method: 'PATCH',
    });

    const resultCode = response.status;

    if (resultCode === 200) {
      alert('도메인 만료일이 성공적으로 갱신되었습니다.');
      location.reload();
    } else if (resultCode === 401) {
      alert('로그인이 필요합니다.');
      location.href = '/login';
    } else if (resultCode === 403) {
      alert('해당 도메인에 대한 접근 권한이 없습니다.');
    } else if (resultCode === 400) {
      alert('만료일 갱신은 1달 전 부터 가능합니다.');
    } else {
      alert('도메인 만료일 갱신 중 오류가 발생했습니다. 다시 시도해주세요.');
    }
  } catch (error) {
    alert('서버와의 통신 중 오류가 발생했습니다.');
  }
}

// 만료일에 맞는 id 반환
function getExpirationClass(expirationDate) {
  if (!expirationDate) return '';

  const now = new Date();
  const expire = new Date(expirationDate);

  const diffMs = expire - now;
  const diffDays = diffMs / (1000 * 60 * 60 * 24);

  return diffDays < 30 ? 'status-no' : '';
}

// User Menu Dropdown Toggle
function toggleMenu(e, el) {
  const dropdown = el.nextElementSibling;
  document.querySelectorAll('.user-dropdown').forEach(d => {
    if (d !== dropdown) d.classList.remove('show');
  });
  dropdown.classList.toggle('show');
  if (e) {
    e.stopPropagation();
  }
}

// 바깥 클릭 시 닫기
document.addEventListener('click', () => {
  document.querySelectorAll('.user-dropdown').forEach(d => d.classList.remove('show'));
});
