# Deploy Pactio API ke VPS Ubuntu

Panduan ini memasang API secara aman di balik Nginx dengan HTTPS. Contoh domain menggunakan `api.contoh-domain-anda.com`; ganti dengan domain Anda sendiri.

## 1. Siapkan VPS

SSH ke VPS, lalu pasang Docker dan Nginx:

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin nginx certbot python3-certbot-nginx
sudo systemctl enable --now docker nginx
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
```

Di panel DNS domain, buat rekam `A` untuk `api` yang mengarah ke IP VPS. Tunggu hingga DNS aktif.

## 2. Salin dan jalankan aplikasi

Dari komputer Anda (PowerShell), kirim folder backend yang sudah diekstrak:

```powershell
scp -r .\KendaliKeluargaBackend pengguna-vps@IP_VPS:/home/pengguna-vps/
```

Kembali ke SSH VPS:

```bash
cd ~/KendaliKeluargaBackend
sudo docker compose up -d --build
sudo docker compose ps
```

API hanya dibuka ke localhost (`127.0.0.1:3030`), sehingga tidak dapat diakses langsung dari internet.

## 3. Pasang Nginx dan HTTPS

Buat konfigurasi berikut dengan `sudo nano /etc/nginx/sites-available/kendali-keluarga`:

```nginx
server {
    listen 80;
    server_name api.contoh-domain-anda.com;

    location / {
        proxy_pass http://127.0.0.1:3030;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 5m;
    }
}
```

Aktifkan lalu buat sertifikat HTTPS:

```bash
sudo ln -s /etc/nginx/sites-available/kendali-keluarga /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
sudo certbot --nginx -d api.contoh-domain-anda.com
```

Periksa hasilnya:

```bash
curl https://api.contoh-domain-anda.com/health
```

Respons yang diharapkan: `{"ok":true}`.

## 4. Cadangan data

Database MVP disimpan pada volume Docker bernama `kendalikeluargabackend_keluarga_data`. Buat cadangan berkala sebelum pembaruan:

```bash
sudo docker run --rm -v kendalikeluargabackend_keluarga_data:/data -v "$PWD":/backup alpine tar czf /backup/kendali-backup-$(date +%F).tar.gz /data
```

Simpan berkas backup di lokasi lain yang aman.

## 5. Mengaktifkan Login Google (opsional)

Endpoint `POST /auth/google-parent` butuh **Client ID OAuth tipe "Web application"** dari
Google Cloud Console (bukan tipe "Android" — itu dipakai di sisi aplikasi, bukan server).
Client ID ini bukan rahasia, tapi tetap disetel lewat env var, bukan ditulis di kode.

Di VPS, di folder yang sama dengan `docker-compose.yml`, buat file `.env` (jangan commit
ke git):

```bash
echo "GOOGLE_WEB_CLIENT_ID=xxxxxxxxxx-xxxxx.apps.googleusercontent.com" > .env
```

Lalu update kode server (`git pull` atau `scp` ulang `server.js`) dan jalankan ulang:

```bash
sudo docker compose up -d --build
curl -s -X POST https://api.contoh-domain-anda.com/auth/google-parent \
  -H 'Content-Type: application/json' -d '{"idToken":"tes"}'
```

Respons `{"error":"Token Google tidak dapat dibaca."}` berarti endpoint sudah aktif dan
env var terbaca (token uji di atas memang bukan token Google asli). Kalau responsnya
`{"error":"Login Google belum dikonfigurasi di server."}`, berarti `.env` belum terbaca —
pastikan filenya ada di folder yang sama saat menjalankan `docker compose`.

## 6. Update kode (setelah ada perbaikan/fitur baru)

**PENTING:** folder build context di VPS (`~/KendaliKeluargaBackend/`, sejajar dengan
`docker-compose.yml`) berisi salinan LANGSUNG `server.js`/`package.json`/`web/` di root-nya -
BUKAN di dalam subfolder apa pun. Kalau di VPS ada juga folder `~/KendaliKeluargaBackend/pactio/`
(clone git untuk `git pull`), itu HANYA sumber, bukan yang benar-benar dipakai `docker compose
build` (lihat `build: .` di `docker-compose.yml` dan `COPY server.js ./` di `Dockerfile` - keduanya
merujuk ke root folder ini, bukan ke `pactio/backend/` atau subfolder lain).

```bash
cd ~/KendaliKeluargaBackend/pactio
git pull

cd ~/KendaliKeluargaBackend
cp pactio/backend/server.js ./server.js
cp pactio/backend/package.json ./package.json
rm -rf ./web && cp -r pactio/backend/web ./web

sudo docker compose up -d --build
```

Verifikasi cepat bahwa image benar-benar pakai kode baru (ganti `NAMA_FUNGSI_BARU` dengan sesuatu
yang pasti ada di commit terbaru):

```bash
sudo docker compose exec api grep -c NAMA_FUNGSI_BARU server.js
```

## Penting sebelum penggunaan nyata

Ini masih MVP dan belum layak menyimpan data anak di internet. Jangan gunakan untuk pengguna nyata sebelum token kedaluwarsa, rate limit, reset kredensial, audit log, database produksi, kebijakan privasi, penghapusan data, dan pengamanan unggah foto diterapkan.
