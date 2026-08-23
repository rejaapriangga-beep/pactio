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

## Penting sebelum penggunaan nyata

Ini masih MVP dan belum layak menyimpan data anak di internet. Jangan gunakan untuk pengguna nyata sebelum token kedaluwarsa, rate limit, reset kredensial, audit log, database produksi, kebijakan privasi, penghapusan data, dan pengamanan unggah foto diterapkan.
