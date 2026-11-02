const API_BASE = '/api';

let state = {
  reports: [],
  categories: [],
  notifications: [],
  users: [],
  stats: {},
  selectedReportId: null
};

// DOM Elements
const tabButtons = document.querySelectorAll('.nav-item');
const tabSections = document.querySelectorAll('.tab-content');
const reportSearch = document.getElementById('report-search');
const statusFilter = document.getElementById('status-filter');
const categoryFilter = document.getElementById('category-filter');
const reportsTableBody = document.getElementById('reports-table-body');
const newCategorySelect = document.getElementById('new-category');
const createReportForm = document.getElementById('create-report-form');
const notificationsList = document.getElementById('notifications-list');
const usersTableBody = document.getElementById('users-table-body');
const refreshBtn = document.getElementById('refresh-btn');
const resetDbBtn = document.getElementById('reset-db-btn');
const markAllReadBtn = document.getElementById('mark-all-read-btn');

// Modal Elements
const statusModal = document.getElementById('status-modal');
const modalReportTitle = document.getElementById('modal-report-title');
const modalStatusSelect = document.getElementById('modal-status-select');
const modalStatusNote = document.getElementById('modal-status-note');
const modalCancelBtn = document.getElementById('modal-cancel-btn');
const modalSaveBtn = document.getElementById('modal-save-btn');

// Init
document.addEventListener('DOMContentLoaded', () => {
  setupNavigation();
  setupEventListeners();
  loadAllData();
});

function setupNavigation() {
  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const tabId = btn.getAttribute('data-tab');
      tabButtons.forEach(b => b.classList.remove('active'));
      tabSections.forEach(s => s.classList.remove('active'));
      btn.classList.add('active');
      const target = document.getElementById('tab-' + tabId);
      if (target) target.classList.add('active');

      const titles = {
        'reports': 'Civic Reports Management',
        'create-report': 'Create Civic Report',
        'notifications': 'Live Notifications',
        'users': 'Registered Citizens & Users',
        'api-docs': 'REST API Reference'
      };
      document.getElementById('page-title').textContent = titles[tabId] || 'Dashboard';
    });
  });
}

function setupEventListeners() {
  refreshBtn.addEventListener('click', () => loadAllData());
  resetDbBtn.addEventListener('click', async () => {
    if (confirm('Reset database back to initial seed data?')) {
      await fetch(`${API_BASE}/reset`, { method: 'POST' });
      await loadAllData();
    }
  });

  reportSearch.addEventListener('input', renderReportsTable);
  statusFilter.addEventListener('change', renderReportsTable);
  categoryFilter.addEventListener('change', renderReportsTable);

  createReportForm.addEventListener('submit', handleCreateReport);
  markAllReadBtn.addEventListener('click', handleMarkAllNotificationsRead);

  // Modal
  modalCancelBtn.addEventListener('click', () => statusModal.classList.remove('open'));
  modalSaveBtn.addEventListener('click', handleSaveStatus);
}

async function loadAllData() {
  try {
    const [reportsRes, catRes, notifRes, statsRes, meRes] = await Promise.all([
      fetch(`${API_BASE}/reports`),
      fetch(`${API_BASE}/categories`),
      fetch(`${API_BASE}/notifications`),
      fetch(`${API_BASE}/stats`),
      fetch(`${API_BASE}/auth/me`)
    ]);

    state.reports = await reportsRes.json();
    state.categories = await catRes.json();
    state.notifications = await notifRes.json();
    state.stats = await statsRes.json();
    state.users = [await meRes.json().then(d => d.user).catch(() => ({}))];

    renderStats();
    populateCategoryFilters();
    renderReportsTable();
    renderNotifications();
    renderUsers();
  } catch (err) {
    console.error('Error fetching data:', err);
  }
}

function renderStats() {
  document.getElementById('stat-total').textContent = state.stats.reportsSubmitted || state.reports.length;
  document.getElementById('stat-in-progress').textContent = state.stats.inProgress || 0;
  document.getElementById('stat-resolved').textContent = state.stats.resolved || 0;
  document.getElementById('stat-users').textContent = state.stats.totalUsers || 1;
}

function populateCategoryFilters() {
  categoryFilter.innerHTML = '<option value="All">All Categories</option>';
  newCategorySelect.innerHTML = '';
  state.categories.forEach(cat => {
    const opt1 = document.createElement('option');
    opt1.value = cat.name;
    opt1.textContent = cat.name;
    categoryFilter.appendChild(opt1);

    const opt2 = document.createElement('option');
    opt2.value = cat.name;
    opt2.textContent = `${cat.name} (${cat.description})`;
    newCategorySelect.appendChild(opt2);
  });
}

function renderReportsTable() {
  const search = (reportSearch.value || '').toLowerCase();
  const status = statusFilter.value;
  const category = categoryFilter.value;

  const filtered = state.reports.filter(r => {
    const matchesSearch = !search ||
      (r.title && r.title.toLowerCase().includes(search)) ||
      (r.category && r.category.toLowerCase().includes(search)) ||
      (r.location && r.location.toLowerCase().includes(search)) ||
      (r.description && r.description.toLowerCase().includes(search));
    const matchesStatus = status === 'All' || r.status === status;
    const matchesCat = category === 'All' || r.category === category;
    return matchesSearch && matchesStatus && matchesCat;
  });

  if (filtered.length === 0) {
    reportsTableBody.innerHTML = `<tr><td colspan="7" class="text-center text-muted" style="padding: 32px;">No matching reports found</td></tr>`;
    return;
  }

  reportsTableBody.innerHTML = filtered.map(r => `
    <tr>
      <td><strong>#${r.id.toUpperCase()}</strong></td>
      <td><span style="font-weight:600;">${r.category}</span></td>
      <td>
        <strong>${escapeHtml(r.title)}</strong>
        <div class="text-muted" style="font-size:12px; max-width: 320px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
          ${escapeHtml(r.description)}
        </div>
      </td>
      <td>📍 ${escapeHtml(r.location)}</td>
      <td>${r.date}</td>
      <td><span class="status-badge ${r.status}">${r.status.replace('_', ' ')}</span></td>
      <td>
        <div style="display:flex; gap:6px;">
          <button class="btn btn-outline btn-sm" onclick="openStatusModal('${r.id}')">Update Status</button>
          <button class="btn btn-outline btn-sm" style="color:var(--danger);" onclick="deleteReport('${r.id}')">✕</button>
        </div>
      </td>
    </tr>
  `).join('');
}

function renderNotifications() {
  if (state.notifications.length === 0) {
    notificationsList.innerHTML = `<div class="text-center text-muted" style="padding: 32px;">No notifications yet.</div>`;
    return;
  }

  notificationsList.innerHTML = state.notifications.map(n => `
    <div class="notif-card ${!n.isRead ? 'unread' : ''}">
      <div>
        <div class="notif-title">${escapeHtml(n.title)}</div>
        <div class="notif-msg">${escapeHtml(n.message)}</div>
        <div class="notif-time">${n.time || 'Just now'}</div>
      </div>
      <div>
        ${!n.isRead ? `<button class="btn btn-outline btn-sm" onclick="markNotificationRead('${n.id}')">Mark Read</button>` : `<span class="text-muted" style="font-size:12px;">Read</span>`}
      </div>
    </div>
  `).join('');
}

function renderUsers() {
  usersTableBody.innerHTML = state.users.map(u => `
    <tr>
      <td>${u.id || 'user_1'}</td>
      <td><strong>${escapeHtml(u.name || 'Citizen')}</strong></td>
      <td>${escapeHtml(u.email || 'citizen@fixmycity.org')}</td>
      <td>${escapeHtml(u.phone || '+91 98765 43210')}</td>
      <td><span class="status-badge SUBMITTED">${u.role || 'Citizen'}</span></td>
      <td>${u.reportsSubmitted || 0}</td>
      <td>${u.reportsResolved || 0}</td>
    </tr>
  `).join('');
}

window.openStatusModal = function(reportId) {
  const report = state.reports.find(r => r.id === reportId);
  if (!report) return;
  state.selectedReportId = reportId;
  modalReportTitle.textContent = `${report.title} (#${report.id.toUpperCase()})`;
  modalStatusSelect.value = report.status;
  modalStatusNote.value = '';
  statusModal.classList.add('open');
};

async function handleSaveStatus() {
  if (!state.selectedReportId) return;
  const newStatus = modalStatusSelect.value;
  const note = modalStatusNote.value;

  try {
    const res = await fetch(`${API_BASE}/reports/${state.selectedReportId}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: newStatus, note: note })
    });
    if (res.ok) {
      statusModal.classList.remove('open');
      await loadAllData();
    }
  } catch (err) {
    alert('Error updating status: ' + err.message);
  }
}

window.deleteReport = async function(reportId) {
  if (!confirm('Are you sure you want to delete report #' + reportId + '?')) return;
  try {
    await fetch(`${API_BASE}/reports/${reportId}`, { method: 'DELETE' });
    await loadAllData();
  } catch (err) {
    alert('Error deleting report: ' + err.message);
  }
};

window.markNotificationRead = async function(notifId) {
  await fetch(`${API_BASE}/notifications/${notifId}/read`, { method: 'PATCH' });
  await loadAllData();
};

async function handleMarkAllNotificationsRead() {
  await fetch(`${API_BASE}/notifications/read-all`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId: 'user_1' })
  });
  await loadAllData();
}

async function handleCreateReport(e) {
  e.preventDefault();
  const category = document.getElementById('new-category').value;
  const location = document.getElementById('new-location').value;
  const title = document.getElementById('new-title').value;
  const description = document.getElementById('new-description').value;
  const imageFile = document.getElementById('new-image').files[0];

  let imageUrl = null;
  if (imageFile) {
    const formData = new FormData();
    formData.append('image', imageFile);
    try {
      const upRes = await fetch(`${API_BASE}/upload`, { method: 'POST', body: formData });
      const upData = await upRes.json();
      if (upData.url) imageUrl = upData.url;
    } catch (err) {
      console.warn('Image upload failed, submitting without image:', err);
    }
  }

  try {
    const res = await fetch(`${API_BASE}/reports`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ category, location, title, description, imageUrl, userId: 'user_1' })
    });
    if (res.ok) {
      createReportForm.reset();
      document.querySelector('[data-tab="reports"]').click();
      await loadAllData();
    }
  } catch (err) {
    alert('Error creating report: ' + err.message);
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
