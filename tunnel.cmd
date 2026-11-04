@echo off
echo =========================================================
echo  Toppa Load - Cloudflare Quick Tunnel (Port 5173)
echo =========================================================
echo Pastikan Backend (8080) dan Frontend (5173) sudah berjalan.
echo Menghubungkan tunnel ke http://localhost:5173 ...
echo.
"%~dp0cloudflared.exe" tunnel --url http://localhost:5173
