// ── BizSupport C++ — shared frontend helpers ──

const TOKEN_KEY = 'bizsupport_token';
const USER_KEY = 'bizsupport_user';

const Auth = {
  token() { return localStorage.getItem(TOKEN_KEY); },
  user() {
    try { return JSON.parse(localStorage.getItem(USER_KEY)); }
    catch { return null; }
  },
  set(token, user) {
    localStorage.setItem(TOKEN_KEY, token);
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
  isLoggedIn() { return !!this.token(); },
  logout() { this.clear(); location.href = '/'; }
};

// API wrapper — attaches the JWT and parses JSON.
async function api(path, options = {}) {
  const opts = { headers: {}, ...options };
  opts.headers = { 'Content-Type': 'application/json', ...opts.headers };
  const token = Auth.token();
  if (token) opts.headers['Authorization'] = 'Bearer ' + token;
  if (opts.body && typeof opts.body !== 'string') opts.body = JSON.stringify(opts.body);

  const res = await fetch('/api' + path, opts);
  if (res.status === 401) { Auth.clear(); location.href = '/'; throw new Error('Unauthorized'); }

  let data = null;
  const text = await res.text();
  if (text) { try { data = JSON.parse(text); } catch { data = text; } }
  if (!res.ok) {
    const msg = (data && data.message) ? data.message : ('Ошибка ' + res.status);
    throw new Error(msg);
  }
  return data;
}

// Redirect to login if not authenticated.
function requireAuth() {
  if (!Auth.isLoggedIn()) { location.href = '/'; return false; }
  return true;
}

const NAV = [
  { href: '/dashboard.html', icon: '\u{1F4CA}', label: 'Дашборд' },
  { href: '/search.html', icon: '\u{1F50D}', label: 'Поиск' },
  { href: '/tax.html', icon: '\u{1F4B0}', label: 'Налоги' },
  { href: '/calculator.html', icon: '\u{1F9EE}', label: 'Калькулятор' },
  { href: '/tenders.html', icon: '\u{1F4DC}', label: 'Тендеры' },
  { href: '/procurement.html', icon: '\u{1F4CB}', label: 'Госзакупки' },
  { href: '/assistant.html', icon: '\u{1F916}', label: 'Ассистент' },
  { href: '/notifications.html', icon: '\u{1F514}', label: 'Уведомления' },
  { href: '/favorites.html', icon: '⭐', label: 'Избранное' },
  { href: '/profile.html', icon: '\u{1F3E2}', label: 'Профиль' },
];

// Render the page shell (sidebar + main container). Returns the .main element.
function renderShell(activeHref) {
  const user = Auth.user() || {};
  const isAdmin = user.role === 'ADMIN';
  const navItems = NAV.map(n =>
    `<a href="${n.href}" class="${n.href === activeHref ? 'active' : ''}">
       <span class="ico">${n.icon}</span> ${n.label}
     </a>`).join('');
  const adminLink = isAdmin
    ? `<a href="/admin.html" class="${activeHref === '/admin.html' ? 'active' : ''}">
         <span class="ico">⚙️</span> Админ-панель</a>` : '';

  document.body.innerHTML = `
    <div class="app">
      <aside class="sidebar">
        <div class="brand">Biz<span>Support</span></div>
        <nav>
          ${navItems}
          ${adminLink}
        </nav>
        <div class="user-box">
          <div>${escapeHtml(user.fullName || user.email || 'Пользователь')}</div>
          <a href="#" id="logoutBtn">Выйти</a>
        </div>
      </aside>
      <main class="main" id="main"></main>
    </div>`;

  document.getElementById('logoutBtn').addEventListener('click', e => { e.preventDefault(); Auth.logout(); });
  return document.getElementById('main');
}

function escapeHtml(s) {
  if (s == null) return '';
  return String(s).replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function money(v) {
  if (v == null) return '—';
  return new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 }).format(v);
}

function fmtDate(s) {
  if (!s) return '—';
  const d = new Date(s.replace(' ', 'T'));
  if (isNaN(d)) return s;
  return d.toLocaleDateString('ru-RU');
}

function statusBadge(status) {
  const map = {
    PUBLISHED: ['green', 'Опубликован'],
    UNDER_REVIEW: ['yellow', 'На рассмотрении'],
    AUCTION: ['blue', 'Аукцион'],
    COMPLETED: ['gray', 'Завершён'],
    CANCELLED: ['red', 'Отменён'],
  };
  const [cls, label] = map[status] || ['gray', status];
  return `<span class="badge ${cls}">${label}</span>`;
}

function lawBadge(law) {
  const map = { FZ_44: 'Закон 44-ФЗ', FZ_223: 'Закон 223-ФЗ', COMMERCIAL: 'Коммерческий' };
  return `<span class="badge blue">${map[law] || law}</span>`;
}
