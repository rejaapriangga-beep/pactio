const http = require("http");
const https = require("https");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const PORT = Number(process.env.PORT || 3030);
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, "data.json");
// Foto bukti tugas disimpan sebagai file biasa (bukan di data.json) supaya JSON db tidak
// membengkak. Ditaruh di folder yang sama dengan DATA_FILE, jadi otomatis ikut ke volume
// Docker yang sama (lihat docker-compose.yml) tanpa perlu konfigurasi tambahan.
const PHOTOS_DIR = path.join(path.dirname(DATA_FILE), "photos");
fs.mkdirSync(PHOTOS_DIR, { recursive: true });
const MAX_PHOTO_BYTES = 5 * 1024 * 1024; // 5MB
// Client ID OAuth tipe "Web application" dari Google Cloud Console. Ini BUKAN rahasia
// (Client ID memang publik, sama seperti yang dibundel di aplikasi Android) — dipakai
// di sini hanya untuk memeriksa klaim "aud" pada token ID Google. Kalau kosong, endpoint
// login Google akan menolak dengan pesan jelas, bukan diam-diam gagal.
const GOOGLE_WEB_CLIENT_ID = process.env.GOOGLE_WEB_CLIENT_ID || "";
// sessions: Map token -> { token, userId, createdAt } untuk lookup cepat saat runtime.
// Sumber kebenarannya tetap db.sessions (ikut tersimpan ke DATA_FILE) supaya token yang
// sudah diberikan ke perangkat (orang tua maupun anak) TIDAK hilang begitu proses backend
// di-restart (deploy ulang, VPS reboot, dsb) - sebelum ini semua sesi hanya ada di memori,
// jadi setiap restart backend memaksa semua orang login ulang (termasuk anak input ulang
// kode keluarga + PIN), padahal dari sisi perangkat mereka tidak pernah "logout".
const sessions = new Map();

function initialData() {
  return { families: [], users: [], tasks: [], sessions: [] };
}

function loadData() {
  if (!fs.existsSync(DATA_FILE)) return initialData();
  const loaded = JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
  if (!Array.isArray(loaded.sessions)) loaded.sessions = [];
  return loaded;
}

let db = loadData();
for (const record of db.sessions) sessions.set(record.token, record);

function save() {
  fs.writeFileSync(DATA_FILE, JSON.stringify(db, null, 2));
}

function id(prefix) {
  return `${prefix}_${crypto.randomUUID()}`;
}

function hash(secret, salt = crypto.randomBytes(16).toString("hex")) {
  const value = crypto.scryptSync(secret, salt, 64).toString("hex");
  return `${salt}:${value}`;
}

function verify(secret, stored) {
  const [salt, expected] = stored.split(":");
  const actual = crypto.scryptSync(secret, salt, 64).toString("hex");
  return crypto.timingSafeEqual(Buffer.from(actual, "hex"), Buffer.from(expected, "hex"));
}

function publicUser(user) {
  return {
    id: user.id,
    role: user.role,
    name: user.name,
    familyId: user.familyId,
    // Hanya relevan untuk anak - field ini undefined (dihilangkan JSON.stringify) untuk orang tua.
    lockModeEnabled: user.role === "child" ? Boolean(user.lockModeEnabled) : undefined
  };
}

function send(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(body));
}

async function bodyOf(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  if (!chunks.length) return {};
  try { return JSON.parse(Buffer.concat(chunks).toString("utf8")); }
  catch { throw new Error("JSON tidak valid."); }
}

function requireText(value, label, min = 1) {
  if (typeof value !== "string" || value.trim().length < min) throw new Error(`${label} wajib diisi.`);
  return value.trim();
}

function auth(req, res, allowedRoles) {
  const value = req.headers.authorization || "";
  const token = value.startsWith("Bearer ") ? value.slice(7) : "";
  const session = sessions.get(token);
  // Selalu ambil user TERBARU dari db.users lewat userId (bukan snapshot lama yang
  // disimpan di sesi) - supaya perubahan seperti lockModeEnabled langsung kelihatan tanpa
  // perlu login ulang, dan supaya sesi yang dipulihkan dari DATA_FILE saat restart tetap
  // sinkron kalau datanya pernah diedit manual.
  const user = session && db.users.find((item) => item.id === session.userId);
  if (!user || (allowedRoles && !allowedRoles.includes(user.role))) {
    send(res, 401, { error: "Autentikasi atau peran tidak diizinkan." });
    return null;
  }
  return user;
}

function createSession(user) {
  const token = crypto.randomBytes(32).toString("base64url");
  const record = { token, userId: user.id, createdAt: Date.now() };
  sessions.set(token, record);
  db.sessions.push(record);
  save();
  return token;
}

function familyFor(user) {
  return db.families.find((family) => family.id === user.familyId);
}

// Saldo yang BISA DIPAKAI = total menit dari tugas yang sudah disetujui, dikurangi menit
// yang sudah pernah "dipakai" (redeemedMinutesTotal - lihat POST /access-balance/redeem).
// Sengaja dihitung ulang tiap kali (bukan disimpan sebagai satu angka "saldo") supaya
// tidak pernah lepas sinkron dari daftar tugas yang jadi sumber kebenarannya.
function accessBalanceFor(child) {
  const approved = db.tasks.filter((task) => task.childId === child.id && task.status === "approved");
  const totalEarned = approved.reduce((total, task) => total + task.rewardMinutes, 0);
  const minutes = Math.max(0, totalEarned - (child.redeemedMinutesTotal || 0));
  return { minutes, approvedTaskCount: approved.length };
}

// --- Verifikasi Google Sign-In (Credential Manager di Android) ---------------------
// Diimplementasikan manual dengan modul bawaan Node (https + crypto), TANPA dependency
// npm eksternal (google-auth-library dsb.) — konsisten dengan aturan proyek ini.

const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
let jwksCache = { keys: [], fetchedAt: 0 };

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    https.get(url, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => {
        try { resolve(JSON.parse(Buffer.concat(chunks).toString("utf8"))); }
        catch (error) { reject(error); }
      });
    }).on("error", reject);
  });
}

async function googleJwks(forceRefresh = false) {
  const oneHour = 60 * 60 * 1000;
  if (!forceRefresh && jwksCache.keys.length && Date.now() - jwksCache.fetchedAt < oneHour) return jwksCache.keys;
  const data = await fetchJson(GOOGLE_JWKS_URL);
  jwksCache = { keys: data.keys || [], fetchedAt: Date.now() };
  return jwksCache.keys;
}

function base64UrlDecode(value) {
  return Buffer.from(value.replace(/-/g, "+").replace(/_/g, "/"), "base64");
}

// Memverifikasi token ID Google: tanda tangan RS256 lewat kunci publik JWKS Google,
// lalu klaim issuer/audience/kedaluwarsa/email. Melempar Error dengan pesan yang aman
// ditampilkan ke pengguna kalau ada yang tidak valid.
async function verifyGoogleIdToken(idToken) {
  if (!GOOGLE_WEB_CLIENT_ID) throw new Error("Login Google belum dikonfigurasi di server.");
  const parts = String(idToken || "").split(".");
  if (parts.length !== 3) throw new Error("Token Google tidak valid.");
  const [headerPart, payloadPart, signaturePart] = parts;

  let header, payload;
  try {
    header = JSON.parse(base64UrlDecode(headerPart).toString("utf8"));
    payload = JSON.parse(base64UrlDecode(payloadPart).toString("utf8"));
  } catch {
    throw new Error("Token Google tidak dapat dibaca.");
  }

  let keys = await googleJwks();
  let jwk = keys.find((key) => key.kid === header.kid);
  if (!jwk) {
    // Google merotasi kunci secara berkala; kalau kid tidak ditemukan, coba sekali lagi
    // dengan memaksa refresh sebelum menyerah.
    keys = await googleJwks(true);
    jwk = keys.find((key) => key.kid === header.kid);
  }
  if (!jwk) throw new Error("Kunci verifikasi Google tidak ditemukan.");

  const publicKey = crypto.createPublicKey({ key: jwk, format: "jwk" });
  const verifier = crypto.createVerify("RSA-SHA256");
  verifier.update(`${headerPart}.${payloadPart}`);
  const signatureValid = verifier.verify(publicKey, base64UrlDecode(signaturePart));
  if (!signatureValid) throw new Error("Tanda tangan token Google tidak valid.");

  if (payload.iss !== "https://accounts.google.com" && payload.iss !== "accounts.google.com") {
    throw new Error("Token Google berasal dari sumber yang tidak dikenali.");
  }
  if (payload.aud !== GOOGLE_WEB_CLIENT_ID) throw new Error("Token Google bukan untuk aplikasi ini.");
  if (!payload.exp || Date.now() / 1000 > payload.exp) throw new Error("Token Google sudah kedaluwarsa, coba masuk lagi.");
  if (payload.email_verified === false) throw new Error("Email akun Google ini belum terverifikasi.");
  if (!payload.email || !payload.sub) throw new Error("Token Google tidak memuat email atau ID pengguna.");

  return payload;
}

function taskForUser(task, user) {
  return task.familyId === user.familyId && (user.role === "parent" || task.childId === user.id);
}

const JPEG_MAGIC = Buffer.from([0xff, 0xd8, 0xff]);
const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

// Menerima foto bukti sebagai data URI base64 (bukan multipart) - paling sederhana dengan
// http bawaan Node tanpa dependency parsing multipart eksternal. Isi file diverifikasi lewat
// magic bytes, BUKAN cuma percaya field mime dari klien, supaya tidak bisa dipakai menyimpan
// file sembarangan mengaku sebagai foto.
function validatePhoto(dataUri) {
  const match = /^data:(image\/jpeg|image\/png);base64,([A-Za-z0-9+/=]+)$/.exec(String(dataUri || ""));
  if (!match) throw new Error("Format foto tidak valid (harus JPEG atau PNG).");
  const [, mime, base64] = match;
  const buffer = Buffer.from(base64, "base64");
  if (buffer.length === 0 || buffer.length > MAX_PHOTO_BYTES) throw new Error("Ukuran foto tidak valid (maksimal 5MB).");
  const magicOk = mime === "image/jpeg"
    ? buffer.subarray(0, 3).equals(JPEG_MAGIC)
    : buffer.subarray(0, 8).equals(PNG_MAGIC);
  if (!magicOk) throw new Error("Isi file tidak cocok dengan tipe foto yang dinyatakan.");
  return { buffer, mime, ext: mime === "image/jpeg" ? "jpg" : "png" };
}

function photoPath(task, ext) {
  return path.join(PHOTOS_DIR, `${task.id}.${ext}`);
}

function savePhoto(task, dataUri) {
  const photo = validatePhoto(dataUri);
  // Hapus foto lama kalau ekstensinya beda (mis. resubmit dari JPEG ke PNG) supaya tidak menumpuk file yatim.
  for (const ext of ["jpg", "png"]) {
    const oldPath = photoPath(task, ext);
    if (ext !== photo.ext && fs.existsSync(oldPath)) fs.unlinkSync(oldPath);
  }
  fs.writeFileSync(photoPath(task, photo.ext), photo.buffer);
  task.evidencePhotoType = photo.mime;
}

async function route(req, res) {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const pathname = url.pathname;

  if (req.method === "GET" && pathname === "/health") return send(res, 200, { ok: true });

  if (req.method === "POST" && pathname === "/auth/register-parent") {
    const body = await bodyOf(req);
    const familyName = requireText(body.familyName, "Nama keluarga");
    const name = requireText(body.name, "Nama orang tua");
    const email = requireText(body.email, "Email").toLowerCase();
    const password = requireText(body.password, "Kata sandi", 8);
    if (db.users.some((user) => user.email === email)) return send(res, 409, { error: "Email sudah terdaftar." });
    const family = { id: id("family"), name: familyName, code: crypto.randomBytes(3).toString("hex").toUpperCase() };
    const user = { id: id("user"), familyId: family.id, role: "parent", name, email, passwordHash: hash(password) };
    db.families.push(family); db.users.push(user); save();
    return send(res, 201, { token: createSession(user), user: publicUser(user), family: { id: family.id, name: family.name, code: family.code } });
  }

  if (req.method === "POST" && pathname === "/auth/login-parent") {
    const body = await bodyOf(req);
    const email = requireText(body.email, "Email").toLowerCase();
    const password = requireText(body.password, "Kata sandi");
    const user = db.users.find((item) => item.role === "parent" && item.email === email);
    // user.passwordHash bisa kosong kalau akun ini dibuat lewat Google Sign-In (belum pernah
    // set kata sandi) — jangan sampai verify() dipanggil dengan stored=undefined dan crash.
    if (!user || !user.passwordHash || !verify(password, user.passwordHash)) {
      return send(res, 401, { error: "Email atau kata sandi salah." });
    }
    return send(res, 200, { token: createSession(user), user: publicUser(user) });
  }

  if (req.method === "POST" && pathname === "/auth/google-parent") {
    const body = await bodyOf(req);
    const idToken = requireText(body.idToken, "Token Google");
    let payload;
    try {
      payload = await verifyGoogleIdToken(idToken);
    } catch (error) {
      const notConfigured = error.message.includes("belum dikonfigurasi");
      return send(res, notConfigured ? 500 : 401, { error: error.message });
    }
    const email = String(payload.email).toLowerCase();
    const googleId = payload.sub;

    let user = db.users.find((item) => item.role === "parent" && item.googleId === googleId);
    if (!user) user = db.users.find((item) => item.role === "parent" && item.email === email);

    if (user) {
      // Akun email/password yang sama sudah ada -> tautkan googleId supaya login berikutnya lebih cepat.
      if (!user.googleId) { user.googleId = googleId; save(); }
      return send(res, 200, { token: createSession(user), user: publicUser(user) });
    }

    // Belum ada akun sama sekali untuk akun Google ini -> buat keluarga baru otomatis.
    // Nama keluarga sengaja diturunkan dari nama profil Google (bukan diminta lewat form
    // tambahan) supaya alur Google Sign-In tetap satu langkah; bisa diubah lagi nanti kalau
    // fitur ubah nama keluarga sudah ada.
    const displayName = requireText(payload.name || payload.given_name || payload.email, "Nama orang tua");
    const familyName = requireText(body.familyName || `Keluarga ${payload.given_name || displayName}`, "Nama keluarga");
    const family = { id: id("family"), name: familyName, code: crypto.randomBytes(3).toString("hex").toUpperCase() };
    const newUser = { id: id("user"), familyId: family.id, role: "parent", name: displayName, email, googleId, passwordHash: null };
    db.families.push(family); db.users.push(newUser); save();
    return send(res, 201, {
      token: createSession(newUser),
      user: publicUser(newUser),
      family: { id: family.id, name: family.name, code: family.code }
    });
  }

  if (req.method === "POST" && pathname === "/auth/login-child") {
    const body = await bodyOf(req);
    const code = requireText(body.familyCode, "Kode keluarga").toUpperCase();
    const pin = requireText(body.pin, "PIN", 4);
    const family = db.families.find((item) => item.code === code);
    const user = family && db.users.find((item) => item.role === "child" && item.familyId === family.id && verify(pin, item.pinHash));
    if (!user) return send(res, 401, { error: "Kode keluarga atau PIN salah." });
    return send(res, 200, { token: createSession(user), user: publicUser(user) });
  }

  if (req.method === "POST" && pathname === "/family/children") {
    const parent = auth(req, res, ["parent"]); if (!parent) return;
    const body = await bodyOf(req);
    const name = requireText(body.name, "Nama anak");
    const pin = requireText(body.pin, "PIN", 4);
    if (!/^\d{4,8}$/.test(pin)) return send(res, 400, { error: "PIN harus 4–8 digit angka." });
    const child = { id: id("user"), familyId: parent.familyId, role: "child", name, pinHash: hash(pin) };
    db.users.push(child); save();
    return send(res, 201, { child: publicUser(child), familyCode: familyFor(parent).code });
  }

  // Hapus profil anak dari Pengaturan orang tua - dibersihkan menyeluruh (bukan cuma
  // ditandai nonaktif): tugas & foto bukti miliknya ikut terhapus supaya tidak ada data
  // yatim yang menunjuk ke user yang sudah tidak ada, dan semua sesi login anak ini
  // langsung dicabut (kalau HP-nya masih tersimpan token lama, langsung ditolak begitu
  // dipakai lagi).
  const childDeleteMatch = pathname.match(/^\/family\/children\/([^/]+)$/);
  if (req.method === "DELETE" && childDeleteMatch) {
    const parent = auth(req, res, ["parent"]); if (!parent) return;
    const child = db.users.find((item) => item.id === childDeleteMatch[1] && item.familyId === parent.familyId && item.role === "child");
    if (!child) return send(res, 404, { error: "Profil anak tidak ditemukan." });

    for (const task of db.tasks.filter((item) => item.childId === child.id)) {
      for (const ext of ["jpg", "png"]) {
        const filePath = photoPath(task, ext);
        if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
      }
    }
    db.tasks = db.tasks.filter((item) => item.childId !== child.id);
    db.users = db.users.filter((item) => item.id !== child.id);
    for (const [token, record] of sessions) {
      if (record.userId === child.id) sessions.delete(token);
    }
    db.sessions = db.sessions.filter((record) => record.userId !== child.id);
    save();
    return send(res, 200, { ok: true });
  }

  const lockMatch = pathname.match(/^\/children\/([^/]+)\/lock$/);
  if (req.method === "POST" && lockMatch) {
    const parent = auth(req, res, ["parent"]); if (!parent) return;
    const child = db.users.find((item) => item.id === lockMatch[1] && item.familyId === parent.familyId && item.role === "child");
    if (!child) return send(res, 404, { error: "Profil anak tidak ditemukan." });
    const body = await bodyOf(req);
    if (typeof body.enabled !== "boolean") return send(res, 400, { error: "enabled harus bernilai true atau false." });
    child.lockModeEnabled = body.enabled; save();
    return send(res, 200, { child: publicUser(child) });
  }

  // Dipoll berkala oleh perangkat anak (bukan orang tua) untuk tahu apakah harus mengunci
  // layar sekarang. Sengaja endpoint ringan terpisah dari /family supaya bisa dipanggil
  // sering tanpa membebani query lain.
  //
  // unlockUntil (lihat POST /access-balance/redeem): kalau masih di masa depan, kunci
  // dianggap TIDAK aktif walau lockModeEnabled=true - ini "waktu akses" yang dibeli anak
  // pakai saldo menit hadiahnya. Waktu berjalan terus (wall-clock), tidak peduli anak
  // benar-benar memakai HP atau tidak - sesuai keputusan produk (sederhana & bisa diprediksi,
  // bukan pelacakan pemakaian per aplikasi).
  if (req.method === "GET" && pathname === "/lock-status") {
    const child = auth(req, res, ["child"]); if (!child) return;
    const unlockUntil = child.unlockUntil || 0;
    const unlockActive = unlockUntil > Date.now();
    return send(res, 200, { enabled: Boolean(child.lockModeEnabled) && !unlockActive, unlockUntil });
  }

  // Dipanggil dari perangkat anak sebelum mengizinkan tombol "Keluar" (logout) - supaya
  // anak tidak bisa keluar dari akunnya sendiri (mis. untuk lolos dari Mode Kunci) tanpa
  // sepengetahuan orang tua. Sengaja cocokkan kata sandi ke SEMUA akun orang tua di
  // keluarga yang sama (bukan minta email tertentu) - anak tidak perlu tahu email orang
  // tua mana yang dipakai, cukup kata sandinya. Kalau semua orang tua di keluarga ini
  // masuk lewat Google (belum pernah set kata sandi), endpoint ini akan selalu menolak -
  // itu batasan yang jujur ditampilkan ke pengguna, bukan celah keamanan.
  if (req.method === "POST" && pathname === "/children/verify-parent-password") {
    const child = auth(req, res, ["child"]); if (!child) return;
    const body = await bodyOf(req);
    const password = requireText(body.password, "Kata sandi orang tua");
    const parents = db.users.filter((item) => item.role === "parent" && item.familyId === child.familyId);
    if (!parents.some((item) => item.passwordHash)) {
      return send(res, 400, { error: "Orang tua di keluarga ini belum mengatur kata sandi (masuk lewat Google). Minta orang tua yang keluarkan langsung dari HP ini." });
    }
    const matches = parents.some((item) => item.passwordHash && verify(password, item.passwordHash));
    // Sengaja 403 (bukan 401) - token sesi anak sendiri tetap valid di sini, cuma kata
    // sandi orang tua yang ditolak. Kalau dijawab 401, klien Android akan menganggap
    // SESI ANAK sendiri yang kedaluwarsa dan memaksa logout otomatis - keliru total.
    if (!matches) return send(res, 403, { error: "Kata sandi orang tua salah." });
    return send(res, 200, { ok: true });
  }

  if (req.method === "GET" && pathname === "/family") {
    const user = auth(req, res); if (!user) return;
    const family = familyFor(user);
    const children = db.users.filter((item) => item.familyId === user.familyId && item.role === "child").map(publicUser);
    return send(res, 200, { family: { id: family.id, name: family.name, code: user.role === "parent" ? family.code : undefined }, children });
  }

  if (pathname === "/tasks" && req.method === "GET") {
    const user = auth(req, res); if (!user) return;
    return send(res, 200, { tasks: db.tasks.filter((task) => taskForUser(task, user)) });
  }

  if (pathname === "/tasks" && req.method === "POST") {
    const parent = auth(req, res, ["parent"]); if (!parent) return;
    const body = await bodyOf(req);
    const childId = requireText(body.childId, "ID anak");
    const title = requireText(body.title, "Judul tugas");
    const rewardMinutes = Number(body.rewardMinutes);
    const child = db.users.find((item) => item.id === childId && item.familyId === parent.familyId && item.role === "child");
    if (!child) return send(res, 404, { error: "Profil anak tidak ditemukan." });
    if (!Number.isInteger(rewardMinutes) || rewardMinutes < 1 || rewardMinutes > 240) return send(res, 400, { error: "Hadiah harus 1–240 menit." });
    const task = { id: id("task"), familyId: parent.familyId, childId, title, description: String(body.description || "").trim(), rewardMinutes, status: "assigned", createdAt: new Date().toISOString(), evidence: null };
    db.tasks.push(task); save();
    return send(res, 201, { task });
  }

  const submitMatch = pathname.match(/^\/tasks\/([^/]+)\/submit$/);
  if (req.method === "POST" && submitMatch) {
    const child = auth(req, res, ["child"]); if (!child) return;
    const task = db.tasks.find((item) => item.id === submitMatch[1] && taskForUser(item, child));
    if (!task) return send(res, 404, { error: "Tugas tidak ditemukan." });
    if (task.status !== "assigned" && task.status !== "rejected") return send(res, 409, { error: "Tugas tidak dapat dikirim pada status ini." });
    const body = await bodyOf(req);
    if (body.evidencePhoto) {
      try { savePhoto(task, body.evidencePhoto); }
      catch (error) { return send(res, 400, { error: error.message }); }
    }
    task.status = "submitted"; task.evidence = String(body.evidence || "").trim(); task.submittedAt = new Date().toISOString(); save();
    return send(res, 200, { task });
  }

  const photoMatch = pathname.match(/^\/tasks\/([^/]+)\/photo$/);
  if (req.method === "GET" && photoMatch) {
    const user = auth(req, res); if (!user) return;
    const task = db.tasks.find((item) => item.id === photoMatch[1] && taskForUser(item, user));
    if (!task || !task.evidencePhotoType) return send(res, 404, { error: "Foto tidak ditemukan." });
    const ext = task.evidencePhotoType === "image/jpeg" ? "jpg" : "png";
    const filePath = photoPath(task, ext);
    if (!fs.existsSync(filePath)) return send(res, 404, { error: "Foto tidak ditemukan." });
    const buffer = fs.readFileSync(filePath);
    res.writeHead(200, { "Content-Type": task.evidencePhotoType, "Content-Length": buffer.length, "Cache-Control": "private, max-age=86400" });
    return res.end(buffer);
  }

  const decisionMatch = pathname.match(/^\/tasks\/([^/]+)\/decision$/);
  if (req.method === "POST" && decisionMatch) {
    const parent = auth(req, res, ["parent"]); if (!parent) return;
    const task = db.tasks.find((item) => item.id === decisionMatch[1] && item.familyId === parent.familyId);
    if (!task) return send(res, 404, { error: "Tugas tidak ditemukan." });
    if (task.status !== "submitted") return send(res, 409, { error: "Hanya tugas terkirim yang dapat diputuskan." });
    const body = await bodyOf(req);
    if (typeof body.approved !== "boolean") return send(res, 400, { error: "approved harus bernilai true atau false." });
    task.status = body.approved ? "approved" : "rejected"; task.decisionNote = String(body.note || "").trim(); task.decidedAt = new Date().toISOString(); save();
    return send(res, 200, { task });
  }

  if (req.method === "GET" && pathname === "/access-balance") {
    const child = auth(req, res, ["child"]); if (!child) return;
    return send(res, 200, { ...accessBalanceFor(child), unlockUntil: child.unlockUntil || 0 });
  }

  // Anak menekan tombol "Gunakan Waktu" di halaman utama - menukar sebagian/seluruh saldo
  // menit hadiah menjadi jendela waktu Mode Kunci nonaktif. Kalau masih ada sisa waktu aktif
  // dari penukaran sebelumnya, waktu baru DITAMBAHKAN di belakangnya (bukan menimpa) - jadi
  // anak bisa menukar sedikit-sedikit tanpa kehilangan sisa waktu yang sedang berjalan.
  if (req.method === "POST" && pathname === "/access-balance/redeem") {
    const child = auth(req, res, ["child"]); if (!child) return;
    const body = await bodyOf(req);
    const minutes = Number(body.minutes);
    if (!Number.isInteger(minutes) || minutes < 1) return send(res, 400, { error: "Jumlah menit harus bilangan bulat positif." });
    const balance = accessBalanceFor(child);
    if (minutes > balance.minutes) return send(res, 400, { error: `Saldo tidak cukup (tersisa ${balance.minutes} menit).` });
    const now = Date.now();
    const base = Math.max(now, child.unlockUntil || 0);
    child.redeemedMinutesTotal = (child.redeemedMinutesTotal || 0) + minutes;
    child.unlockUntil = base + minutes * 60 * 1000;
    save();
    return send(res, 200, { ...accessBalanceFor(child), unlockUntil: child.unlockUntil });
  }

  send(res, 404, { error: "Endpoint tidak ditemukan." });
}

const server = http.createServer((req, res) => route(req, res).catch((error) => {
  const status = error.message.includes("wajib") || error.message.includes("JSON") ? 400 : 500;
  if (status === 500) console.error(error);
  send(res, status, { error: status === 500 ? "Kesalahan server." : error.message });
}));

if (require.main === module) server.listen(PORT, () => console.log(`Pactio API berjalan di http://localhost:${PORT}`));
module.exports = { server };
