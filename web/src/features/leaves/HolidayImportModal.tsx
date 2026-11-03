import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient, HttpError } from '../../shared/api/httpClient';

export interface HolidayImportModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const INDONESIA_2026_HOLIDAYS = [
  { holidayDate: '2026-01-01', name: 'Tahun Baru 2026 Masehi', scope: 'NATIONAL' },
  { holidayDate: '2026-01-16', name: 'Isra Mikraj Nabi Muhammad SAW', scope: 'NATIONAL' },
  { holidayDate: '2026-02-17', name: 'Tahun Baru Imlek 2577 Kongzili', scope: 'NATIONAL' },
  { holidayDate: '2026-03-21', name: 'Hari Suci Nyepi Tahun Baru Saka 1948', scope: 'NATIONAL' },
  { holidayDate: '2026-03-20', name: 'Hari Raya Idul Fitri 1447 Hijriah (Hari 1)', scope: 'NATIONAL' },
  { holidayDate: '2026-03-21', name: 'Hari Raya Idul Fitri 1447 Hijriah (Hari 2)', scope: 'NATIONAL' },
  { holidayDate: '2026-04-03', name: 'Wafat Yesus Kristus', scope: 'NATIONAL' },
  { holidayDate: '2026-04-05', name: 'Hari Paskah', scope: 'NATIONAL' },
  { holidayDate: '2026-05-01', name: 'Hari Buruh Internasional', scope: 'NATIONAL' },
  { holidayDate: '2026-05-14', name: 'Kenaikan Yesus Kristus', scope: 'NATIONAL' },
  { holidayDate: '2026-05-31', name: 'Hari Raya Waisak 2570 BE', scope: 'NATIONAL' },
  { holidayDate: '2026-06-01', name: 'Hari Lahir Pancasila', scope: 'NATIONAL' },
  { holidayDate: '2026-06-27', name: 'Hari Raya Idul Adha 1447 Hijriah', scope: 'NATIONAL' },
  { holidayDate: '2026-07-16', name: 'Tahun Baru Islam 1448 Hijriah', scope: 'NATIONAL' },
  { holidayDate: '2026-08-17', name: 'Hari Kemerdekaan Republik Indonesia Ke-81', scope: 'NATIONAL' },
  { holidayDate: '2026-09-25', name: 'Maulid Nabi Muhammad SAW', scope: 'NATIONAL' },
  { holidayDate: '2026-12-25', name: 'Hari Raya Natal', scope: 'NATIONAL' },
];

export const HolidayImportModal: React.FC<HolidayImportModalProps> = ({ isOpen, onClose }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<'preset' | 'csv' | 'json'>('preset');
  const [csvContent, setCsvContent] = useState('');
  const [jsonContent, setJsonContent] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const importMutation = useMutation({
    mutationFn: async (payload: { type: 'json' | 'csv'; data: unknown }) => {
      if (payload.type === 'csv') {
        return apiClient.post('/api/v1/holidays/import', payload.data, {
          headers: { 'Content-Type': 'text/csv' },
        });
      }
      return apiClient.post('/api/v1/holidays/import', payload.data);
    },
    onSuccess: (res: any) => {
      queryClient.invalidateQueries({ queryKey: ['holidays'] });
      queryClient.invalidateQueries({ queryKey: ['capacity'] });
      setSuccessMessage(
        t(
          'leaves.importSuccess',
          `Berhasil memproses hari libur! (${res?.importedCount || 0} baru, ${
            res?.updatedCount || 0
          } diperbarui)`
        )
      );
      setErrorMessage('');
      setTimeout(() => {
        onClose();
        setSuccessMessage('');
      }, 1500);
    },
    onError: (err: unknown) => {
      if (err instanceof HttpError) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage(t('leaves.importFailed', 'Gagal mengimpor hari libur. Periksa format teks.'));
      }
    },
  });

  if (!isOpen) return null;

  const handleImportPreset = () => {
    importMutation.mutate({ type: 'json', data: INDONESIA_2026_HOLIDAYS });
  };

  const handleImportCsv = () => {
    if (!csvContent.trim()) {
      setErrorMessage(t('leaves.emptyCsv', 'Konten CSV tidak boleh kosong.'));
      return;
    }
    importMutation.mutate({ type: 'csv', data: csvContent.trim() });
  };

  const handleImportJson = () => {
    try {
      const parsed = JSON.parse(jsonContent);
      if (!Array.isArray(parsed)) {
        throw new Error('JSON harus berupa array list hari libur.');
      }
      importMutation.mutate({ type: 'json', data: parsed });
    } catch (e: any) {
      setErrorMessage(`Format JSON tidak valid: ${e.message}`);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="w-full max-w-lg bg-white rounded-lg shadow-xl border border-slate-200 overflow-hidden animate-in fade-in duration-150">
        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-slate-50">
          <div>
            <h2 className="text-sm font-bold text-slate-900">
              {t('leaves.importHolidaysTitle', 'Impor Libur Nasional Indonesia')}
            </h2>
            <p className="text-xs text-slate-500">
              {t('leaves.importHolidaysDesc', 'Impor kalender libur resmi SKB 3 Menteri secara idempotent.')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded text-slate-400 hover:text-slate-700 hover:bg-slate-200 focus:outline-none"
          >
            ✕
          </button>
        </div>

        <div className="p-4 space-y-4 text-xs">
          {successMessage && (
            <div className="p-2.5 rounded bg-emerald-50 border border-emerald-200 text-emerald-800 font-medium">
              ✓ {successMessage}
            </div>
          )}
          {errorMessage && (
            <div className="p-2.5 rounded bg-rose-50 border border-rose-200 text-rose-800">
              ⚠️ {errorMessage}
            </div>
          )}

          {/* Mode Tabs */}
          <div className="flex border-b border-slate-200 gap-4 text-xs">
            <button
              type="button"
              onClick={() => setActiveTab('preset')}
              className={`pb-2 font-semibold ${
                activeTab === 'preset'
                  ? 'border-b-2 border-slate-900 text-slate-900'
                  : 'text-slate-500 hover:text-slate-800'
              }`}
            >
              Preset Indonesia 2026 (1-Klik)
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('csv')}
              className={`pb-2 font-semibold ${
                activeTab === 'csv'
                  ? 'border-b-2 border-slate-900 text-slate-900'
                  : 'text-slate-500 hover:text-slate-800'
              }`}
            >
              Format CSV
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('json')}
              className={`pb-2 font-semibold ${
                activeTab === 'json'
                  ? 'border-b-2 border-slate-900 text-slate-900'
                  : 'text-slate-500 hover:text-slate-800'
              }`}
            >
              Format JSON
            </button>
          </div>

          {activeTab === 'preset' && (
            <div className="space-y-3">
              <p className="text-slate-600 text-xs">
                Termasuk 17 hari libur nasional resmi Republik Indonesia untuk tahun 2026.
                Jika sudah pernah diimpor, proses ini akan memperbarui nama libur tanpa menggandakan data.
              </p>
              <div className="max-h-40 overflow-y-auto border border-slate-200 rounded p-2 bg-slate-50 font-mono text-[11px] space-y-1">
                {INDONESIA_2026_HOLIDAYS.map((h) => (
                  <div key={h.holidayDate} className="flex justify-between">
                    <span className="font-semibold">{h.holidayDate}</span>
                    <span className="text-slate-700">{h.name}</span>
                  </div>
                ))}
              </div>
              <button
                type="button"
                disabled={importMutation.isPending}
                onClick={handleImportPreset}
                className="w-full py-2 bg-slate-900 hover:bg-slate-800 text-white rounded font-medium disabled:opacity-50"
              >
                {importMutation.isPending ? 'Mengimpor...' : 'Impor Sekarang (17 Hari Libur)'}
              </button>
            </div>
          )}

          {activeTab === 'csv' && (
            <div className="space-y-3">
              <p className="text-slate-500 text-[11px]">
                {t('leaves.csvRowFormat', 'Format baris:')} <code className="bg-slate-100 px-1 py-0.5 rounded">tanggal,nama_libur,cakupan</code>
                <br />
                {t('leaves.csvExample', 'Contoh: 2026-08-17,Hari Kemerdekaan RI,NATIONAL')}
              </p>
              <textarea
                rows={5}
                value={csvContent}
                onChange={(e) => setCsvContent(e.target.value)}
                placeholder={t('leaves.importPlaceholder', "2026-01-01,Tahun Baru Masehi,NATIONAL\n2026-05-01,Hari Buruh,NATIONAL")}
                className="w-full p-2 border border-slate-300 rounded font-mono text-[11px] focus:outline-none focus:ring-2 focus:ring-slate-900"
              />
              <button
                type="button"
                disabled={importMutation.isPending}
                onClick={handleImportCsv}
                className="w-full py-2 bg-slate-900 hover:bg-slate-800 text-white rounded font-medium disabled:opacity-50"
              >
                {importMutation.isPending ? 'Mengimpor...' : 'Unggah & Impor CSV'}
              </button>
            </div>
          )}

          {activeTab === 'json' && (
            <div className="space-y-3">
              <textarea
                rows={5}
                value={jsonContent}
                onChange={(e) => setJsonContent(e.target.value)}
                placeholder='[{"holidayDate":"2026-01-01","name":"Tahun Baru Masehi","scope":"NATIONAL"}]'
                className="w-full p-2 border border-slate-300 rounded font-mono text-[11px] focus:outline-none focus:ring-2 focus:ring-slate-900"
              />
              <button
                type="button"
                disabled={importMutation.isPending}
                onClick={handleImportJson}
                className="w-full py-2 bg-slate-900 hover:bg-slate-800 text-white rounded font-medium disabled:opacity-50"
              >
                {importMutation.isPending ? 'Mengimpor...' : 'Unggah & Impor JSON'}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

