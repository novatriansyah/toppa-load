# ADR-0005: Arsitektur UI Alat Kerja Padat (React 19, Vite, Radix Headless, Token Kustom, Lazy Chunks)

- **Status:** Accepted *(Pilihan library spesifik ditandai sebagai Keputusan Default Builder, diverifikasi pada Spike Minggu 2)*
- **Tanggal:** 2026-11-01
- **Pengambil Keputusan:** Rencana v1.3 + Keputusan Default Builder

## Konteks
Rencana Bagian 4 menekankan bahwa Toppa Load harus terasa seperti **alat kerja presisi** (setara spreadsheet cepat / alat perencanaan profesional), bukan template dashboard generik penuh kartu KPI dekoratif, gradien ungu, dan ruang kosong berlebih. Selain itu, library visual berat (kalender, Gantt, grafik laporan) tidak boleh memperlambat pemuatan layar utama **Heatmap Orang × Minggu** yang ditargetkan tampil `< 1 detik`.

## Keputusan
1. **Core Frontend:** Gunakan **React 19 + TypeScript + Vite** dengan **React Router** (setiap view dan filter utama tersimpan di URL query string agar dapat dibagikan langsung).
2. **State & Tabel Data:**
   - **TanStack Query v5** untuk sinkronisasi server state dengan `refetchInterval: 30_000` (polling ringan 30 detik pada layar Heatmap untuk 6 pengguna, tanpa SSE/WebSocket).
   - **TanStack Table v8** untuk grid padat Proyek, Task, Alokasi, dan Timesheet dengan navigasi keyboard.
3. **Komponen Headless & Sistem Token Kustom:**
   - Gunakan primitif aksesibel **Radix UI** (`Dialog`/Drawer, `Popover`, `Select`, `Tooltip`, `DropdownMenu`) yang diberi gaya menggunakan **Tailwind CSS v4 + CSS Variables Token Kustom**:
     - Skala spasi ketat kelipatan `4px`, radius tajam `4px` (`--radius-sm`) dan `6px` (`--radius-md`), pemisah garis `1px` (`--border-subtle`), tanpa *heavy drop-shadow*.
     - Tipografi `Plus Jakarta Sans` / `Inter` dengan utilitas wajib `.num-tabular { font-variant-numeric: tabular-nums; text-align: right; }` untuk semua sel jam dan persentase.
     - Pola status tiga lapis (warna + teks persentase + ikon/arsir CSS untuk `OVERLOADED`) demi kepatuhan WCAG AA dan pengguna buta warna.
4. **Code-Splitting Ketat & Kandidat Spike Minggu 2 *(Keputusan Default Builder)*:**
   - Layar utama **Heatmap Orang × Minggu** dibangun sebagai komponen grid HTML/CSS native ringan (0 dependency grafik berat) agar *First Contentful Paint* `< 1 detik`.
   - **`@dnd-kit/core`** digunakan sebagai kandidat utama *drag-and-drop* rebalancing (diuji pada *spike* 1 hari Minggu 2).
   - **`FullCalendar`** (Fase 2 Kalender) dan **`Apache ECharts`** (Fase 3 Laporan) dimuat secara eksklusif via `React.lazy()` pada rutenya masing-masing.
5. **Lokalisasi Sejak Hari Pertama:** Gunakan **`i18next` + `react-i18next`** dengan kamus utama `web/src/i18n/locales/id.json` (memakai kosakata asli tim hasil wawancara Minggu 2) dan kerangka `en.json` untuk Fase 3.

## Alternatif yang Dipertimbangkan
- **UI Kit Siap Pakai Default (MUI / Ant Design / Default shadcn tanpa rombak token):** Cepat dirakit, namun langsung terlihat generik atau membawa ukuran bundle awal yang berat.
- **Library Gantt Enterprise Berbayar:** Mahal dan berlebihan untuk 6 pengguna dibanding komponen timeline CSS Grid / SVG terfokus.

## Konsekuensi & Trade-offs
- **Positif:** Bundle awal layar Heatmap sangat kecil dan instan, identitas visual tajam serta fungsional untuk portfolio maupun pemakaian harian, dan siap dwibahasa tanpa merombak komponen di Fase 3.
- **Negatif:** Membutuhkan disiplin menjaga konsistensi token CSS kustom di awal Minggu 1–2 (diselesaikan dengan mendefinisikan `tokens.css` sekali di fondasi Minggu 1).
