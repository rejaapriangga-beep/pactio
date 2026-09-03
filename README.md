# Pactio

Aplikasi parental-control berbasis tugas. Orang tua memberi tugas → anak mengerjakan &
mengirim bukti → orang tua menyetujui/menolak → anak memperoleh hadiah berupa menit akses
perangkat/aplikasi. Kontrol perangkat sungguhan (Android/iOS/Huawei) direncanakan pada
tahap berikutnya, memakai mekanisme resmi tiap platform (mis. Family Controls/Screen Time
di iOS) — **tidak ada** pengawasan tersembunyi, spyware, bypass permission, accessibility
abuse, atau root exploit.

## Struktur repo

```
pactio/
├── android/           # Aplikasi Android — Kotlin, Jetpack Compose, Material 3
├── backend/           # API Node.js (server.js), tanpa dependency eksternal
└── BUILD_ANDROID.md   # Cara build APK debug + langkah uji di HP
```

## Status saat ini

| Bagian | Status |
|---|---|
| Backend (`server.js`) | Berjalan & teruji (`npm test` lulus), sudah live di `https://api.patio.my.id` |
| Android — auth, family, tasks, approval, balance | Terhubung ke backend nyata, token tersimpan aman (Android Keystore) |
| Android — build APK | ✅ **Berhasil** — CI GitHub Actions membangun `app-debug.apk` otomatis setiap push (lihat `BUILD_ANDROID.md`) |
| Kontrol perangkat nyata | **Belum diimplementasikan** — sesuai PRD, menunggu alur Task→Approval→Reward stabil terlebih dahulu |

## Menjalankan backend secara lokal

```bash
cd backend
node server.js        # atau: npm test
```

Server berjalan di `http://localhost:3030`. Untuk deploy ke VPS Ubuntu + Docker, lihat
`backend/DEPLOY-UBUNTU.md`.

## Menjalankan Android

Lihat [`BUILD_ANDROID.md`](BUILD_ANDROID.md).

## Aturan proyek (ringkas)

- Backend tanpa dependency eksternal, storage JSON lokal — belum layak produksi penuh
  (token in-memory tanpa expiry; lihat `backend/README.md`).
- Android tidak pernah menyimpan token/kata sandi/PIN sebagai teks biasa, dan tidak pernah
  mencetaknya ke log.
- API produksi wajib HTTPS (`https://api.patio.my.id`) — tidak ada cleartext HTTP.
- Tidak ada kontrol perangkat agresif sampai tahap ini secara eksplisit direncanakan dan
  disetujui.


<!-- Security scan triggered at 2026-08-31 17:17:37 -->

<!-- Security scan triggered at 2026-08-31 16:54:30 -->

<!-- Security scan triggered at 2026-08-31 18:33:42 -->

<!-- Security scan triggered at 2026-09-02 06:51:50 -->

<!-- Security scan triggered at 2026-09-02 14:38:00 -->

<!-- Security scan triggered at 2026-09-03 22:11:10 -->