# Daftar Cek Setup VPS & Coolify (Minggu 1) — Toppa Load

Panduan eksekusi infrastruktur Minggu 1 agar satu VPS **2 vCPU / 4 GB RAM** siap menjalankan `staging`, `production`, PostgreSQL, Uptime Kuma, dan backup S3 dengan aman.

---

## 1. Provisioning & Hardening VPS (Ubuntu 24.04 LTS)

- [ ] Pilih VPS minimal **2 vCPU, 4 GB RAM, 60+ GB NVMe** di region **Singapura** atau **Jakarta**; aktifkan fitur **Automated Weekly Snapshot** dari panel penyedia VPS.
- [ ] Tambahkan SSH Public Key (`ed25519`) Anda; login dan buat user sudo non-root ( atau pastikan root hanya mengizinkan `prohibit-password`).
- [ ] Matikan login password SSH di `/etc/ssh/sshd_config`:
  - `PasswordAuthentication no`
  - `KbdInteractiveAuthentication no`
  - `systemctl reload ssh`
- [ ] Aktifkan firewall `ufw`:
  ```bash
  ufw default deny incoming
  ufw default allow outgoing
  ufw limit 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
  # Jangan buka port 8000 Coolify ke publik setelah domain HTTPS Coolify aktif!
  ufw enable
  ```
- [ ] Pasang `fail2ban` dan `unattended-upgrades`:
  ```bash
  apt update && apt install -y fail2ban unattended-upgrades
  dpkg-reconfigure -plow unattended-upgrades
  ```
- [ ] Tambahkan *swapfile* 2 GB sebagai sabuk pengaman saat proses `docker build` berjalan di VPS:
  ```bash
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
  ```

---

## 2. Instalasi & Pengamanan Coolify

- [ ] Pasang Coolify resmi:
  ```bash
  curl -fsSL https://cdn.coollabs.io/coolify/install.sh | bash
  ```
- [ ] Arahkan DNS `A Record` subdomain panel (mis. `ops.<domain-anda>`) ke IP VPS.
- [ ] Masuk ke Coolify, atur **Instance Domain** ke `https://ops.<domain-anda>`, aktifkan **2FA (TOTP)** pada akun Admin Coolify, lalu tutup port `8000` langsung bila sebelumnya sempat dibuka sementara.
- [ ] *(Disarankan)* Batasi akses `ops.<domain-anda>` melalui allowlist IP / Tailscale / Cloudflare Zero Trust Access.

---

## 3. Penyimpanan S3 & Database PostgreSQL + Backup Harian

- [ ] Buat bucket privat di **Cloudflare R2** (atau Backblaze B2), mis. `toppa-load-db-backups`, beserta Access Key & Secret Key khusus bucket tersebut.
- [ ] Di Coolify -> **S3 Storages** -> tambahkan koneksi S3 dan pastikan tombol **Validate Connection** sukses.
- [ ] Buat Project **`Toppa Load`** dengan 2 Environment: `staging` dan `production`.
- [ ] Di `staging`: tambahkan Resource **PostgreSQL 17** (`toppaload_staging`, tidak diekspos ke port publik).
- [ ] Di `production`: tambahkan Resource **PostgreSQL 17** (`toppaload_prod`, tidak diekspos ke port publik) -> buka tab **Scheduled Backups**:
  -Jadwal cron: `30 18 * * *` (`01:30 WIB` setiap malam)
  - Simpan ke S3 (`toppa-load-db-backups`), retensi lokal 7 hari, retensi S3 30 hari.
- [ ] **Wajib Minggu 1:** Klik **Backup Now**, unduh/ambil hasil backup dari S3, dan uji restore ke `toppaload_staging` sesuai [`docs/runbook.md`](../docs/runbook.md).

---

## 4. Aplikasi Toppa Load & Webhook CI/CD

- [ ] Arahkan DNS `A Record`:
  - `staging-workload.<domain-anda>` -> IP VPS
  - `workload.<domain-anda>` -> IP VPS
- [ ] Di `staging` & `production`, buat Resource **Application** dari repositori GitHub `toppa-load`:
  - **Build Pack:** `Dockerfile`
  - **Dockerfile Location:** `/deploy/Dockerfile`
  - **Port Exposes:** `8080`
  - **Health Check:** aktifkan `GET /actuator/health` (interval `30s`, start period `40s`)
- [ ] Salin variabel dari [`deploy/.env.example`](.env.example) ke tab **Environment Variables** masing-masing environment (`staging` memakai DB staging & kredensial uji; `production` memakai DB prod).
- [ ] Matikan *Auto-Deploy on Push* langsung dari Git webhook mentah; sebagai gantinya panggil **Coolify Deploy Webhook URL** dari step terakhir GitHub Actions `.github/workflows/ci.yml` **hanya setelah seluruh test lulus** (otomatis untuk `staging`, manual/workflow_dispatch untuk `production`).

---

## 5. Monitoring Ringan

- [ ] Tambahkan Resource *One-Click Service* **Uptime Kuma** di Coolify (`status.<domain-anda>`), batasi memori kontainer `256m`.
- [ ] Tambahkan 2 monitor HTTP(s) ke `https://workload.<domain-anda>/actuator/health` dan `https://staging-workload.<domain-anda>/actuator/health` (interval 60 detik) dengan notifikasi ke email/Telegram Anda.
