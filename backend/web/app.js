"use strict";

/**
 * Dashboard web untuk ORANG TUA - vanilla JS, tanpa framework/build step, konsisten dengan
 * backend (tanpa dependency npm eksternal). Disajikan same-origin oleh server.js yang sama
 * (lihat serveStatic di server.js) jadi semua panggilan API di bawah pakai path relatif,
 * tidak perlu CORS.
 *
 * Versi pertama (inti): Dashboard, Daftar Tugas, Approval Tugas. Kunci Perangkat, Chat, dan
 * Pengaturan (kelola profil anak) BELUM ada di sini - tetap dikelola lewat aplikasi Android
 * untuk saat ini, menyusul di iterasi berikutnya.
 *
 * Login HANYA lewat email+password orang tua (POST /auth/login-parent) - akun anak (kode
 * keluarga+PIN) sengaja TIDAK didukung di sini, supaya halaman ini murni jadi kanal kontrol
 * orang tua, konsisten dengan namanya "Dashboard Orang Tua".
 *
 * Token disimpan di localStorage (bukan cookie httpOnly) - cukup aman selama halaman ini tidak
 * pernah memuat skrip pihak ketiga apa pun (tidak ada di sini), tapi tetap kurang aman
 * dibanding EncryptedSharedPreferences di Android. Wajar untuk v1 dashboard web sederhana;
 * dicatat di sini secara transparan sesuai kebiasaan proyek ini.
 */

const TOKEN_KEY = "pactio_web_token";
const USER_KEY = "pactio_web_user";

const state = {
  token: localStorage.getItem(TOKEN_KEY) || null,
  user: safeParse(localStorage.getItem(USER_KEY)),
  family: null,
  children: [],
  tasks: [],
  loading: false,
  errorMessage: null,
  infoMessage: null,
  tab: "dashboard", // dashboard | tasks | approval
  taskFilter: { childId: "", status: "" },
  detailTaskId: null,
  showCreateTask: false,
  loginError: null
};

function safeParse(json) {
  try { return json ? JSON.parse(json) : null; } catch { return null; }
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

const STATUS_LABEL = { assigned: "Belum dikirim", submitted: "Menunggu persetujuan", approved: "Disetujui", rejected: "Ditolak" };

// --- Panggilan API ------------------------------------------------------------------------

async function api(method, path, body) {
  const headers = { "Content-Type": "application/json" };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch(path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
  const json = await response.json().catch(() => ({}));
  if (response.status === 401) {
    clearSession();
    throw new Error(json.error || "Sesi berakhir, silakan masuk kembali.");
  }
  if (!response.ok) throw new Error(json.error || "Terjadi kesalahan.");
  return json;
}

/** Untuk endpoint biner (foto/dokumen bukti) - mengembalikan Blob, bukan JSON. */
async function apiBytes(path) {
  const headers = {};
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch(path, { headers });
  if (response.status === 401) { clearSession(); throw new Error("Sesi berakhir, silakan masuk kembali."); }
  if (!response.ok) {
    const json = await response.json().catch(() => ({}));
    throw new Error(json.error || "Gagal memuat berkas.");
  }
  return response.blob();
}

function clearSession() {
  state.token = null;
  state.user = null;
  state.family = null;
  state.children = [];
  state.tasks = [];
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function guarded(fn) {
  state.loading = true;
  state.errorMessage = null;
  render();
  try {
    await fn();
  } catch (error) {
    state.errorMessage = error.message || "Terjadi kesalahan.";
  }
  state.loading = false;
  render();
}

async function loadFamily() {
  const result = await api("GET", "/family");
  state.family = result.family;
  state.children = result.children;
}

async function loadTasks() {
  const result = await api("GET", "/tasks");
  state.tasks = result.tasks;
}

async function refreshAll() {
  await Promise.all([loadFamily(), loadTasks()]);
}

// --- Aksi -----------------------------------------------------------------------------

async function handleLogin(email, password) {
  state.loginError = null;
  state.loading = true;
  render();
  try {
    const result = await api("POST", "/auth/login-parent", { email, password });
    if (result.user.role !== "parent") {
      throw new Error("Dashboard ini khusus akun orang tua.");
    }
    state.token = result.token;
    state.user = result.user;
    localStorage.setItem(TOKEN_KEY, state.token);
    localStorage.setItem(USER_KEY, JSON.stringify(state.user));
    await refreshAll();
  } catch (error) {
    state.loginError = error.message || "Email atau kata sandi salah.";
    state.token = null;
    state.user = null;
  }
  state.loading = false;
  render();
}

function handleLogout() {
  clearSession();
  render();
}

async function handleCreateTask(childId, title, description, rewardMinutes) {
  await guarded(async () => {
    await api("POST", "/tasks", { childId, title, description, rewardMinutes });
    await loadTasks();
    state.showCreateTask = false;
    state.infoMessage = "Tugas berhasil dibuat.";
  });
}

async function handleDecide(taskId, approved, note) {
  await guarded(async () => {
    await api("POST", `/tasks/${taskId}/decision`, { approved, note: note || "" });
    await loadTasks();
    state.infoMessage = approved ? "Tugas disetujui." : "Tugas ditolak.";
  });
}

// --- Render -----------------------------------------------------------------------------

const root = document.getElementById("app");

function render() {
  root.innerHTML = "";
  if (state.loading) {
    const bar = document.createElement("div");
    bar.className = "loading-bar";
    root.appendChild(bar);
  }
  if (!state.token || !state.user) {
    root.appendChild(renderLogin());
    return;
  }
  root.appendChild(renderApp());
}

function renderLogin() {
  const wrap = document.createElement("div");
  wrap.className = "login-screen";
  wrap.innerHTML = `
    <div class="login-card">
      <h1>Pactio</h1>
      <p class="subtitle">Dashboard Orang Tua</p>
      ${state.loginError ? `<div class="banner banner-error">${escapeHtml(state.loginError)}</div>` : ""}
      <form id="login-form">
        <div class="field">
          <label>Email</label>
          <input type="email" name="email" required autocomplete="username" />
        </div>
        <div class="field">
          <label>Kata sandi</label>
          <input type="password" name="password" required autocomplete="current-password" />
        </div>
        <button type="submit" class="btn btn-primary btn-block" ${state.loading ? "disabled" : ""}>Masuk</button>
      </form>
    </div>
  `;
  wrap.querySelector("#login-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    handleLogin(form.get("email"), form.get("password"));
  });
  return wrap;
}

function renderApp() {
  const wrap = document.createElement("div");

  // Top bar
  const topbar = document.createElement("div");
  topbar.className = "topbar";
  topbar.innerHTML = `
    <div class="brand">Pactio</div>
    <div class="actions">
      <span style="color: var(--text-muted); font-size: 14px;">${escapeHtml(state.user.name)}</span>
      <button class="btn btn-text btn-sm" id="logout-btn">Keluar</button>
    </div>
  `;
  topbar.querySelector("#logout-btn").addEventListener("click", handleLogout);
  wrap.appendChild(topbar);

  // Tabs
  const approvalCount = state.tasks.filter((t) => t.status === "submitted").length;
  const tabs = document.createElement("div");
  tabs.className = "tabs";
  tabs.innerHTML = [
    tabHtml("dashboard", "Dashboard"),
    tabHtml("tasks", "Daftar Tugas"),
    tabHtml("approval", "Approval Tugas", approvalCount)
  ].join("");
  tabs.querySelectorAll(".tab").forEach((el) => {
    el.addEventListener("click", () => { state.tab = el.dataset.tab; render(); });
  });
  wrap.appendChild(tabs);

  // Content
  const content = document.createElement("div");
  content.className = "content";

  if (state.errorMessage) content.appendChild(banner("error", state.errorMessage, () => { state.errorMessage = null; render(); }));
  if (state.infoMessage) content.appendChild(banner("info", state.infoMessage, () => { state.infoMessage = null; render(); }));

  if (state.tab === "dashboard") content.appendChild(renderDashboard());
  else if (state.tab === "tasks") content.appendChild(renderTaskList());
  else if (state.tab === "approval") content.appendChild(renderApproval());

  wrap.appendChild(content);

  if (state.showCreateTask) wrap.appendChild(renderCreateTaskModal());
  const detailTask = state.tasks.find((t) => t.id === state.detailTaskId);
  if (detailTask) wrap.appendChild(renderTaskDetailModal(detailTask));

  return wrap;
}

function tabHtml(key, label, badgeCount) {
  const badge = badgeCount ? `<span class="status-chip status-submitted badge">${badgeCount}</span>` : "";
  return `<div class="tab ${state.tab === key ? "active" : ""}" data-tab="${key}">${escapeHtml(label)}${badge}</div>`;
}

function banner(kind, message, onClose) {
  const el = document.createElement("div");
  el.className = `banner banner-${kind}`;
  el.innerHTML = `${escapeHtml(message)}<button class="close">&times;</button>`;
  el.querySelector(".close").addEventListener("click", onClose);
  return el;
}

function childName(childId) {
  const child = state.children.find((c) => c.id === childId);
  return child ? child.name : null;
}

// --- Dashboard ---------------------------------------------------------------------------

function renderDashboard() {
  const el = document.createElement("div");
  const approvalCount = state.tasks.filter((t) => t.status === "submitted").length;
  const lockedCount = state.children.filter((c) => c.lockModeEnabled).length;

  el.innerHTML = `
    <div class="card">
      <h2 style="margin-bottom: 6px;">${escapeHtml(state.family?.name || "Keluarga")}</h2>
      ${state.family?.code ? `<div class="family-code">${escapeHtml(state.family.code)}</div>` : ""}
      ${state.children.length === 0 ? `<p style="color: var(--text-muted); margin-top: 12px;">Belum ada profil anak. Tambah lewat aplikasi Android (Pengaturan).</p>` : ""}
    </div>
    <div class="stat-grid">
      <div class="stat-card"><div class="value">${approvalCount}</div><div class="label">Menunggu Approval</div></div>
      <div class="stat-card"><div class="value">${state.children.length}</div><div class="label">Anak</div></div>
      <div class="stat-card"><div class="value">${lockedCount}</div><div class="label">Terkunci</div></div>
      <div class="stat-card"><div class="value">${state.tasks.length}</div><div class="label">Total Tugas</div></div>
    </div>
    <button class="btn btn-primary" id="open-create-task" ${state.children.length === 0 ? "disabled" : ""}>+ Buat Tugas</button>
  `;
  el.querySelector("#open-create-task").addEventListener("click", () => { state.showCreateTask = true; render(); });
  return el;
}

function renderCreateTaskModal() {
  const overlay = document.createElement("div");
  overlay.className = "modal-overlay";
  overlay.innerHTML = `
    <div class="modal">
      <h2>Buat tugas baru</h2>
      <form id="create-task-form">
        <div class="field">
          <label>Anak</label>
          <select name="childId" required>
            ${state.children.map((c) => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.name)}</option>`).join("")}
          </select>
        </div>
        <div class="field"><label>Judul tugas</label><input type="text" name="title" required /></div>
        <div class="field"><label>Deskripsi (opsional)</label><textarea name="description"></textarea></div>
        <div class="field"><label>Hadiah (menit, 1-240)</label><input type="number" name="rewardMinutes" min="1" max="240" value="15" required /></div>
        <div class="modal-close-row">
          <button type="button" class="btn btn-text" id="cancel-create-task">Batal</button>
          <button type="submit" class="btn btn-primary" ${state.loading ? "disabled" : ""}>Buat Tugas</button>
        </div>
      </form>
    </div>
  `;
  overlay.addEventListener("click", (event) => { if (event.target === overlay) { state.showCreateTask = false; render(); } });
  overlay.querySelector("#cancel-create-task").addEventListener("click", () => { state.showCreateTask = false; render(); });
  overlay.querySelector("#create-task-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    const rewardMinutes = Number(form.get("rewardMinutes"));
    handleCreateTask(form.get("childId"), form.get("title"), form.get("description") || "", rewardMinutes);
  });
  return overlay;
}

// --- Daftar Tugas --------------------------------------------------------------------------

function renderTaskList() {
  const el = document.createElement("div");

  const filterRow = document.createElement("div");
  filterRow.className = "filter-row";
  const childOptions = state.children.map((c) => `<option value="${escapeHtml(c.id)}" ${state.taskFilter.childId === c.id ? "selected" : ""}>${escapeHtml(c.name)}</option>`).join("");
  const statusOptions = Object.entries(STATUS_LABEL).map(([value, label]) => `<option value="${value}" ${state.taskFilter.status === value ? "selected" : ""}>${escapeHtml(label)}</option>`).join("");
  filterRow.innerHTML = `
    ${state.children.length > 1 ? `<select id="filter-child"><option value="">Semua Anak</option>${childOptions}</select>` : ""}
    <select id="filter-status"><option value="">Semua Status</option>${statusOptions}</select>
  `;
  const childSelect = filterRow.querySelector("#filter-child");
  if (childSelect) childSelect.addEventListener("change", (e) => { state.taskFilter.childId = e.target.value; render(); });
  filterRow.querySelector("#filter-status").addEventListener("change", (e) => { state.taskFilter.status = e.target.value; render(); });
  el.appendChild(filterRow);

  const filtered = state.tasks.filter((t) =>
    (!state.taskFilter.status || t.status === state.taskFilter.status) &&
    (!state.taskFilter.childId || t.childId === state.taskFilter.childId)
  );

  if (filtered.length === 0) {
    el.appendChild(emptyHint("Tidak ada tugas yang cocok dengan filter."));
    return el;
  }

  filtered.forEach((task) => el.appendChild(taskRow(task)));
  return el;
}

function taskRow(task) {
  const row = document.createElement("div");
  row.className = "task-row";
  const name = childName(task.childId);
  row.innerHTML = `
    <div class="info">
      <div class="title">${escapeHtml(task.title)}</div>
      <div class="meta">${name ? escapeHtml(name) + " &middot; " : ""}${task.rewardMinutes} menit</div>
    </div>
    <span class="status-chip status-${task.status}">${STATUS_LABEL[task.status] || task.status}</span>
  `;
  row.addEventListener("click", () => { state.detailTaskId = task.id; render(); });
  return row;
}

function emptyHint(text) {
  const el = document.createElement("div");
  el.className = "empty-hint";
  el.textContent = text;
  return el;
}

function renderTaskDetailModal(task) {
  const overlay = document.createElement("div");
  overlay.className = "modal-overlay";
  const name = childName(task.childId);
  overlay.innerHTML = `
    <div class="modal">
      <h2>${escapeHtml(task.title)}</h2>
      <span class="status-chip status-${task.status}">${STATUS_LABEL[task.status] || task.status}</span>
      ${name ? `<p style="margin: 10px 0 0; color: var(--text-muted);">Anak: ${escapeHtml(name)}</p>` : ""}
      <p style="color: var(--primary); font-weight: 700;">Hadiah: ${task.rewardMinutes} menit akses</p>
      ${task.description ? `<p>${escapeHtml(task.description)}</p>` : ""}
      ${task.evidence ? `<p><strong>Bukti (teks):</strong> ${escapeHtml(task.evidence)}</p>` : ""}
      ${task.evidenceFiles && task.evidenceFiles.length ? `<div><strong>Berkas bukti:</strong></div><div class="evidence-gallery" id="evidence-gallery"></div>` : ""}
      ${task.status === "rejected" && task.decisionNote ? `<p><strong>Catatan orang tua:</strong> ${escapeHtml(task.decisionNote)}</p>` : ""}
      <div class="modal-close-row">
        <button type="button" class="btn btn-primary" id="close-detail">Tutup</button>
      </div>
    </div>
  `;
  overlay.addEventListener("click", (event) => { if (event.target === overlay) { state.detailTaskId = null; render(); } });
  overlay.querySelector("#close-detail").addEventListener("click", () => { state.detailTaskId = null; render(); });

  const gallery = overlay.querySelector("#evidence-gallery");
  if (gallery) populateEvidenceGallery(gallery, task);

  return overlay;
}

/** Memuat tiap berkas bukti async - gambar tampil sebagai thumbnail (klik untuk pratinjau penuh), dokumen sebagai tombol unduh/buka. */
function populateEvidenceGallery(container, task) {
  task.evidenceFiles.forEach((file) => {
    const btn = document.createElement("button");
    btn.className = "evidence-thumb";
    btn.type = "button";
    btn.textContent = "Memuat...";
    container.appendChild(btn);

    const isImage = file.mime === "image/jpeg" || file.mime === "image/png";
    apiBytes(`/tasks/${task.id}/evidence/${file.id}`)
      .then((blob) => {
        const url = URL.createObjectURL(blob);
        if (isImage) {
          btn.innerHTML = `<img src="${url}" alt="Bukti" />`;
          btn.addEventListener("click", () => showImagePreview(url));
        } else {
          btn.textContent = extensionLabel(file.mime);
          btn.addEventListener("click", () => {
            const link = document.createElement("a");
            link.href = url;
            link.target = "_blank";
            link.rel = "noopener";
            link.click();
          });
        }
      })
      .catch(() => { btn.textContent = "Gagal memuat"; });
  });
}

function extensionLabel(mime) {
  const map = {
    "application/pdf": "PDF",
    "application/msword": "DOC",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "DOCX",
    "application/vnd.ms-excel": "XLS",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": "XLSX",
    "application/vnd.ms-powerpoint": "PPT",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation": "PPTX",
    "text/plain": "TXT"
  };
  return map[mime] || "Berkas";
}

function showImagePreview(url) {
  const overlay = document.createElement("div");
  overlay.className = "modal-overlay";
  overlay.innerHTML = `
    <div class="modal image-preview">
      <img src="${url}" alt="Pratinjau bukti" />
      <div class="modal-close-row">
        <button type="button" class="btn btn-primary" id="close-preview">Tutup</button>
      </div>
    </div>
  `;
  overlay.addEventListener("click", (event) => { if (event.target === overlay) overlay.remove(); });
  overlay.querySelector("#close-preview").addEventListener("click", () => overlay.remove());
  document.body.appendChild(overlay);
}

// --- Approval Tugas ------------------------------------------------------------------------

function renderApproval() {
  const el = document.createElement("div");
  const waiting = state.tasks.filter((t) => t.status === "submitted");
  if (waiting.length === 0) {
    el.appendChild(emptyHint("Belum ada tugas yang dikirim anak."));
    return el;
  }
  waiting.forEach((task) => el.appendChild(approvalCard(task)));
  return el;
}

function approvalCard(task) {
  const el = document.createElement("div");
  el.className = "approval-card";
  const name = childName(task.childId);
  el.innerHTML = `
    <div class="title">${escapeHtml(task.title)}</div>
    <div class="meta">${name ? escapeHtml(name) + " &middot; " : ""}${task.rewardMinutes} menit akses</div>
    ${task.evidence ? `<div class="evidence-text"><strong>Bukti:</strong> ${escapeHtml(task.evidence)}</div>` : ""}
    ${task.evidenceFiles && task.evidenceFiles.length ? `<div class="evidence-gallery" id="gallery-${task.id}"></div>` : ""}
    <div class="approval-actions">
      <button class="btn btn-primary" id="approve-${task.id}" ${state.loading ? "disabled" : ""}>Setujui</button>
      <button class="btn btn-outline" id="reject-toggle-${task.id}" ${state.loading ? "disabled" : ""}>Tolak</button>
    </div>
    <div class="reject-form" id="reject-form-${task.id}">
      <div class="field"><textarea id="reject-note-${task.id}" placeholder="Catatan untuk anak (opsional)"></textarea></div>
      <button class="btn btn-danger btn-sm" id="reject-confirm-${task.id}">Tolak Tugas</button>
    </div>
  `;

  const gallery = el.querySelector(`#gallery-${task.id}`);
  if (gallery) populateEvidenceGallery(gallery, task);

  el.querySelector(`#approve-${task.id}`).addEventListener("click", () => handleDecide(task.id, true, ""));
  el.querySelector(`#reject-toggle-${task.id}`).addEventListener("click", () => {
    el.querySelector(`#reject-form-${task.id}`).classList.toggle("open");
  });
  el.querySelector(`#reject-confirm-${task.id}`).addEventListener("click", () => {
    const note = el.querySelector(`#reject-note-${task.id}`).value;
    handleDecide(task.id, false, note);
  });

  return el;
}

// --- Mulai ------------------------------------------------------------------------------

render();
if (state.token && state.user) {
  guarded(refreshAll);
}
