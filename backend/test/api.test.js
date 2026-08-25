const test = require("node:test");
const assert = require("node:assert/strict");
const { server } = require("../server");

let baseUrl;
test.before(async () => {
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});
test.after(() => server.close());

async function request(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, { headers: { "Content-Type": "application/json", ...options.headers }, ...options });
  return { status: response.status, body: await response.json() };
}

test("orang tua dapat memberi hadiah akses setelah tugas disetujui", async () => {
  const suffix = Date.now();
  const registered = await request("/auth/register-parent", { method: "POST", body: JSON.stringify({ familyName: "Keluarga Uji", name: "Ibu", email: `ibu${suffix}@contoh.id`, password: "rahasia-aman" }) });
  assert.equal(registered.status, 201);
  const parentAuth = { Authorization: `Bearer ${registered.body.token}` };
  const child = await request("/family/children", { method: "POST", headers: parentAuth, body: JSON.stringify({ name: "Budi", pin: "1234" }) });
  const task = await request("/tasks", { method: "POST", headers: parentAuth, body: JSON.stringify({ childId: child.body.child.id, title: "Belajar", rewardMinutes: 20 }) });
  const childLogin = await request("/auth/login-child", { method: "POST", body: JSON.stringify({ familyCode: child.body.familyCode, pin: "1234" }) });
  const childAuth = { Authorization: `Bearer ${childLogin.body.token}` };
  await request(`/tasks/${task.body.task.id}/submit`, { method: "POST", headers: childAuth, body: JSON.stringify({ evidence: "Sudah selesai" }) });
  const decision = await request(`/tasks/${task.body.task.id}/decision`, { method: "POST", headers: parentAuth, body: JSON.stringify({ approved: true }) });
  assert.equal(decision.body.task.status, "approved");
  const balance = await request("/access-balance", { headers: childAuth });
  assert.equal(balance.body.minutes, 20);
});

test("chat grup keluarga (thread \"family\") tidak bocor ke keluarga lain", async () => {
  const suffix = Date.now();
  const familyA = await request("/auth/register-parent", { method: "POST", body: JSON.stringify({ familyName: "Keluarga A", name: "Ayah A", email: `ayahA${suffix}@contoh.id`, password: "rahasia-aman" }) });
  const familyB = await request("/auth/register-parent", { method: "POST", body: JSON.stringify({ familyName: "Keluarga B", name: "Ayah B", email: `ayahB${suffix}@contoh.id`, password: "rahasia-aman" }) });
  const authA = { Authorization: `Bearer ${familyA.body.token}` };
  const authB = { Authorization: `Bearer ${familyB.body.token}` };

  const sent = await request("/chat/family/messages", { method: "POST", headers: authA, body: JSON.stringify({ type: "text", text: "Pesan rahasia keluarga A" }) });
  assert.equal(sent.status, 201);

  // Keluarga B TIDAK boleh melihat pesan grup keluarga A di thread "family" miliknya sendiri.
  const seenByB = await request("/chat/family/messages", { headers: authB });
  assert.equal(seenByB.status, 200);
  assert.deepEqual(seenByB.body.messages, []);

  // Keluarga A tetap melihat pesannya sendiri seperti biasa.
  const seenByA = await request("/chat/family/messages", { headers: authA });
  assert.equal(seenByA.body.messages.length, 1);
  assert.equal(seenByA.body.messages[0].text, "Pesan rahasia keluarga A");
  // Kontrak API klien tidak berubah - childId yang dikembalikan tetap sentinel "family".
  assert.equal(seenByA.body.messages[0].childId, "family");
});
