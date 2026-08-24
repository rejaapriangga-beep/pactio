"use strict";

/**
 * Dashboard web untuk ORANG TUA - vanilla JS, tanpa framework/build step, konsisten dengan
 * backend (tanpa dependency npm eksternal). Disajikan same-origin oleh server.js yang sama
 * (lihat serveStatic di server.js) jadi semua panggilan API di bawah pakai path relatif,
 * tidak perlu CORS.
 *
 * Cakupan: Dashboard (ringkasan + tugas belum selesai per anak + pratinjau chat grup),
 * Daftar Tugas, Approval Tugas, Kunci Perangkat, Chat, dan Pengaturan (kelola profil anak,
 * reset PIN anak, log aktivitas) - setara aplikasi Android untuk peran orang tua.
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

/** Thread key khusus grup obrolan bersama SEMUA anggota keluarga - harus sama persis dengan FAMILY_THREAD_KEY di server.js & FAMILY_CHAT_THREAD_ID di Android. */
const FAMILY_CHAT_THREAD_ID = "family";

/** Jarak antar poll otomatis di background - sama dengan AUTO_REFRESH_MS di Android MainActivity.kt. */
const AUTO_REFRESH_MS = 8000;
/** Poll pesan chat lebih cepat selagi tab Chat aktif - sama dengan ChatScreen.kt di Android. */
const CHAT_POLL_MS = 4000;

const state = {
  token: localStorage.getItem(TOKEN_KEY) || null,
  user: safeParse(localStorage.getItem(USER_KEY)),
  family: null,
  children: [],
  tasks: [],
  loading: false,
  errorMessage: null,
  infoMessage: null,
  tab: "dashboard", // dashboard | tasks | approval | lock | chat | settings
  taskFilter: { childId: "", status: "" },
  detailTaskId: null,
  showCreateTask: false,
  loginError: null,
  // Chat
  chatThreadId: FAMILY_CHAT_THREAD_ID,
  chatMessages: [],
  chatUnreadTotal: 0,
  chatUnreadByThread: {}, // { [threadId]: count } - badge per sub-tab thread (lihat renderChatSubTabs)
  chatSending: false,
  chatError: null,
  chatReplyTarget: null, // { id, senderLabel, preview } - pesan yang sedang dibalas, null kalau tidak
  reactionPickerMessageId: null, // id pesan yang popover pilihan emoji-nya sedang terbuka
  // Dashboard - pratinjau ringkas, terpisah dari state chat tab (chatMessages) supaya tidak
  // saling menimpa saat dua-duanya aktif dipakai.
  dashboardChatPreview: [],
  dashboardCardModal: null, // "approval" | "children" | "locked" | "tasks" | null
  // Pengaturan
  showAddChild: false,
  childPendingDeleteId: null,
  childPendingResetPinId: null,
  activityLog: [],
  showBackupModal: false,
  backupSending: false,
  backupError: null
};

function safeParse(json) {
  try { return json ? JSON.parse(json) : null; } catch { return null; }
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

const STATUS_LABEL = { assigned: "Belum dikirim", submitted: "Menunggu persetujuan", approved: "Disetujui", rejected: "Ditolak" };

/** Emoji reaksi yang didukung - HARUS SAMA PERSIS dengan ALLOWED_CHAT_REACTIONS di server.js. */
const CHAT_REACTION_EMOJI = ["👍", "❤️", "😂", "😮", "😢", "🙏"];

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
  state.chatMessages = [];
  state.chatUnreadTotal = 0;
  state.chatUnreadByThread = {};
  state.chatReplyTarget = null;
  state.reactionPickerMessageId = null;
  state.dashboardChatPreview = [];
  state.dashboardCardModal = null;
  state.activityLog = [];
  state.showBackupModal = false;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function guarded(fn) {
  state.loading = true;
  state.errorMessage = null;
  render();
  try {
    await fn();
    // Semua aksi lewat guarded() (buat/putuskan tugas, kunci, kelola profil anak) berpotensi
    // menambah entri baru di server (lihat logActivity di server.js) - paksa Log Aktivitas
    // dimuat ulang di render berikutnya kalau sedang berada di tab Pengaturan, supaya entri
    // barunya langsung terlihat tanpa harus pindah tab dulu.
    lastLoadedSettingsTab = false;
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

async function loadChatUnread() {
  const result = await api("GET", "/chat/unread-summary");
  state.chatUnreadTotal = result.total;
  const byThread = {};
  result.threads.forEach((t) => { byThread[t.childId] = t.unreadCount; });
  state.chatUnreadByThread = byThread;
}

/** 4 pesan terakhir dari grup keluarga, untuk pratinjau di Dashboard - TIDAK menandai thread sebagai terbaca (lihat komentar di loadChatMessages), murni pratinjau. */
async function loadDashboardChatPreview() {
  const result = await api("GET", `/chat/${FAMILY_CHAT_THREAD_ID}/messages`);
  state.dashboardChatPreview = result.messages.slice(-4);
}

async function refreshAll() {
  await Promise.all([loadFamily(), loadTasks(), loadChatUnread(), loadDashboardChatPreview()]);
}

/**
 * true kalau user sedang mengetik di suatu field teks (composer chat, catatan tolak tugas,
 * form tambah anak, dsb). Dipakai untuk MELEWATKAN re-render penuh dari polling latar
 * belakang - pendekatan render() di file ini selalu reset innerHTML total, yang akan
 * menghapus fokus & isi field yang sedang diketik kalau dipaksa render ulang di tengah
 * mengetik. Data tetap diambil di background seperti biasa; tampilannya menyusul begitu
 * user selesai mengetik / pindah fokus, di siklus poll berikutnya.
 */
function isTypingInField() {
  const el = document.activeElement;
  if (!el) return false;
  if (el.tagName === "TEXTAREA") return true;
  if (el.tagName === "INPUT") return ["text", "", "search", "email", "password", "number"].includes(el.type);
  return false;
}

/**
 * Poll berkala di background - supaya tugas baru/status berubah/pesan chat dari PERANGKAT
 * LAIN (mis. Android orang tua yang sama, atau anak) muncul otomatis tanpa perlu refresh
 * manual. Sengaja diam-diam (tidak menyentuh loading/errorMessage) - konsisten dengan
 * AppViewModel.silentRefresh di Android.
 */
async function silentRefresh() {
  if (!state.token) return;
  try {
    await Promise.all([loadFamily(), loadTasks(), loadChatUnread(), loadDashboardChatPreview()]);
    if (!isTypingInField()) render();
  } catch {
    // kegagalan sesekali (jaringan hiccup) tidak perlu ditampilkan sebagai error - dicoba lagi siklus berikutnya
  }
}
setInterval(silentRefresh, AUTO_REFRESH_MS);

// --- Poll pesan chat (lebih cepat, cuma selagi tab Chat aktif) -----------------------------

let chatPollTimer = null;
let lastLoadedChatThreadId = null;

/**
 * Dipanggil di akhir tiap render() - menyalakan/mematikan poll cepat sesuai tab aktif, dan
 * memuat pesan sekali segera saat tab Chat baru dibuka / thread-nya baru dipilih (bukan
 * menunggu siklus poll pertama).
 */
function ensureChatPolling() {
  const shouldPoll = Boolean(state.token) && state.tab === "chat";
  if (!shouldPoll) {
    lastLoadedChatThreadId = null;
    if (chatPollTimer) { clearInterval(chatPollTimer); chatPollTimer = null; }
    return;
  }
  if (lastLoadedChatThreadId !== state.chatThreadId) {
    lastLoadedChatThreadId = state.chatThreadId;
    loadChatMessages(true).then(render).catch(() => {});
  }
  if (!chatPollTimer) {
    chatPollTimer = setInterval(async () => {
      try {
        await loadChatMessages(true);
        if (!isTypingInField()) render();
      } catch {
        // sama seperti silentRefresh - dicoba lagi siklus berikutnya
      }
    }, CHAT_POLL_MS);
  }
}

// --- Log aktivitas (dimuat sekali tiap kali tab Pengaturan dibuka, bukan dipoll berkala -
// riwayat, bukan sesuatu yang perlu real-time) --------------------------------------------

let lastLoadedSettingsTab = false;

function ensureSettingsDataLoaded() {
  if (state.tab !== "settings") { lastLoadedSettingsTab = false; return; }
  if (lastLoadedSettingsTab) return;
  lastLoadedSettingsTab = true;
  api("GET", "/activity-log").then((result) => {
    state.activityLog = result.entries;
    if (!isTypingInField()) render();
  }).catch(() => {
    // gagal diam-diam - Pengaturan tetap tampil tanpa riwayat, tidak menghalangi profil anak/tambah anak
  });
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

async function handleSetLock(childId, enabled) {
  await guarded(async () => {
    await api("POST", `/children/${childId}/lock`, { enabled });
    await loadFamily();
    state.infoMessage = enabled ? "Perangkat anak dikunci." : "Kunci perangkat dibuka.";
  });
}

async function handleAddChild(name, pin) {
  await guarded(async () => {
    await api("POST", "/family/children", { name, pin });
    await loadFamily();
    state.showAddChild = false;
    state.infoMessage = "Profil anak berhasil ditambahkan.";
  });
}

async function handleResetPin(childId, pin) {
  await guarded(async () => {
    await api("POST", `/family/children/${childId}/reset-pin`, { pin });
    state.childPendingResetPinId = null;
    state.infoMessage = "PIN anak berhasil diatur ulang.";
  });
}

async function handleDeleteChild(childId) {
  await guarded(async () => {
    await api("DELETE", `/family/children/${childId}`);
    await loadFamily();
    await loadTasks();
    state.childPendingDeleteId = null;
    state.infoMessage = "Profil anak dihapus.";
  });
}

/**
 * Membuat backup terenkripsi lewat POST /backup/create (server yang mengenkripsi pakai
 * kata sandi ini - lihat catatan lengkap di server.js), lalu langsung memicu unduhan berkas
 * JSON-nya di browser. Kata sandi TIDAK pernah disimpan di mana pun (termasuk localStorage) -
 * hanya dipakai sesaat untuk request ini.
 */
async function handleDownloadBackup(password) {
  state.backupSending = true;
  state.backupError = null;
  render();
  try {
    const result = await api("POST", "/backup/create", { password });
    const blob = new Blob([JSON.stringify(result, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const dateStr = new Date().toISOString().slice(0, 10);
    const familyName = (state.family?.name || "pactio").replace(/[^a-z0-9]+/gi, "-").toLowerCase();
    const link = document.createElement("a");
    link.href = url;
    link.download = `pactio-backup-${familyName}-${dateStr}.json`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    state.showBackupModal = false;
    state.infoMessage = "Backup terenkripsi berhasil diunduh. Simpan kata sandinya baik-baik - server TIDAK menyimpannya, jadi tanpa kata sandi itu berkas backup ini tidak akan bisa dibuka lagi.";
  } catch (error) {
    state.backupError = error.message || "Gagal membuat backup.";
  }
  state.backupSending = false;
  render();
}

// --- Chat -----------------------------------------------------------------------------

/**
 * Isi thread aktif diambil/dikirim terpisah dari refreshAll biasa (mirip pola ChatScreen di
 * Android) - dipanggil saat tab Chat dibuka & lewat poll cepat CHAT_POLL_MS selagi aktif.
 * Menandai terbaca setiap kali dibuka atau ada pesan MASUK baru (bukan pesan sendiri).
 */
async function loadChatMessages(markReadIfNew) {
  const previousIds = new Set(state.chatMessages.map((m) => m.id));
  const result = await api("GET", `/chat/${state.chatThreadId}/messages`);
  state.chatMessages = result.messages;
  const hasNewIncoming = result.messages.some((m) => !previousIds.has(m.id) && m.senderId !== state.user.id);
  if (markReadIfNew && (previousIds.size === 0 || hasNewIncoming)) {
    await api("POST", `/chat/${state.chatThreadId}/read`);
    await loadChatUnread();
  }
}

async function handleSendChatText(text) {
  if (!text.trim()) return;
  state.chatSending = true;
  state.chatError = null;
  render();
  try {
    const replyToId = state.chatReplyTarget?.id;
    const body = { type: "text", text: text.trim() };
    if (replyToId) body.replyToId = replyToId;
    const result = await api("POST", `/chat/${state.chatThreadId}/messages`, body);
    state.chatMessages = [...state.chatMessages, result.message];
    state.chatReplyTarget = null;
  } catch (error) {
    state.chatError = error.message || "Gagal mengirim pesan.";
  }
  state.chatSending = false;
  render();
}

/** file: objek File dari <input type="file"> - dibaca sebagai data URI base64 lalu dikirim (foto chat, JPEG/PNG saja). */
async function handleSendChatPhoto(file) {
  state.chatSending = true;
  state.chatError = null;
  render();
  try {
    const dataUri = await readFileAsDataUri(file);
    const replyToId = state.chatReplyTarget?.id;
    const body = { type: "photo", photo: dataUri };
    if (replyToId) body.replyToId = replyToId;
    const result = await api("POST", `/chat/${state.chatThreadId}/messages`, body);
    state.chatMessages = [...state.chatMessages, result.message];
    state.chatReplyTarget = null;
  } catch (error) {
    state.chatError = error.message || "Gagal mengirim foto.";
  }
  state.chatSending = false;
  render();
}

/** Toggle reaksi SAYA dengan emoji ini pada satu pesan - lihat komentar lengkap di POST /chat/:threadId/messages/:id/react (server.js): satu user cuma bisa punya satu reaksi aktif per pesan. */
async function handleReact(threadId, messageId, emoji) {
  try {
    const result = await api("POST", `/chat/${threadId}/messages/${messageId}/react`, { emoji });
    state.chatMessages = state.chatMessages.map((m) => (m.id === messageId ? result.message : m));
  } catch (error) {
    state.chatError = error.message || "Gagal memberi reaksi.";
  }
  state.reactionPickerMessageId = null;
  render();
}

function readFileAsDataUri(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error("Gagal membaca berkas."));
    reader.readAsDataURL(file);
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
    ensureChatPolling();
    return;
  }
  root.appendChild(renderApp());
  ensureChatPolling();
  ensureSettingsDataLoaded();
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
    tabHtml("approval", "Approval Tugas", approvalCount),
    tabHtml("chat", "Chat", state.chatUnreadTotal),
    tabHtml("lock", "Kunci Perangkat"),
    tabHtml("settings", "Pengaturan")
  ].join("");
  tabs.querySelectorAll(".tab").forEach((el) => {
    el.addEventListener("click", () => { state.tab = el.dataset.tab; render(); });
  });
  wrap.appendChild(tabs);

  // Sub-tabs pemilih thread ("Semua Anak" + tiap anak) - tepat di bawah menu utama, HANYA
  // selagi tab Chat aktif. Menggantikan dropdown lama di dalam konten tab Chat.
  if (state.tab === "chat" && state.children.length > 0) {
    wrap.appendChild(renderChatSubTabs());
  }

  // Content
  const content = document.createElement("div");
  content.className = "content";
  if (state.tab === "chat") content.classList.add("content-chat");

  if (state.errorMessage) content.appendChild(banner("error", state.errorMessage, () => { state.errorMessage = null; render(); }));
  if (state.infoMessage) content.appendChild(banner("info", state.infoMessage, () => { state.infoMessage = null; render(); }));

  if (state.tab === "dashboard") content.appendChild(renderDashboard());
  else if (state.tab === "tasks") content.appendChild(renderTaskList());
  else if (state.tab === "approval") content.appendChild(renderApproval());
  else if (state.tab === "chat") content.appendChild(renderChatTab());
  else if (state.tab === "lock") content.appendChild(renderLockTab());
  else if (state.tab === "settings") content.appendChild(renderSettingsTab());

  wrap.appendChild(content);

  if (state.showCreateTask) wrap.appendChild(renderCreateTaskModal());
  const detailTask = state.tasks.find((t) => t.id === state.detailTaskId);
  if (detailTask) wrap.appendChild(renderTaskDetailModal(detailTask));
  if (state.showAddChild) wrap.appendChild(renderAddChildModal());
  const deleteTarget = state.children.find((c) => c.id === state.childPendingDeleteId);
  if (deleteTarget) wrap.appendChild(renderDeleteChildModal(deleteTarget));
  const resetPinTarget = state.children.find((c) => c.id === state.childPendingResetPinId);
  if (resetPinTarget) wrap.appendChild(renderResetPinModal(resetPinTarget));
  if (state.dashboardCardModal) wrap.appendChild(renderDashboardCardModal());
  if (state.showBackupModal) wrap.appendChild(renderBackupModal());

  return wrap;
}

/**
 * Baris sub-tab pemilih thread chat, ditampilkan di bawah menu utama - lihat renderApp().
 * Konsisten gaya dengan `.tabs`/`.tab` tapi lebih kecil (`.subtabs`/`.subtab`, lihat app.css).
 */
function renderChatSubTabs() {
  const bar = document.createElement("div");
  bar.className = "subtabs";
  const options = [{ id: FAMILY_CHAT_THREAD_ID, name: "Semua Anak" }, ...state.children];
  bar.innerHTML = options.map((o) => {
    const unread = state.chatUnreadByThread[o.id] || 0;
    const badge = unread ? `<span class="status-chip status-submitted badge">${unread}</span>` : "";
    return `<div class="subtab ${state.chatThreadId === o.id ? "active" : ""}" data-thread="${escapeHtml(o.id)}">${escapeHtml(o.name)}${badge}</div>`;
  }).join("");
  bar.querySelectorAll(".subtab").forEach((el) => {
    el.addEventListener("click", () => {
      state.chatThreadId = el.dataset.thread;
      state.chatMessages = [];
      state.chatReplyTarget = null;
      state.reactionPickerMessageId = null;
      render();
    });
  });
  return bar;
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
      <div class="stat-card stat-card-clickable" data-modal="approval"><div class="value">${approvalCount}</div><div class="label">Menunggu Approval</div></div>
      <div class="stat-card stat-card-clickable" data-modal="children"><div class="value">${state.children.length}</div><div class="label">Anak</div></div>
      <div class="stat-card stat-card-clickable" data-modal="locked"><div class="value">${lockedCount}</div><div class="label">Terkunci</div></div>
      <div class="stat-card stat-card-clickable" data-modal="tasks"><div class="value">${state.tasks.length}</div><div class="label">Total Tugas</div></div>
    </div>
    <button class="btn btn-primary" id="open-create-task" ${state.children.length === 0 ? "disabled" : ""}>+ Buat Tugas</button>
  `;
  el.querySelector("#open-create-task").addEventListener("click", () => { state.showCreateTask = true; render(); });
  // Tiap kartu ringkasan bisa diklik untuk pop-up review/follow-up cepat tanpa pindah tab dulu.
  el.querySelectorAll(".stat-card-clickable").forEach((card) => {
    card.addEventListener("click", () => { state.dashboardCardModal = card.dataset.modal; render(); });
  });

  if (state.children.length > 0) {
    el.appendChild(renderDashboardIncompleteTasks());
    el.appendChild(renderDashboardChatPreview());
  }
  return el;
}

/** "Tugas belum selesai" = semua status kecuali approved (assigned/submitted/rejected masih butuh tindakan lanjutan), dipisah per anak supaya orang tua langsung tahu siapa yang masih punya PR. */
function renderDashboardIncompleteTasks() {
  const card = document.createElement("div");
  card.className = "card";
  card.innerHTML = `<h3 style="margin-top: 0;">Tugas Belum Selesai</h3>`;

  state.children.forEach((child) => {
    const incomplete = state.tasks.filter((t) => t.childId === child.id && t.status !== "approved");
    const group = document.createElement("div");
    group.className = "dash-child-group";
    group.innerHTML = `<div class="dash-child-name">${escapeHtml(child.name)}${incomplete.length ? ` <span class="status-chip status-submitted">${incomplete.length}</span>` : ""}</div>`;

    if (incomplete.length === 0) {
      const hint = document.createElement("div");
      hint.className = "dash-task-mini-empty";
      hint.textContent = "Semua tugas sudah selesai.";
      group.appendChild(hint);
    } else {
      incomplete.forEach((task) => {
        const row = document.createElement("div");
        row.className = "dash-task-mini";
        row.innerHTML = `
          <span class="title">${escapeHtml(task.title)}</span>
          <span class="status-chip status-${task.status}">${STATUS_LABEL[task.status] || task.status}</span>
        `;
        row.addEventListener("click", () => { state.detailTaskId = task.id; render(); });
        group.appendChild(row);
      });
    }
    card.appendChild(group);
  });

  return card;
}

/** Pratinjau 4 pesan terakhir grup keluarga - klik kartu untuk langsung pindah ke tab Chat. */
function renderDashboardChatPreview() {
  const card = document.createElement("div");
  card.className = "card";
  card.innerHTML = `<h3 style="margin-top: 0;">Percakapan Grup Terakhir</h3>`;

  if (state.dashboardChatPreview.length === 0) {
    const hint = document.createElement("p");
    hint.style.color = "var(--text-muted)";
    hint.textContent = "Belum ada percakapan di grup keluarga.";
    card.appendChild(hint);
  } else {
    state.dashboardChatPreview.forEach((message) => {
      const row = document.createElement("div");
      row.className = "dash-chat-row";
      const senderLabel = message.senderId === state.user.id ? "Kamu" : (message.senderRole === "parent" ? "Orang Tua" : (childName(message.senderId) || "Anak"));
      const preview = message.type === "photo" ? "📷 Foto" : (message.text || "");
      row.innerHTML = `
        <span class="dash-chat-sender">${escapeHtml(senderLabel)}:</span>
        <span class="dash-chat-text">${escapeHtml(preview)}</span>
        <span class="dash-chat-time">${escapeHtml(formatChatTime(message.createdAt))}</span>
      `;
      card.appendChild(row);
    });
  }

  const openBtn = document.createElement("button");
  openBtn.type = "button";
  openBtn.className = "btn btn-text btn-sm";
  openBtn.style.marginTop = "8px";
  openBtn.textContent = "Buka Chat →";
  openBtn.addEventListener("click", () => { state.tab = "chat"; state.chatThreadId = FAMILY_CHAT_THREAD_ID; render(); });
  card.appendChild(openBtn);

  return card;
}

/**
 * Pop-up review/follow-up cepat saat salah satu kartu ringkasan Dashboard diklik - lihat
 * data-modal di renderDashboard(). "approval" pakai ulang approvalCard() supaya orang tua bisa
 * langsung setujui/tolak tanpa pindah tab; "locked" punya tombol buka kunci langsung di tempat.
 */
function renderDashboardCardModal() {
  const overlay = document.createElement("div");
  overlay.className = "modal-overlay";
  const modal = document.createElement("div");
  modal.className = "modal";
  overlay.appendChild(modal);
  overlay.addEventListener("click", (event) => { if (event.target === overlay) { state.dashboardCardModal = null; render(); } });

  const closeRow = document.createElement("div");
  closeRow.className = "modal-close-row";
  const closeBtn = document.createElement("button");
  closeBtn.type = "button";
  closeBtn.className = "btn btn-primary";
  closeBtn.textContent = "Tutup";
  closeBtn.addEventListener("click", () => { state.dashboardCardModal = null; render(); });
  closeRow.appendChild(closeBtn);

  if (state.dashboardCardModal === "approval") {
    const h2 = document.createElement("h2");
    h2.textContent = "Menunggu Approval";
    modal.appendChild(h2);
    const waiting = state.tasks.filter((t) => t.status === "submitted");
    if (waiting.length === 0) modal.appendChild(emptyHint("Tidak ada tugas yang menunggu approval."));
    else waiting.forEach((task) => modal.appendChild(approvalCard(task)));
  } else if (state.dashboardCardModal === "children") {
    const h2 = document.createElement("h2");
    h2.textContent = "Anak";
    modal.appendChild(h2);
    if (state.children.length === 0) modal.appendChild(emptyHint("Belum ada profil anak."));
    else state.children.forEach((child) => {
      const incomplete = state.tasks.filter((t) => t.childId === child.id && t.status !== "approved").length;
      const row = document.createElement("div");
      row.className = "settings-child-row";
      row.innerHTML = `
        <span>${escapeHtml(child.name)}</span>
        <span class="settings-child-actions">
          ${child.lockModeEnabled ? '<span class="status-chip status-rejected">Terkunci</span>' : ""}
          <span class="status-chip status-submitted">${incomplete} tugas tertunda</span>
        </span>
      `;
      modal.appendChild(row);
    });
  } else if (state.dashboardCardModal === "locked") {
    const h2 = document.createElement("h2");
    h2.textContent = "Perangkat Terkunci";
    modal.appendChild(h2);
    const locked = state.children.filter((c) => c.lockModeEnabled);
    if (locked.length === 0) modal.appendChild(emptyHint("Tidak ada perangkat yang terkunci saat ini."));
    else locked.forEach((child) => {
      const row = document.createElement("div");
      row.className = "settings-child-row";
      row.innerHTML = `<span>${escapeHtml(child.name)}</span>`;
      const unlockBtn = document.createElement("button");
      unlockBtn.type = "button";
      unlockBtn.className = "btn btn-outline btn-sm";
      unlockBtn.textContent = "Buka Kunci";
      unlockBtn.disabled = state.loading;
      unlockBtn.addEventListener("click", () => handleSetLock(child.id, false));
      row.appendChild(unlockBtn);
      modal.appendChild(row);
    });
  } else if (state.dashboardCardModal === "tasks") {
    const h2 = document.createElement("h2");
    h2.textContent = "Semua Tugas";
    modal.appendChild(h2);
    if (state.tasks.length === 0) modal.appendChild(emptyHint("Belum ada tugas."));
    else state.tasks.forEach((task) => {
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
      // Tutup pop-up ini SEKALIGUS buka detail tugas dalam satu render - bukan dua modal bertumpuk.
      row.addEventListener("click", () => { state.detailTaskId = task.id; state.dashboardCardModal = null; render(); });
      modal.appendChild(row);
    });
  }

  modal.appendChild(closeRow);
  return overlay;
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

// --- Kunci Perangkat ---------------------------------------------------------------------

function renderLockTab() {
  const el = document.createElement("div");
  if (state.children.length === 0) {
    el.appendChild(emptyHint("Belum ada profil anak."));
    return el;
  }
  const lockedCount = state.children.filter((c) => c.lockModeEnabled).length;
  const summary = document.createElement("p");
  summary.className = "lock-summary";
  summary.style.color = lockedCount > 0 ? "var(--error-text)" : "var(--text-muted)";
  summary.textContent = lockedCount === 0 ? "Tidak ada yang dikunci" : `${lockedCount} dari ${state.children.length} anak dikunci`;
  el.appendChild(summary);

  state.children.forEach((child) => {
    const row = document.createElement("div");
    row.className = "lock-row";
    row.innerHTML = `
      <span>${escapeHtml(child.name)}</span>
      <label class="switch">
        <input type="checkbox" ${child.lockModeEnabled ? "checked" : ""} ${state.loading ? "disabled" : ""} />
        <span class="switch-slider"></span>
      </label>
    `;
    row.querySelector("input").addEventListener("change", (event) => handleSetLock(child.id, event.target.checked));
    el.appendChild(row);
  });
  return el;
}

// --- Pengaturan ----------------------------------------------------------------------------

function renderSettingsTab() {
  const el = document.createElement("div");
  const card = document.createElement("div");
  card.className = "card";
  card.innerHTML = `
    <h3 style="margin-top: 0;">Profil Anak</h3>
    <p style="color: var(--text-muted); font-size: 13px; margin-top: -6px;">PIN anak disimpan terenkripsi dan tidak bisa ditampilkan ulang. Kalau anak lupa PIN, gunakan "Reset PIN" untuk membuat PIN baru.</p>
  `;

  if (state.children.length === 0) {
    const hint = document.createElement("p");
    hint.style.color = "var(--text-muted)";
    hint.textContent = "Belum ada profil anak.";
    card.appendChild(hint);
  } else {
    state.children.forEach((child) => {
      const row = document.createElement("div");
      row.className = "settings-child-row";
      row.innerHTML = `
        <span>${escapeHtml(child.name)}</span>
        <span class="settings-child-actions">
          <button type="button" class="btn btn-outline btn-sm" data-action="reset-pin">Reset PIN</button>
          <button type="button" class="btn btn-text btn-sm" data-action="delete">Hapus</button>
        </span>
      `;
      row.querySelector('[data-action="reset-pin"]').addEventListener("click", () => { state.childPendingResetPinId = child.id; render(); });
      row.querySelector('[data-action="delete"]').addEventListener("click", () => { state.childPendingDeleteId = child.id; render(); });
      card.appendChild(row);
    });
  }

  const addBtn = document.createElement("button");
  addBtn.type = "button";
  addBtn.className = "btn btn-outline btn-block";
  addBtn.style.marginTop = "14px";
  addBtn.textContent = "+ Tambah Anak";
  addBtn.addEventListener("click", () => { state.showAddChild = true; render(); });
  card.appendChild(addBtn);

  el.appendChild(card);
  el.appendChild(renderBackupCard());
  el.appendChild(renderActivityLogCard());
  return el;
}

/** Lihat handleDownloadBackup untuk alur lengkapnya (server mengenkripsi, browser langsung mengunduh). */
function renderBackupCard() {
  const card = document.createElement("div");
  card.className = "card";
  card.innerHTML = `
    <h3 style="margin-top: 0;">Cadangan Data</h3>
    <p style="color: var(--text-muted); font-size: 13px;">
      Unduh salinan data keluarga (profil anak, tugas, riwayat chat) sebagai berkas terenkripsi
      ke perangkat kamu. Kata sandinya kamu tentukan sendiri saat mengunduh - server TIDAK
      menyimpannya, jadi simpan baik-baik.
    </p>
    <button type="button" class="btn btn-outline" id="open-backup">Unduh Backup Terenkripsi</button>
  `;
  card.querySelector("#open-backup").addEventListener("click", () => { state.showBackupModal = true; render(); });
  return card;
}

function renderBackupModal() {
  const overlay = document.createElement("div");
  overlay.className = "modal-overlay";
  overlay.innerHTML = `
    <div class="modal">
      <h2>Unduh Backup Terenkripsi</h2>
      <p style="color: var(--text-muted);">Buat kata sandi backup (minimal 8 karakter). Kata sandi ini HARUS kamu ingat sendiri - dipakai lagi nanti untuk membuka berkas ini, server tidak menyimpannya sama sekali.</p>
      ${state.backupError ? `<div class="banner banner-error">${escapeHtml(state.backupError)}</div>` : ""}
      <form id="backup-form">
        <div class="field"><label>Kata sandi backup</label><input type="password" name="password" minlength="8" required autofocus /></div>
        <div class="field"><label>Ulangi kata sandi</label><input type="password" name="confirm" minlength="8" required /></div>
        <div class="modal-close-row">
          <button type="button" class="btn btn-text" id="cancel-backup">Batal</button>
          <button type="submit" class="btn btn-primary" ${state.backupSending ? "disabled" : ""}>Unduh Backup</button>
        </div>
      </form>
    </div>
  `;
  overlay.addEventListener("click", (event) => { if (event.target === overlay) { state.showBackupModal = false; state.backupError = null; render(); } });
  overlay.querySelector("#cancel-backup").addEventListener("click", () => { state.showBackupModal = false; state.backupError = null; render(); });
  overlay.querySelector("#backup-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    const password = String(form.get("password") || "");
    const confirm = String(form.get("confirm") || "");
    if (password !== confirm) { state.backupError = "Kata sandi tidak sama."; render(); return; }
    handleDownloadBackup(password);
  });
  return overlay;
}

/** Label Indonesia untuk tiap kode `action` dari GET /activity-log - server sengaja mengirim kode mentah (bukan teks siap-tampil), sama seperti STATUS_LABEL untuk status tugas. */
const ACTIVITY_ACTION_LABEL = {
  login: "Masuk ke akun",
  child_added: "Menambahkan profil anak",
  child_removed: "Menghapus profil anak",
  child_pin_reset: "Mengatur ulang PIN anak",
  device_locked: "Mengunci perangkat anak",
  device_unlocked: "Membuka kunci perangkat anak",
  task_created: "Membuat tugas baru",
  task_submitted: "Mengirim bukti tugas",
  task_approved: "Menyetujui tugas",
  task_rejected: "Menolak tugas",
  access_redeemed: "Menukar saldo menit jadi waktu akses",
  backup_created: "Mengunduh backup terenkripsi"
};

function renderActivityLogCard() {
  const card = document.createElement("div");
  card.className = "card";
  card.innerHTML = `<h3 style="margin-top: 0;">Log Aktivitas</h3>`;

  if (state.activityLog.length === 0) {
    const hint = document.createElement("p");
    hint.style.color = "var(--text-muted)";
    hint.textContent = "Belum ada aktivitas tercatat.";
    card.appendChild(hint);
  } else {
    const list = document.createElement("div");
    list.className = "activity-log-list";
    state.activityLog.forEach((entry) => {
      const row = document.createElement("div");
      row.className = "activity-log-row";
      const label = ACTIVITY_ACTION_LABEL[entry.action] || entry.action;
      const roleLabel = entry.actorRole === "parent" ? "Orang Tua" : "Anak";
      row.innerHTML = `
        <div class="activity-log-main">
          <span class="activity-log-actor">${escapeHtml(entry.actorName)}</span>
          <span class="activity-log-role">(${roleLabel})</span> ${escapeHtml(label)}
          ${entry.detail ? `<span class="activity-log-detail"> - ${escapeHtml(entry.detail)}</span>` : ""}
        </div>
        <div class="activity-log-time">${escapeHtml(formatActivityLogTime(entry.createdAt))}</div>
      `;
      list.appendChild(row);
    });
    card.appendChild(list);
  }

  return card;
}

function formatActivityLogTime(iso) {
  try {
    const d = new Date(iso);
    const pad = (n) => String(n).padStart(2, "0");
    return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  } catch {
    return "";
  }
}

function renderResetPinModal(child) {
  const overlay = document.createElement("div");
  overlay.className = "modal-overlay";
  overlay.innerHTML = `
    <div class="modal">
      <h2>Reset PIN ${escapeHtml(child.name)}</h2>
      <p style="color: var(--text-muted);">PIN lama langsung tidak berlaku begitu PIN baru disimpan. Beri tahu PIN baru ini ke ${escapeHtml(child.name)} secara langsung.</p>
      <form id="reset-pin-form">
        <div class="field"><label>PIN baru (4-8 digit)</label><input type="password" name="pin" inputmode="numeric" pattern="[0-9]{4,8}" required autofocus /></div>
        <div class="modal-close-row">
          <button type="button" class="btn btn-text" id="cancel-reset-pin">Batal</button>
          <button type="submit" class="btn btn-primary" ${state.loading ? "disabled" : ""}>Simpan PIN Baru</button>
        </div>
      </form>
    </div>
  `;
  overlay.addEventListener("click", (event) => { if (event.target === overlay) { state.childPendingResetPinId = null; render(); } });
  overlay.querySelector("#cancel-reset-pin").addEventListener("click", () => { state.childPendingResetPinId = null; render(); });
  overlay.querySelector("#reset-pin-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    const pin = String(form.get("pin") || "").replace(/\D/g, "").slice(0, 8);
    handleResetPin(child.id, pin);
  });
  return overlay;
}

function renderAddChildModal() {
  const overlay = document.createElement("div");
  overlay.className = "modal-overlay";
  overlay.innerHTML = `
    <div class="modal">
      <h2>Tambah profil anak</h2>
      <form id="add-child-form">
        <div class="field"><label>Nama anak</label><input type="text" name="name" required /></div>
        <div class="field"><label>PIN (4-8 digit)</label><input type="password" name="pin" inputmode="numeric" pattern="[0-9]{4,8}" required /></div>
        <div class="modal-close-row">
          <button type="button" class="btn btn-text" id="cancel-add-child">Batal</button>
          <button type="submit" class="btn btn-primary" ${state.loading ? "disabled" : ""}>Simpan</button>
        </div>
      </form>
    </div>
  `;
  overlay.addEventListener("click", (event) => { if (event.target === overlay) { state.showAddChild = false; render(); } });
  overlay.querySelector("#cancel-add-child").addEventListener("click", () => { state.showAddChild = false; render(); });
  overlay.querySelector("#add-child-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    const pin = String(form.get("pin") || "").replace(/\D/g, "").slice(0, 8);
    handleAddChild(form.get("name"), pin);
  });
  return overlay;
}

function renderDeleteChildModal(child) {
  const overlay = document.createElement("div");
  overlay.className = "modal-overlay";
  overlay.innerHTML = `
    <div class="modal">
      <h2>Hapus profil ${escapeHtml(child.name)}?</h2>
      <p>Semua tugas, riwayat chat, dan foto bukti miliknya akan ikut terhapus, dan perangkat anak ini akan otomatis keluar. Tindakan ini tidak bisa dibatalkan.</p>
      <div class="modal-close-row">
        <button type="button" class="btn btn-text" id="cancel-delete-child">Batal</button>
        <button type="button" class="btn btn-danger" id="confirm-delete-child" ${state.loading ? "disabled" : ""}>Hapus</button>
      </div>
    </div>
  `;
  overlay.addEventListener("click", (event) => { if (event.target === overlay) { state.childPendingDeleteId = null; render(); } });
  overlay.querySelector("#cancel-delete-child").addEventListener("click", () => { state.childPendingDeleteId = null; render(); });
  overlay.querySelector("#confirm-delete-child").addEventListener("click", () => handleDeleteChild(child.id));
  return overlay;
}

// --- Chat -----------------------------------------------------------------------------------

/**
 * Kalau anak lebih dari satu (atau bahkan satu), pemilih thread selalu tampil: "Semua Anak"
 * (grup bersama semua anggota keluarga) sebagai pilihan pertama, ditambah satu thread privat
 * per anak - konsisten dengan ParentChatTab di Android.
 */
function renderChatTab() {
  const el = document.createElement("div");
  el.className = "chat-wrap";

  if (state.children.length === 0) {
    el.appendChild(emptyHint("Belum ada profil anak untuk diajak chat."));
    return el;
  }

  // Pemilih thread sudah dipindah jadi baris sub-tab di bawah menu utama - lihat
  // renderChatSubTabs() (dipanggil dari renderApp()), bukan dropdown di sini lagi.

  if (state.chatError) el.appendChild(banner("error", state.chatError, () => { state.chatError = null; render(); }));

  const messagesEl = document.createElement("div");
  messagesEl.className = "chat-messages";
  messagesEl.id = "chat-messages";
  el.appendChild(messagesEl);
  renderChatMessages(messagesEl);

  if (state.chatReplyTarget) {
    const strip = document.createElement("div");
    strip.className = "chat-reply-strip";
    strip.innerHTML = `
      <div class="chat-reply-strip-text"><strong>${escapeHtml(state.chatReplyTarget.senderLabel)}</strong>: ${escapeHtml(state.chatReplyTarget.preview)}</div>
      <button type="button" class="chat-reply-strip-close">&times;</button>
    `;
    strip.querySelector(".chat-reply-strip-close").addEventListener("click", () => { state.chatReplyTarget = null; render(); });
    el.appendChild(strip);
  }

  const form = document.createElement("form");
  form.className = "chat-composer";
  form.innerHTML = `
    <label class="btn btn-outline btn-sm chat-photo-btn" title="Kirim foto">
      Foto
      <input type="file" accept="image/jpeg,image/png" id="chat-photo-input" hidden />
    </label>
    <input type="text" id="chat-text-input" placeholder="Tulis pesan..." autocomplete="off" ${state.chatSending ? "disabled" : ""} />
    <button type="submit" class="btn btn-primary btn-sm" ${state.chatSending ? "disabled" : ""}>Kirim</button>
  `;
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const input = form.querySelector("#chat-text-input");
    const text = input.value;
    input.value = "";
    handleSendChatText(text);
  });
  form.querySelector("#chat-photo-input").addEventListener("change", (event) => {
    const file = event.target.files[0];
    event.target.value = "";
    if (file) handleSendChatPhoto(file);
  });
  el.appendChild(form);

  return el;
}

/** Dipanggil terpisah dari renderChatTab supaya bisa dipakai ulang tiap poll pesan baru (lihat ensureChatPolling). */
function renderChatMessages(container) {
  container.innerHTML = "";
  if (state.chatMessages.length === 0) {
    container.appendChild(emptyHint("Belum ada percakapan. Kirim pesan pertama!"));
    return;
  }
  state.chatMessages.forEach((message) => container.appendChild(chatBubble(message)));
  container.scrollTop = container.scrollHeight;
}

/** Label pengirim singkat dipakai berulang (quote balasan, pratinjau balas) - "Kamu" untuk pesan sendiri. */
function senderLabelFor(message) {
  if (message.senderId === state.user.id) return "Kamu";
  return message.senderRole === "parent" ? "Orang Tua" : (childName(message.senderId) || "Anak");
}

function chatBubble(message) {
  const isMine = message.senderId === state.user.id;
  const wrap = document.createElement("div");
  wrap.className = `chat-row ${isMine ? "mine" : ""}`;

  const bubble = document.createElement("div");
  bubble.className = "chat-bubble";

  if (!isMine) {
    const label = document.createElement("div");
    label.className = "chat-sender";
    label.textContent = senderLabelFor(message);
    bubble.appendChild(label);
  }

  // Kutipan pesan yang dibalas (kalau ada & masih ada dalam riwayat yang sudah dimuat) - lihat
  // replyToId di server.js. Diam-diam dilewati kalau target tidak ditemukan (mis. di luar 200
  // pesan terakhir), bukan error - kutipan cuma pemanis, bukan data penting.
  if (message.replyToId) {
    const target = state.chatMessages.find((m) => m.id === message.replyToId);
    if (target) {
      const quote = document.createElement("div");
      quote.className = "chat-quote";
      const preview = target.type === "photo" ? "📷 Foto" : (target.text || "");
      quote.innerHTML = `<span class="chat-quote-sender">${escapeHtml(senderLabelFor(target))}</span><span class="chat-quote-text">${escapeHtml(preview)}</span>`;
      bubble.appendChild(quote);
    }
  }

  if (message.type === "photo") {
    const slot = document.createElement("div");
    slot.className = "chat-photo-slot";
    slot.textContent = "Memuat...";
    bubble.appendChild(slot);
    apiBytes(`/chat/${message.childId}/messages/${message.id}/photo`)
      .then((blob) => {
        const url = URL.createObjectURL(blob);
        slot.innerHTML = `<img src="${url}" alt="Foto chat" />`;
        slot.addEventListener("click", () => showImagePreview(url));
      })
      .catch(() => { slot.textContent = "Foto tidak tersedia"; });
  } else {
    const text = document.createElement("div");
    text.textContent = message.text || "";
    bubble.appendChild(text);
  }

  const time = document.createElement("div");
  time.className = "chat-time";
  time.textContent = formatChatTime(message.createdAt);
  bubble.appendChild(time);

  // Reaksi yang sudah ada, dikelompokkan per emoji - klik pill = toggle reaksi SAYA dengan
  // emoji itu (jalan pintas, sama efeknya dengan pilih dari popover di bawah).
  if (message.reactions && message.reactions.length > 0) {
    const grouped = {};
    message.reactions.forEach((r) => { (grouped[r.emoji] = grouped[r.emoji] || []).push(r.userId); });
    const pills = document.createElement("div");
    pills.className = "chat-reactions";
    Object.entries(grouped).forEach(([emoji, userIds]) => {
      const pill = document.createElement("button");
      pill.type = "button";
      pill.className = `chat-reaction-pill ${userIds.includes(state.user.id) ? "mine" : ""}`;
      pill.textContent = `${emoji} ${userIds.length}`;
      pill.addEventListener("click", () => handleReact(message.childId, message.id, emoji));
      pills.appendChild(pill);
    });
    bubble.appendChild(pills);
  }

  const actionsRow = document.createElement("div");
  actionsRow.className = "chat-bubble-actions";
  actionsRow.innerHTML = `
    <button type="button" class="chat-action-btn" data-action="reply" title="Balas">↩ Balas</button>
    <button type="button" class="chat-action-btn" data-action="react" title="Beri reaksi">🙂 Reaksi</button>
  `;
  actionsRow.querySelector('[data-action="reply"]').addEventListener("click", () => {
    const preview = message.type === "photo" ? "📷 Foto" : (message.text || "");
    state.chatReplyTarget = { id: message.id, senderLabel: senderLabelFor(message), preview };
    state.reactionPickerMessageId = null;
    render();
    document.getElementById("chat-text-input")?.focus();
  });
  actionsRow.querySelector('[data-action="react"]').addEventListener("click", () => {
    state.reactionPickerMessageId = state.reactionPickerMessageId === message.id ? null : message.id;
    render();
  });
  bubble.appendChild(actionsRow);

  if (state.reactionPickerMessageId === message.id) {
    const picker = document.createElement("div");
    picker.className = "chat-reaction-picker";
    CHAT_REACTION_EMOJI.forEach((emoji) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.textContent = emoji;
      btn.addEventListener("click", () => handleReact(message.childId, message.id, emoji));
      picker.appendChild(btn);
    });
    bubble.appendChild(picker);
  }

  wrap.appendChild(bubble);
  return wrap;
}

function formatChatTime(iso) {
  try {
    const d = new Date(iso);
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  } catch {
    return "";
  }
}

// --- Mulai ------------------------------------------------------------------------------

render();
if (state.token && state.user) {
  guarded(refreshAll);
}
