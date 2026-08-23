# Pactio API — MVP Lokal

Backend tanpa dependensi eksternal untuk aplikasi Pactio. API ini menyimpan data pada berkas JSON lokal dan ditujukan untuk demonstrasi/pengembangan awal, bukan produksi.

## Menjalankan

Memerlukan Node.js 18+.

```powershell
node server.js
```

Server tersedia pada `http://localhost:3030`. Berkas data `data.json` dibuat otomatis saat API pertama kali menerima perubahan.

Untuk menjalankannya di VPS Ubuntu dengan Docker, lihat [DEPLOY-UBUNTU.md](DEPLOY-UBUNTU.md).

## Alur uji cepat

1. Daftarkan akun orang tua dengan `POST /auth/register-parent`.
2. Salin `token` dari respons dan gunakan sebagai `Authorization: Bearer <token>`.
3. Buat profil anak lewat `POST /family/children`.
4. Buat tugas lewat `POST /tasks`.
5. Anak masuk lewat `POST /auth/login-child` menggunakan kode keluarga dan PIN.
6. Anak mengirim tugas lewat `POST /tasks/:id/submit`.
7. Orang tua menyetujui lewat `POST /tasks/:id/decision` dengan `{"approved":true}`.

## Endpoint

| Metode | URL | Peran | Fungsi |
|---|---|---|---|
| POST | `/auth/register-parent` | Publik | Membuat keluarga dan orang tua |
| POST | `/auth/login-parent` | Publik | Masuk sebagai orang tua |
| POST | `/auth/login-child` | Publik | Masuk sebagai anak memakai kode keluarga + PIN |
| POST | `/family/children` | Orang tua | Membuat profil anak |
| GET | `/family` | Semua | Membaca keluarga aktif |
| GET/POST | `/tasks` | Semua/Orang tua | Melihat atau membuat tugas |
| POST | `/tasks/:id/submit` | Anak | Mengirim tugas selesai dan opsional bukti teks |
| POST | `/tasks/:id/decision` | Orang tua | Menyetujui/menolak tugas |
| GET | `/access-balance` | Anak | Melihat total hadiah akses yang disetujui |

## Keamanan sebelum produksi

Versi ini meng-hash kata sandi dan PIN dengan `scrypt`, tetapi token masih berada di memori dan database masih berupa file lokal. Sebelum dipublikasikan, pindahkan data ke database terenkripsi, gunakan token kedaluwarsa/refresh token, HTTPS, rate limiting, unggah bukti yang aman, pemeriksaan otorisasi, audit log, serta kebijakan privasi anak yang sesuai wilayah operasi.
