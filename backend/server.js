const http = require("http");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const PORT = Number(process.env.PORT || 3030);
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, "data.json");
const sessions = new Map();

function initialData() {
  return { families: [], users: [], tasks: [] };
}

function loadData() {
  if (!fs.existsSync(DATA_FILE)) return initialData();
  return JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
}

let db = loadData();
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
  return { id: user.id, role: user.role, name: user.name, familyId: user.familyId };
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
  if (!session || (allowedRoles && !allowedRoles.includes(session.user.role))) {
    send(res, 401, { error: "Autentikasi atau peran tidak diizinkan." });
    return null;
  }
  return session.user;
}

function createSession(user) {
  const token = crypto.randomBytes(32).toString("base64url");
  sessions.set(token, { user, createdAt: Date.now() });
  return token;
}

function familyFor(user) {
  return db.families.find((family) => family.id === user.familyId);
}

function taskForUser(task, user) {
  return task.familyId === user.familyId && (user.role === "parent" || task.childId === user.id);
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
    return send(res, 201, { token: createSession(publicUser(user)), user: publicUser(user), family: { id: family.id, name: family.name, code: family.code } });
  }

  if (req.method === "POST" && pathname === "/auth/login-parent") {
    const body = await bodyOf(req);
    const email = requireText(body.email, "Email").toLowerCase();
    const password = requireText(body.password, "Kata sandi");
    const user = db.users.find((item) => item.role === "parent" && item.email === email);
    if (!user || !verify(password, user.passwordHash)) return send(res, 401, { error: "Email atau kata sandi salah." });
    return send(res, 200, { token: createSession(publicUser(user)), user: publicUser(user) });
  }

  if (req.method === "POST" && pathname === "/auth/login-child") {
    const body = await bodyOf(req);
    const code = requireText(body.familyCode, "Kode keluarga").toUpperCase();
    const pin = requireText(body.pin, "PIN", 4);
    const family = db.families.find((item) => item.code === code);
    const user = family && db.users.find((item) => item.role === "child" && item.familyId === family.id && verify(pin, item.pinHash));
    if (!user) return send(res, 401, { error: "Kode keluarga atau PIN salah." });
    return send(res, 200, { token: createSession(publicUser(user)), user: publicUser(user) });
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
    task.status = "submitted"; task.evidence = String(body.evidence || "").trim(); task.submittedAt = new Date().toISOString(); save();
    return send(res, 200, { task });
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
    const approved = db.tasks.filter((task) => task.childId === child.id && task.status === "approved");
    const minutes = approved.reduce((total, task) => total + task.rewardMinutes, 0);
    return send(res, 200, { minutes, approvedTaskCount: approved.length });
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
