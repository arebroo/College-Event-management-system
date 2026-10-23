/* =====================================================================
   EventHub frontend - plain JavaScript (no framework).
   Parts:  1) helpers   2) login/sign-up   3) navigation   4) views (one per menu item)
           5) click actions   6) start-up
   The browser NEVER decides permissions: it only hides buttons.
   The Java backend checks every request again.
   ===================================================================== */

const $ = (id) => document.getElementById(id);
let token = sessionStorage.getItem('token');
let me = null;                 // logged-in user (comes from /api/me)
let currentView = 'dashboard';
let reload = () => {};         // re-draws the current view after an action

// ------------------------------------------------------------ 1) helpers
async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  const res = await fetch('/api' + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  let data = {};
  try { data = await res.json(); } catch (e) { /* empty body */ }
  if (!res.ok) {
    if (res.status === 401 && token) { localLogout(); }
    throw new Error(data.error || 'Request failed (' + res.status + ')');
  }
  return data;
}

function esc(v) {   // stops HTML injection (XSS) when we print user-typed text
  return String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

let toastTimer;
function toast(msg, kind) {
  const t = $('toast');
  t.textContent = msg;
  t.className = 'toast ' + (kind || '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.add('hidden'), 3500);
}

function fmtDate(d) {
  if (!d) return '';
  const dt = new Date(d + 'T00:00:00');
  return dt.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
}

function badge(text) { return `<span class="badge ${esc(text)}">${esc(text)}</span>`; }
function options(list, selected) {
  return list.map((o) => {
    const [v, l] = Array.isArray(o) ? o : [o, o];
    return `<option value="${esc(v)}" ${String(v) === String(selected) ? 'selected' : ''}>${esc(l)}</option>`;
  }).join('');
}
function field(label, name, value, attrs) {
  return `<label>${label}<input name="${name}" value="${esc(value)}" ${attrs || ''}></label>`;
}
function selectField(label, name, list, selected) {
  return `<label>${label}<select name="${name}">${options(list, selected)}</select></label>`;
}

const CATEGORIES = ['Technical', 'Cultural', 'Sports', 'Workshop', 'Seminar', 'Social'];
const ROLES = ['STUDENT', 'VOLUNTEER', 'COORDINATOR', 'ADMIN'];

// ---- modal: shows a form (or any HTML). onSubmit receives the form values as an object.
function openModal(title, html, onSubmit, opts) {
  opts = opts || {};
  const box = $('modalBox');
  box.className = 'modal-box' + (opts.wide ? ' wide' : '');
  box.innerHTML = `<h3>${esc(title)}</h3><form id="modalForm">${html}
    <div class="modal-actions"><button type="button" class="btn" data-action="closeModal">${onSubmit ? 'Cancel' : 'Close'}</button>
    ${onSubmit ? `<button class="btn primary" type="submit">${esc(opts.button || 'Save')}</button>` : ''}</div></form>`;
  $('modal').classList.remove('hidden');
  $('modalForm').onsubmit = async (e) => {
    e.preventDefault();
    if (!onSubmit) return closeModal();
    try {
      const values = Object.fromEntries(new FormData(e.target).entries());
      await onSubmit(values);
      closeModal();
    } catch (err) { toast(err.message, 'error'); }
  };
}
function closeModal() { $('modal').classList.add('hidden'); $('modalBox').innerHTML = ''; }

// ------------------------------------------------------------ 2) login / sign-up
function showAuthMessage(text, ok) {
  const m = $('authMsg');
  m.textContent = text;
  m.className = 'msg' + (ok ? ' ok' : '');
}

function setupAuthScreen() {
  $('tabLogin').onclick = () => switchTab(true);
  $('tabSignup').onclick = () => switchTab(false);
  function switchTab(login) {
    $('loginForm').classList.toggle('hidden', !login);
    $('signupForm').classList.toggle('hidden', login);
    $('tabLogin').classList.toggle('active', login);
    $('tabSignup').classList.toggle('active', !login);
    $('authMsg').classList.add('hidden');
  }
  document.querySelectorAll('.chip').forEach((c) => {
    c.onclick = () => {
      const f = $('loginForm');
      f.elements.username.value = c.dataset.user;
      f.elements.password.value = c.dataset.pass;
    };
  });
  $('loginForm').onsubmit = async (e) => {
    e.preventDefault();
    try {
      const f = e.target;
      const res = await api('POST', '/login', { username: f.elements.username.value, password: f.elements.password.value });
      token = res.token;
      sessionStorage.setItem('token', token);
      me = res.user;
      startApp();
    } catch (err) { showAuthMessage(err.message); }
  };
  $('signupForm').onsubmit = async (e) => {
    e.preventDefault();
    try {
      const values = Object.fromEntries(new FormData(e.target).entries());
      await api('POST', '/signup', values);
      e.target.reset();
      switchTab(true);
      showAuthMessage('Account created! You can log in now.', true);
    } catch (err) { showAuthMessage(err.message); }
  };
}

function localLogout() {
  token = null; me = null;
  sessionStorage.removeItem('token');
  $('appScreen').classList.add('hidden');
  $('authScreen').classList.remove('hidden');
}

// ------------------------------------------------------------ 3) navigation
const MENU_LABELS = {
  dashboard: '📊 Dashboard', events: '📅 Browse Events', myRegistrations: '🎟️ My Registrations',
  participation: '🏅 My Participation', myDuties: '🧑‍🤝‍🧑 My Duties', manageEvents: '🛠️ Manage Events',
  duties: '📋 Volunteer Duties', users: '👥 Manage Users'
};

function startApp() {
  $('authScreen').classList.add('hidden');
  $('appScreen').classList.remove('hidden');
  $('userName').textContent = me.name;
  $('userRole').textContent = me.role;
  $('dashTitle').textContent = me.dashboardTitle;
  // The menu itself comes from the Java backend (each role class returns its own menu).
  $('nav').innerHTML = me.menu.map((m) => `<button data-action="go" data-id="${m}" id="nav-${m}">${MENU_LABELS[m] || m}</button>`).join('');
  go('dashboard');
}

function go(view) {
  currentView = view;
  document.querySelectorAll('#nav button').forEach((b) => b.classList.toggle('active', b.dataset.id === view));
  $('pageTitle').textContent = (MENU_LABELS[view] || view).replace(/^\S+\s/, '');
  $('sidebar').classList.remove('open');
  $('content').innerHTML = '<p class="muted">Loading...</p>';
  const draw = VIEWS[view];
  reload = () => draw().catch((err) => { if (token) $('content').innerHTML = `<div class="empty">⚠ ${esc(err.message)}</div>`; });
  reload();
}

// ------------------------------------------------------------ 4) views
function eventCard(ev) {
  const seatsLeft = ev.capacity - ev.registeredCount;
  const canJoin = me.permissions.includes('REGISTER_EVENT');
  let btn = '';
  if (canJoin) {
    if (ev.myStatus === 'REGISTERED') btn = `<button class="btn danger sm" data-action="cancelReg" data-id="${ev.id}">Cancel registration</button>`;
    else if (ev.status !== 'UPCOMING') btn = `<button class="btn sm" disabled>Registration closed</button>`;
    else if (seatsLeft <= 0) btn = `<button class="btn sm" disabled>Event full</button>`;
    else btn = `<button class="btn primary sm" data-action="register" data-id="${ev.id}">Register</button>`;
  }
  return `<div class="event">
    <div>${badge(ev.status)} ${ev.myStatus === 'REGISTERED' ? badge('REGISTERED') : ''} <span class="badge">${esc(ev.category)}</span></div>
    <h3>${esc(ev.title)}</h3>
    <div class="small">📅 ${fmtDate(ev.date)} &nbsp; 🕒 ${esc(ev.startTime)} - ${esc(ev.endTime)}</div>
    <div class="small">📍 ${esc(ev.venue)}</div>
    <div class="small muted">${esc(ev.description)}</div>
    <div class="small">👤 ${esc(ev.coordinatorName)} &nbsp; 🎟️ ${ev.registeredCount}/${ev.capacity} seats &nbsp; 🙋 ${ev.volunteerCount} volunteers</div>
    <div class="actions">${btn}</div></div>`;
}

function bars(obj) {
  const max = Math.max(1, ...Object.values(obj));
  return Object.entries(obj).map(([k, v]) =>
    `<div class="bar"><span class="name">${esc(k)}</span><span class="fill" style="width:${(v / max) * 60}%"></span><b>${v}</b></div>`).join('');
}

const VIEWS = {
  async dashboard() {
    const [st, events] = await Promise.all([api('GET', '/stats'), api('GET', '/events?status=UPCOMING')]);
    let html = `<div class="cards">` + Object.entries(st.cards).map(([k, v]) =>
      `<div class="stat"><div class="num">${esc(v)}</div><div class="lbl">${esc(k)}</div></div>`).join('') + `</div>`;
    if (st.usersByRole) {
      html += `<div class="row"><div class="panel"><h3>Users by role</h3>${bars(st.usersByRole)}</div>
               <div class="panel"><h3>Events by category</h3>${bars(st.eventsByCategory)}</div></div>`;
    }
    html += `<h3>Next upcoming events</h3><div class="grid">` +
      (events.slice(0, 3).map(eventCard).join('') || '<div class="empty">No upcoming events.</div>') + `</div>`;
    $('content').innerHTML = html;
  },

  async events() {
    $('content').innerHTML = `<div class="toolbar">
      <input id="fSearch" class="grow" placeholder="🔍 Search title, venue, description...">
      <select id="fCat"><option value="">All categories</option>${options(CATEGORIES)}</select>
      <select id="fStatus"><option value="">All statuses</option>${options(['UPCOMING', 'COMPLETED', 'CANCELLED'])}</select></div>
      <div id="eventGrid" class="grid"></div>`;
    const load = async () => {
      const q = new URLSearchParams({ search: $('fSearch').value, category: $('fCat').value, status: $('fStatus').value });
      const list = await api('GET', '/events?' + q);
      $('eventGrid').innerHTML = list.map(eventCard).join('') || '<div class="empty">No events match your filters.</div>';
    };
    let timer;
    $('fSearch').oninput = () => { clearTimeout(timer); timer = setTimeout(() => load().catch(e => toast(e.message, 'error')), 250); };
    $('fCat').onchange = $('fStatus').onchange = () => load().catch(e => toast(e.message, 'error'));
    reload = () => load().catch(e => toast(e.message, 'error'));
    await load();
  },

  async myRegistrations() {
    const list = await api('GET', '/my/registrations');
    $('content').innerHTML = `<div class="panel tablewrap"><table><tr><th>Event</th><th>Date</th><th>Time</th><th>Venue</th><th>Status</th><th>Attended</th><th></th></tr>` +
      (list.map((r) => `<tr><td><b>${esc(r.event.title)}</b></td><td>${fmtDate(r.event.date)}</td>
        <td>${esc(r.event.startTime)}-${esc(r.event.endTime)}</td><td>${esc(r.event.venue)}</td><td>${badge(r.status)}</td>
        <td>${r.status === 'REGISTERED' ? (r.attended ? badge('yes') : '—') : ''}</td>
        <td>${r.status === 'REGISTERED' && !r.attended && r.event.status === 'UPCOMING'
          ? `<button class="btn danger sm" data-action="cancelReg" data-id="${r.event.id}">Cancel</button>` : ''}</td></tr>`).join('')
        || `<tr><td colspan="7" class="empty">You have not registered for any event yet. Go to Browse Events.</td></tr>`) + `</table></div>`;
  },

  async participation() {
    const p = await api('GET', '/my/participation');
    let html = `<div class="cards">
      <div class="stat"><div class="num">${p.registered}</div><div class="lbl">Events registered</div></div>
      <div class="stat"><div class="num">${p.attended}</div><div class="lbl">Events attended</div></div>
      <div class="stat"><div class="num">${p.rate}%</div><div class="lbl">Attendance rate</div></div></div>
      <h3>Events I attended</h3><div class="grid">${p.attendedEvents.map(eventCard).join('') || '<div class="empty">No attended events yet.</div>'}</div>`;
    if (me.role === 'VOLUNTEER') {
      html += `<h3 style="margin-top:22px">Duties I completed (attendance marked)</h3><div class="panel tablewrap"><table><tr><th>Event</th><th>Task</th><th>Date</th></tr>` +
        (p.dutiesCompleted.map((d) => `<tr><td>${esc(d.eventTitle)}</td><td>${esc(d.task)}</td><td>${fmtDate(d.date)}</td></tr>`).join('')
          || '<tr><td colspan="3" class="empty">No completed duties yet.</td></tr>') + `</table></div>`;
    }
    $('content').innerHTML = html;
  },

  async myDuties() {
    const list = await api('GET', '/duties');
    $('content').innerHTML = `<div class="grid">` + (list.map((d) => `<div class="event">
      <div>${badge(d.status)} ${d.attended ? badge('yes') : ''}</div><h3>${esc(d.task)}</h3>
      <div class="small">Event: <b>${esc(d.eventTitle)}</b></div>
      <div class="small">📅 ${fmtDate(d.date)} &nbsp; 🕒 ${esc(d.time)}</div><div class="small">📍 ${esc(d.venue)}</div>
      <div class="actions">
        ${d.status === 'ASSIGNED' ? `<button class="btn success sm" data-action="dutyStatus" data-id="${d.id}" data-val="ACCEPTED">Accept</button>
           <button class="btn danger sm" data-action="dutyStatus" data-id="${d.id}" data-val="DECLINED">Decline</button>` : ''}
        ${d.status === 'ACCEPTED' ? `<button class="btn primary sm" data-action="dutyStatus" data-id="${d.id}" data-val="DONE">Mark as done</button>` : ''}
      </div></div>`).join('') || '<div class="empty">No duties assigned to you yet.</div>') + `</div>`;
  },

  async manageEvents() {
    $('content').innerHTML = `<div class="toolbar">
      <input id="fSearch" class="grow" placeholder="🔍 Search events...">
      <select id="fStatus"><option value="">All statuses</option>${options(['UPCOMING', 'COMPLETED', 'CANCELLED'])}</select>
      <button class="btn primary" data-action="newEvent">＋ New event</button></div>
      <div class="panel tablewrap"><table><thead><tr><th>Title</th><th>Date &amp; time</th><th>Venue</th><th>Seats</th><th>Status</th><th>Coordinator</th><th>Actions</th></tr></thead>
      <tbody id="evRows"></tbody></table></div>`;
    const load = async () => {
      const q = new URLSearchParams({ search: $('fSearch').value, status: $('fStatus').value, mine: 'true' });
      const list = await api('GET', '/events?' + q);
      $('evRows').innerHTML = list.map((e) => `<tr><td><b>${esc(e.title)}</b><br><span class="small muted">${esc(e.category)}</span></td>
        <td>${fmtDate(e.date)}<br><span class="small">${esc(e.startTime)}-${esc(e.endTime)}</span></td><td>${esc(e.venue)}</td>
        <td>${e.registeredCount}/${e.capacity}</td><td>${badge(e.status)}</td><td>${esc(e.coordinatorName)}</td>
        <td><button class="btn sm" data-action="viewRegs" data-id="${e.id}">Attendance</button>
            <button class="btn sm" data-action="editEvent" data-id="${e.id}">Edit</button>
            <button class="btn danger sm" data-action="deleteEvent" data-id="${e.id}" data-name="${esc(e.title)}">Delete</button></td></tr>`).join('')
        || '<tr><td colspan="7" class="empty">No events found. Click "New event".</td></tr>';
    };
    let timer;
    $('fSearch').oninput = () => { clearTimeout(timer); timer = setTimeout(() => load().catch(e => toast(e.message, 'error')), 250); };
    $('fStatus').onchange = () => load().catch(e => toast(e.message, 'error'));
    reload = () => load().catch(e => toast(e.message, 'error'));
    await load();
  },

  async duties() {
    const list = await api('GET', '/duties');
    $('content').innerHTML = `<div class="toolbar"><button class="btn primary" data-action="assignDuty">＋ Assign duty</button></div>
      <div class="panel tablewrap"><table><tr><th>Event</th><th>Volunteer</th><th>Task</th><th>Date</th><th>Status</th><th>Attended</th><th>Actions</th></tr>` +
      (list.map((d) => `<tr><td>${esc(d.eventTitle)}</td><td>${esc(d.volunteerName)}</td><td>${esc(d.task)}</td><td>${fmtDate(d.date)}</td>
        <td><select data-action="dutyStatusSelect" data-id="${d.id}" style="width:auto">${options(['ASSIGNED', 'ACCEPTED', 'DECLINED', 'DONE'], d.status)}</select></td>
        <td>${d.attended ? badge('yes') : '—'}</td>
        <td><button class="btn sm" data-action="dutyAtt" data-id="${d.id}" data-val="${!d.attended}">${d.attended ? 'Mark absent' : 'Mark present'}</button>
            <button class="btn danger sm" data-action="deleteDuty" data-id="${d.id}">Remove</button></td></tr>`).join('')
        || '<tr><td colspan="7" class="empty">No duties yet. Click "Assign duty".</td></tr>') + `</table></div>`;
  },

  async users() {
    $('content').innerHTML = `<div class="toolbar">
      <input id="fSearch" class="grow" placeholder="🔍 Search name, username, email...">
      <select id="fRole"><option value="">All roles</option>${options(ROLES)}</select>
      <button class="btn primary" data-action="newUser">＋ Add user</button></div>
      <div class="panel tablewrap"><table><thead><tr><th>Name</th><th>Username</th><th>Email</th><th>Role</th><th>Dept / Year</th><th>Actions</th></tr></thead><tbody id="userRows"></tbody></table></div>`;
    const load = async () => {
      const q = new URLSearchParams({ search: $('fSearch').value, role: $('fRole').value });
      const list = await api('GET', '/users?' + q);
      $('userRows').innerHTML = list.map((u) => `<tr><td><b>${esc(u.name)}</b></td><td>${esc(u.username)}</td><td>${esc(u.email)}</td>
        <td><span class="badge">${esc(u.role)}</span></td><td>${esc(u.department || '')} ${u.year ? '/ Y' + u.year : ''}</td>
        <td><button class="btn sm" data-action="editUser" data-id="${u.id}">Edit</button>
            ${u.id === me.id ? '' : `<button class="btn danger sm" data-action="deleteUser" data-id="${u.id}" data-name="${esc(u.name)}">Delete</button>`}</td></tr>`).join('')
        || '<tr><td colspan="6" class="empty">No users found.</td></tr>';
      window.__users = list;
    };
    let timer;
    $('fSearch').oninput = () => { clearTimeout(timer); timer = setTimeout(() => load().catch(e => toast(e.message, 'error')), 250); };
    $('fRole').onchange = () => load().catch(e => toast(e.message, 'error'));
    reload = () => load().catch(e => toast(e.message, 'error'));
    await load();
  }
};

// ------------------------------------------------------------ forms used by actions
async function eventForm(ev) {
  const isEdit = !!ev;
  ev = ev || { title: '', category: 'Technical', venue: '', date: '', startTime: '10:00', endTime: '12:00', capacity: 50, description: '', status: 'UPCOMING' };
  let coordField = '';
  if (me.role === 'ADMIN') {
    const coords = await api('GET', '/users?role=COORDINATOR');
    const list = coords.map((c) => [c.id, c.name]).concat([[me.id, me.name + ' (admin)']]);
    coordField = selectField('Coordinator', 'coordinatorId', list, ev.coordinatorId || me.id);
  }
  const html = field('Title', 'title', ev.title, 'required minlength="3" maxlength="80"') +
    `<div class="row">${selectField('Category', 'category', CATEGORIES, ev.category)}${field('Venue', 'venue', ev.venue, 'required maxlength="60"')}</div>` +
    `<div class="row">${field('Date', 'date', ev.date, 'type="date" required')}${field('Capacity', 'capacity', ev.capacity, 'type="number" min="1" max="5000" required')}</div>` +
    `<div class="row">${field('Start time', 'startTime', ev.startTime, 'type="time" required')}${field('End time', 'endTime', ev.endTime, 'type="time" required')}</div>` +
    `<label>Description<textarea name="description" rows="3" maxlength="500">${esc(ev.description)}</textarea></label>` +
    (isEdit ? selectField('Status', 'status', ['UPCOMING', 'COMPLETED', 'CANCELLED'], ev.status) : '') + coordField;
  openModal(isEdit ? 'Edit event' : 'Create event', html, async (v) => {
    if (isEdit) await api('PUT', '/events/' + ev.id, v); else await api('POST', '/events', v);
    toast(isEdit ? 'Event updated' : 'Event created', 'good');
    reload();
  });
}

async function userForm(u) {
  const isEdit = !!u;
  u = u || { name: '', username: '', email: '', role: 'STUDENT', department: '', year: 2, skills: '' };
  const html = field('Full name', 'name', u.name, 'required minlength="2" maxlength="50"') +
    `<div class="row">${field('Username', 'username', u.username, 'required pattern="[A-Za-z0-9_]{3,20}" title="3-20 letters, digits or _"')}
     ${selectField('Role', 'role', ROLES, u.role)}</div>` +
    field('Email', 'email', u.email, 'type="email" required') +
    `<div class="row">${field('Department', 'department', u.department, 'maxlength="40"')}${field('Year (students/volunteers)', 'year', u.year || 2, 'type="number" min="1" max="4"')}</div>` +
    field('Skills (volunteers)', 'skills', u.skills, 'maxlength="100"') +
    field(isEdit ? 'New password (leave blank to keep)' : 'Password (min 6)', 'password', '', `type="password" maxlength="50" ${isEdit ? '' : 'required minlength="6"'}`);
  openModal(isEdit ? 'Edit user' : 'Add user', html, async (v) => {
    if (isEdit) await api('PUT', '/users/' + u.id, v); else await api('POST', '/users', v);
    toast(isEdit ? 'User updated' : 'User created', 'good');
    reload();
  });
}

async function showRegistrations(eventId) {
  const regs = await api('GET', '/events/' + eventId + '/registrations');
  const html = `<div class="tablewrap"><table><tr><th>Student</th><th>Username</th><th>Dept</th><th>Attended</th><th></th></tr>` +
    (regs.map((r) => `<tr><td>${esc(r.studentName)}</td><td>${esc(r.username)}</td><td>${esc(r.department)}</td>
      <td>${r.attended ? badge('yes') : '—'}</td>
      <td><button type="button" class="btn sm" data-action="regAtt" data-id="${r.id}" data-event="${eventId}" data-val="${!r.attended}">
        ${r.attended ? 'Mark absent' : 'Mark present'}</button></td></tr>`).join('')
      || '<tr><td colspan="5" class="empty">No registrations yet.</td></tr>') + `</table></div>`;
  openModal('Registrations & attendance', html, null, { wide: true });
}

async function dutyForm() {
  const [events, vols] = await Promise.all([api('GET', '/events?mine=true'), api('GET', '/volunteers')]);
  const open = events.filter((e) => e.status !== 'CANCELLED');
  if (!open.length) throw new Error('You have no active events to assign duties for.');
  if (!vols.length) throw new Error('There are no volunteers in the system yet.');
  const html = selectField('Event', 'eventId', open.map((e) => [e.id, e.title + ' (' + e.date + ')']), '') +
    selectField('Volunteer', 'volunteerId', vols.map((v) => [v.id, v.name + (v.skills ? ' - ' + v.skills : '')]), '') +
    field('Task / duty', 'task', '', 'required minlength="3" maxlength="100" placeholder="e.g. Registration desk"');
  openModal('Assign volunteer duty', html, async (v) => {
    await api('POST', '/duties', v);
    toast('Duty assigned', 'good');
    reload();
  }, { button: 'Assign' });
}

// ------------------------------------------------------------ 5) click actions (event delegation)
const ACTIONS = {
  go: (id) => go(id),
  closeModal: () => closeModal(),
  async register(id) { await api('POST', `/events/${id}/register`); toast('Registered successfully!', 'good'); reload(); },
  async cancelReg(id) {
    if (!confirm('Cancel your registration?')) return;
    await api('POST', `/events/${id}/cancel`); toast('Registration cancelled'); reload();
  },
  newEvent: () => eventForm(null),
  async editEvent(id) {
    const list = await api('GET', '/events?mine=true');
    eventForm(list.find((e) => e.id === id));
  },
  async deleteEvent(id, el) {
    if (!confirm(`Delete "${el.dataset.name}" together with its registrations and duties?`)) return;
    await api('DELETE', '/events/' + id); toast('Event deleted'); reload();
  },
  viewRegs: (id) => showRegistrations(id),
  async regAtt(id, el) {
    await api('POST', `/registrations/${id}/attendance`, { attended: el.dataset.val === 'true' });
    showRegistrations(el.dataset.event); reload();
  },
  assignDuty: () => dutyForm(),
  async dutyStatus(id, el) { await api('PUT', `/duties/${id}/status`, { status: el.dataset.val }); toast('Duty updated', 'good'); reload(); },
  async dutyStatusSelect(id, el) {
    try { await api('PUT', `/duties/${id}/status`, { status: el.value }); toast('Status updated', 'good'); }
    finally { reload(); }
  },
  async dutyAtt(id, el) { await api('POST', `/duties/${id}/attendance`, { attended: el.dataset.val === 'true' }); reload(); },
  async deleteDuty(id) { if (!confirm('Remove this duty?')) return; await api('DELETE', '/duties/' + id); toast('Duty removed'); reload(); },
  newUser: () => userForm(null),
  editUser: (id) => userForm(window.__users.find((u) => u.id === id)),
  async deleteUser(id, el) {
    if (!confirm(`Delete user "${el.dataset.name}"? Their registrations and duties will be removed.`)) return;
    await api('DELETE', '/users/' + id); toast('User deleted'); reload();
  }
};

async function runAction(el) {
  try { await ACTIONS[el.dataset.action](el.dataset.id, el); }
  catch (err) { toast(err.message, 'error'); }
}

document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-action]');
  if (!el || el.tagName === 'SELECT' || !ACTIONS[el.dataset.action]) return;
  runAction(el);
});
document.addEventListener('change', (e) => {
  if (e.target.tagName === 'SELECT' && e.target.dataset.action) runAction(e.target);
});

// ------------------------------------------------------------ 6) start-up
$('logoutBtn').onclick = async () => { try { await api('POST', '/logout'); } catch (e) { /* ignore */ } localLogout(); };
$('menuBtn').onclick = () => $('sidebar').classList.toggle('open');
$('modal').addEventListener('click', (e) => { if (e.target.id === 'modal') closeModal(); });

setupAuthScreen();
(async function init() {
  if (!token) return;
  try { me = await api('GET', '/me'); startApp(); } catch (e) { localLogout(); }
})();
