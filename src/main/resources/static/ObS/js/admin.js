document.addEventListener('DOMContentLoaded', () => {
    loadStats();
    searchUsers(); // Initial load
});

// --- UI Utilities ---
function showLoading(show) {
    document.getElementById('loadingOverlay').classList.toggle('hidden', !show);
}

function openModal({ title, message, inputPlaceholder, onConfirm }) {
    const modal = document.getElementById('customModal');
    const input = document.getElementById('modalInput');
    document.getElementById('modalTitle').innerText = title || '확인';
    document.getElementById('modalMessage').innerText = message || '';
    
    if (inputPlaceholder) {
        input.classList.remove('hidden');
        input.placeholder = inputPlaceholder;
        input.value = '';
    } else {
        input.classList.add('hidden');
    }

    const confirmBtn = document.getElementById('modalConfirmBtn');
    const newConfirmBtn = confirmBtn.cloneNode(true);
    confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);

    newConfirmBtn.onclick = () => {
        const val = input.value;
        closeModal();
        if (onConfirm) onConfirm(val);
    };

    modal.classList.remove('hidden');
}

function closeModal() {
    document.getElementById('customModal').classList.add('hidden');
}

async function apiFetch(url, options = {}) {
    showLoading(true);
    try {
        const response = await fetch(url, options);
        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.message || '요청 처리에 실패했습니다.');
        }
        return response.headers.get('content-type')?.includes('application/json') ? await response.json() : null;
    } catch (e) {
        alert(e.message);
        throw e;
    } finally {
        showLoading(false);
    }
}

async function loadStats() {
    try {
        const data = await apiFetch('/admin/stats');
        document.getElementById('totalUsers').innerText = data.totalUsers;
        document.getElementById('bannedUsers').innerText = data.bannedUsers;
        document.getElementById('totalDomains').innerText = data.totalDomains;
        document.getElementById('totalZones').innerText = data.zones;
    } catch (e) {}
}

function showTab(tabName) {
    document.querySelectorAll('.tab-content').forEach(tab => tab.classList.add('hidden'));
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    
    document.getElementById(tabName + 'Tab').classList.remove('hidden');
    event.target.classList.add('active');

    if (tabName === 'zones') loadZones();
    if (tabName === 'domains') searchDomains();
}

// --- User Management ---
async function searchUsers() {
    const query = document.getElementById('userSearchInput').value;
    const list = document.getElementById('userList');
    list.innerHTML = '<p class="msg">로딩 중...</p>';

    try {
        const users = await apiFetch(`/admin/users?query=${encodeURIComponent(query)}`);
        list.innerHTML = '';
        if (users.length === 0) {
            list.innerHTML = '<p class="msg">검색 결과가 없습니다.</p>';
            return;
        }

        users.forEach(user => {
            const item = document.createElement('div');
            item.className = 'admin-item-wrapper';
            item.innerHTML = `
                <div class="admin-item" onclick="toggleUserDomains(${user.id}, this)">
                    <div class="admin-item-header">
                        <span class="admin-item-title">${user.provider}: ${user.providerId} (ID: ${user.id})</span>
                        <span class="badge ${user.banned ? 'badge-banned' : 'badge-active'}">${user.banned ? 'BANNED' : 'ACTIVE'}</span>
                    </div>
                    <div class="admin-item-actions" onclick="event.stopPropagation()">
                        <button class="btn-admin" onclick="toggleBan(${user.id}, ${!user.banned})">${user.banned ? '해제' : '정지'}</button>
                        <button class="btn-admin" onclick="changeMaxRecords(${user.id}, ${user.maxRecords})">한도(${user.maxRecords})</button>
                    </div>
                </div>
                <div id="userDomains-${user.id}" class="user-domains-container hidden"></div>
            `;
            list.appendChild(item);
        });
    } catch (e) {
        list.innerHTML = '<p class="msg">데이터를 가져오는데 실패했습니다.</p>';
    }
}

async function toggleUserDomains(memberId, element) {
    const container = document.getElementById(`userDomains-${memberId}`);
    if (!container.classList.contains('hidden')) {
        container.classList.add('hidden');
        return;
    }

    container.innerHTML = '<p class="msg">도메인 불러오는 중...</p>';
    container.classList.remove('hidden');

    try {
        const domains = await apiFetch(`/admin/users/${memberId}/domains`);
        if (domains.length === 0) {
            container.innerHTML = '<p class="msg">보유한 도메인이 없습니다.</p>';
            return;
        }

        container.innerHTML = '<h4 style="margin: 10px 0 5px 10px; font-size: 0.9rem;">보유 도메인 목록</h4>';
        domains.forEach(d => {
            const dItem = document.createElement('div');
            dItem.className = 'admin-sub-item';
            dItem.innerHTML = `
                <div class="admin-item-header">
                    <span class="admin-item-title" style="font-size: 0.85rem;">${d.fullDomain} (${d.recordType})</span>
                </div>
                <div style="font-size: 0.8rem; color: #666;">${d.content}</div>
                <div style="font-size: 0.75rem; color: #888; margin-top: 5px;">만료: ${d.expiryDate} | 상태: ${d.domainStatus}</div>
                <div class="admin-item-actions" style="margin-top: 5px;">
                    <button class="btn-admin btn-sm btn-cancel" onclick="deleteDomain('${d.fullDomain}', () => toggleUserDomains(${memberId}, null))">삭제</button>
                    <button class="btn-admin btn-sm" onclick="changeExpiryDate(${d.id}, '${d.expiryDate}', () => toggleUserDomains(${memberId}, null))">만료일</button>
                </div>
            `;
            container.appendChild(dItem);
        });
    } catch (e) {
        container.innerHTML = '<p class="msg">데이터 로드 실패</p>';
    }
}

async function toggleBan(memberId, banned) {
    openModal({
        title: banned ? '계정 정지' : '계정 해제',
        message: banned ? '해당 사용자를 정지하시겠습니까?' : '해당 사용자의 정지를 해제하시겠습니까?',
        onConfirm: async () => {
            try {
                await apiFetch(`/admin/users/${memberId}/ban?banned=${banned}`, { method: 'POST' });
                searchUsers();
                loadStats();
            } catch (e) {}
        }
    });
}

async function changeMaxRecords(memberId, current) {
    openModal({
        title: '한도 수정',
        message: '새로운 최대 레코드 한도를 입력하세요.',
        inputPlaceholder: current.toString(),
        onConfirm: async (newVal) => {
            if (!newVal) return;
            try {
                await apiFetch(`/admin/users/${memberId}/maxRecords?maxRecords=${newVal}`, { method: 'POST' });
                searchUsers();
            } catch (e) {}
        }
    });
}

// --- Domain Management ---
let currentDomainPage = 0;

async function searchDomains(page = 0) {
    currentDomainPage = page;
    const query = document.getElementById('domainSearchInput').value;
    const sortSelect = document.getElementById('domainSortSelect');
    const sortVal = sortSelect ? sortSelect.value.split(',') : ['expiryDate', 'asc'];
    const list = document.getElementById('domainList');
    const pagination = document.getElementById('domainPagination');
    
    list.innerHTML = '<p class="msg">로딩 중...</p>';
    if (pagination) pagination.innerHTML = '';

    try {
        const url = `/admin/domains?query=${encodeURIComponent(query)}&page=${page}&size=10&sort=${sortVal[0]}&direction=${sortVal[1].toUpperCase()}`;
        const data = await apiFetch(url);
        
        list.innerHTML = '';
        if (data.content.length === 0) {
            list.innerHTML = '<p class="msg">검색 결과가 없습니다.</p>';
            return;
        }

        data.content.forEach(d => {
            const item = document.createElement('div');
            item.className = 'admin-item';
            item.innerHTML = `
                <div class="admin-item-header">
                    <span class="admin-item-title">${d.fullDomain} (${d.recordType})</span>
                    <span class="badge badge-active">${d.domainStatus}</span>
                </div>
                <div style="font-size: 0.85rem; color: #666; margin-bottom: 0.2rem;">${d.content}</div>
                <div style="font-size: 0.75rem; color: #888; margin-bottom: 0.5rem;">소유자 ID: ${d.memberId} (${d.providerId}) | 만료: ${d.expiryDate}</div>
                <div class="admin-item-actions">
                    <button class="btn-admin btn-cancel" onclick="deleteDomain('${d.fullDomain}', () => searchDomains(currentDomainPage))">강제삭제</button>
                    <button class="btn-admin" onclick="transferDomain('${d.fullDomain}', () => searchDomains(currentDomainPage))">소유권이전</button>
                    <button class="btn-admin" onclick="changeExpiryDate(${d.id}, '${d.expiryDate}', () => searchDomains(currentDomainPage))">만료일</button>
                </div>
            `;
            list.appendChild(item);
        });

        if (pagination) renderPagination(data, pagination, searchDomains);
    } catch (e) {
        list.innerHTML = '<p class="msg">데이터를 가져오는데 실패했습니다.</p>';
    }
}

function renderPagination(pageData, container, callback) {
    if (pageData.totalPages <= 1) return;

    const nav = document.createElement('div');
    nav.className = 'paging-nav';

    if (!pageData.first) {
        const prev = document.createElement('button');
        prev.innerText = '이전';
        prev.onclick = () => callback(pageData.number - 1);
        nav.appendChild(prev);
    }

    const info = document.createElement('span');
    info.innerText = `${pageData.number + 1} / ${pageData.totalPages}`;
    nav.appendChild(info);

    if (!pageData.last) {
        const next = document.createElement('button');
        next.innerText = '다음';
        next.onclick = () => callback(pageData.number + 1);
        nav.appendChild(next);
    }

    container.appendChild(nav);
}

async function changeExpiryDate(domainId, current, onDone) {
    openModal({
        title: '만료일 수정',
        message: `현재 만료일: ${current}. 새로운 만료일을 입력하세요 (YYYY-MM-DD).`,
        inputPlaceholder: 'YYYY-MM-DD',
        onConfirm: async (newDate) => {
            if (!/^\d{4}-\d{2}-\d{2}$/.test(newDate)) {
                alert('날짜 형식이 올바르지 않습니다 (YYYY-MM-DD).');
                return;
            }
            try {
                await apiFetch(`/admin/domains/${domainId}/expiry?expiryDate=${newDate}`, { method: 'POST' });
                if (onDone) onDone();
                else {
                    searchDomains(currentDomainPage);
                    searchUsers();
                }
            } catch (e) {}
        }
    });
}

async function deleteDomain(fullDomain, onDone) {
    openModal({
        title: '도메인 삭제',
        message: `'${fullDomain}' 도메인을 즉시 삭제하시겠습니까?`,
        onConfirm: async () => {
            try {
                await apiFetch(`/admin/domains?fullDomain=${encodeURIComponent(fullDomain)}`, { method: 'DELETE' });
                if (onDone) onDone();
                else searchDomains(currentDomainPage);
            } catch (e) {}
        }
    });
}

async function transferDomain(fullDomain, onDone) {
    openModal({
        title: '소유권 이전',
        message: '이전할 새로운 Member ID를 입력하세요.',
        inputPlaceholder: 'Member ID',
        onConfirm: async (newId) => {
            if (!newId) return;
            try {
                await apiFetch(`/admin/domains/transfer?fullDomain=${encodeURIComponent(fullDomain)}&newMemberId=${newId}`, { method: 'POST' });
                if (onDone) onDone();
                else searchDomains(currentDomainPage);
            } catch (e) {}
        }
    });
}

// --- Zone Management ---
async function loadZones() {
    const list = document.getElementById('zoneList');
    list.innerHTML = '<p class="msg">로딩 중...</p>';
    try {
        const zones = await apiFetch('/admin/zones');
        list.innerHTML = '';
        zones.forEach(z => {
            const item = document.createElement('div');
            item.className = 'admin-item';
            item.innerHTML = `
                <div class="admin-item-header">
                    <span class="admin-item-title">${z.name}</span>
                </div>
                <div class="admin-item-actions">
                    <button class="btn-admin btn-cancel" onclick="deleteZone('${z.name}')">존 삭제</button>
                </div>
            `;
            list.appendChild(item);
        });
    } catch (e) {
        list.innerHTML = '<p class="msg">데이터를 가져오는데 실패했습니다.</p>';
    }
}

async function addZone() {
    const zone = document.getElementById('zoneAddInput').value;
    if (!zone) return;
    try {
        await apiFetch(`/admin/zones?zone=${encodeURIComponent(zone)}`, { method: 'POST' });
        document.getElementById('zoneAddInput').value = '';
        loadZones();
        loadStats();
    } catch (e) {}
}

async function deleteZone(zone) {
    openModal({
        title: '존 삭제',
        message: `'${zone}' 존을 삭제하면 소속된 모든 서브도메인이 삭제됩니다. 확인을 위해 'DELETE'를 입력하세요.`,
        inputPlaceholder: 'DELETE',
        onConfirm: async (code) => {
            if (code !== 'DELETE') return;
            try {
                await apiFetch(`/admin/deleteZone/${zone}/${code}`, { method: 'POST' });
                loadZones();
                loadStats();
            } catch (e) {}
        }
    });
}
