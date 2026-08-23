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
