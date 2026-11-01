# ADR-0004: Autentikasi OIDC (Google / Microsoft) dengan Email Allowlist Ketat dan Cookie Sesi

- **Status:** Accepted *(Google OIDC sebagai default aktif + Microsoft siap via env var ditandai sebagai Keputusan Default Builder)*
- **Tanggal:** 2026-11-01
- **Pengambil Keputusan:** Rencana v1.3 + Keputusan Default Builder

## Konteks
Aplikasi ini mengelola data beban kerja dan cuti karyawan internal dan di-host di VPS yang terhubung ke internet publik. Sistem tidak boleh mengizinkan pendaftaran mandiri (*self-signup*) dari akun Google/Microsoft sembarang, dan tidak membutuhkan kerumitan manajemen token JWT *stateless* + *refresh token rotation* di `localStorage` yang rentan XSS ketika frontend dan backend berada dalam satu origin yang sama (ADR-0003).

## Keputusan
1. Gunakan **Spring Security OAuth2 / OIDC Client** (`spring-boot-starter-oauth2-client` / `spring-security-oauth2-client`) dengan alur standar *Authorization Code*.
2. Sediakan dukungan konfigurasi bawaan untuk **Google OIDC** (default) dan **Microsoft Entra ID** melalui variabel environment tanpa perlu mengubah kode Java.
3. Terapkan **`AllowlistOidcUserService`** saat callback login:
   - Email terverifikasi dari IdP dicocokkan secara *case-insensitive* ke tabel `users` (`WHERE LOWER(email) = LOWER(:email) AND active = true AND deleted_at IS NULL`).
   - Jika email tidak terdaftar atau `active = false`, autentikasi langsung ditolak dengan halaman penolakan yang jelas (tanpa membuat baris user baru).
   - Satu akun bootstrap admin (`APP_SECURITY_BOOTSTRAP_ADMIN_EMAIL`) dipastikan ada dan aktif saat aplikasi start agar admin pertama tidak pernah terkunci.
4. Gunakan **Sesi Server Berbasis Cookie `HttpOnly`, `Secure` (di HTTPS), `SameSite=Lax`** dengan proteksi **CSRF Cookie-to-Header (`CookieCsrfTokenRepository` + `SpaCsrfTokenRequestHandler`)** standar Spring Security 7 / Boot 4.1.
5. Wajibkan `@EnableMethodSecurity` dengan `@PreAuthorize` pada setiap controller/service mutasi (`ADMIN`, `SUPERVISOR`, `MEMBER`).

## Alternatif yang Dipertimbangkan
- **Username + Password Lokal (BCrypt):** Menambah beban reset password, kebijakan kompleksitas sandi, dan risiko kebocoran kredensial bagi 6 pengguna yang sudah memiliki akun email kerja.
- **Stateless JWT di Browser Storage:** Rawan pencurian token lewat XSS dan menyulitkan pencabutan akses seketika saat anggota tim dinonaktifkan (`active = false`).

## Konsekuensi & Trade-offs
- **Positif:** Keamanan tinggi dari hari pertama (memanfaatkan 2FA bawaan Google/Microsoft kantor), pencabutan akses instan, bebas risiko token bocor di `localStorage`.
- **Negatif:** Saat aplikasi tunggal direstart untuk deploy (±5–10 detik), sesi di memori JVM dapat meminta klik satu tombol *"Masuk kembali dengan SSO"* (dapat ditambah Spring Session JDBC di tabel Postgres bila dirasa mengganggu, namun untuk 6 pengguna sangat jarang diperlukan).

