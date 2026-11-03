import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../../shared/api/httpClient';
import {
  HeatmapResponseData,
  CapacityCellData,
  GranularityType,
  AllocationSummaryData,
} from './HeatmapTypes';
import { HeatmapGrid } from './HeatmapGrid';
import { CellBreakdownDrawer } from './CellBreakdownDrawer';
import { AllocationFormDrawer } from '../allocations/AllocationFormDrawer';

interface WorkloadBalanceSuggestion {
  overloadedUserId: string;
  overloadedUserName: string;
  currentUtilizationPct: number;
  suggestedCandidateId: string;
  suggestedCandidateName: string;
  candidateAvailableHours: number;
  suggestionReason: string;
}

function getInitialDateRange(granularity: GranularityType): { from: string; to: string } {
  const today = new Date();
  const y = today.getFullYear();
  const m = today.getMonth();
  const d = today.getDate();

  if (granularity === 'DAY') {
    // Current date - 3 days to + 10 days (2 weeks of days)
    const start = new Date(y, m, d - 2);
    const end = new Date(y, m, d + 11);
    return {
      from: start.toISOString().split('T')[0],
      to: end.toISOString().split('T')[0],
    };
  }

  if (granularity === 'MONTH') {
    // Current month - 1 month to + 5 months (6 months)
    const start = new Date(y, m - 1, 1);
    const end = new Date(y, m + 5, 0);
    return {
      from: start.toISOString().split('T')[0],
      to: end.toISOString().split('T')[0],
    };
  }

  // WEEK: current week (Monday) to + 7 weeks (8 weeks total)
  const dayOfWeek = today.getDay(); // 0 is Sunday
  const diffToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  const start = new Date(y, m, d + diffToMonday);
  const end = new Date(start);
  end.setDate(start.getDate() + 8 * 7 - 1); // 8 weeks

  return {
    from: start.toISOString().split('T')[0],
    to: end.toISOString().split('T')[0],
  };
}

export const HeatmapPage: React.FC = () => {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();

  const granularity = (searchParams.get('granularity') as GranularityType) || 'WEEK';
  const includeTentative = searchParams.get('includeTentative') === 'true';

  const initialRange = getInitialDateRange(granularity);
  const from = searchParams.get('from') || initialRange.from;
  const to = searchParams.get('to') || initialRange.to;

  // Selected cell for breakdown drawer
  const [selectedCellInfo, setSelectedCellInfo] = useState<{
    userId: string;
    userName: string;
    userRole: string;
    cell: CapacityCellData;
  } | null>(null);

  // Allocation form drawer state
  const [isAllocationDrawerOpen, setIsAllocationDrawerOpen] = useState(false);
  const [editingAllocation, setEditingAllocation] = useState<AllocationSummaryData | null>(null);
  const [formInitialUserId, setFormInitialUserId] = useState<string | undefined>();
  const [formInitialStartDate, setFormInitialStartDate] = useState<string | undefined>();
  const [formInitialEndDate, setFormInitialEndDate] = useState<string | undefined>();
  const [showAiSuggestions, setShowAiSuggestions] = useState(false);

  // Fetch AI Workload Balancing Suggestions
  const { data: balanceSuggestions } = useQuery<WorkloadBalanceSuggestion[]>({
    queryKey: ['aiBalanceSuggestions'],
    queryFn: () => apiClient.get<WorkloadBalanceSuggestion[]>('/api/v1/ai/balance-suggestions'),
    refetchInterval: 60_000,
  });

  // Fetch heatmap with 30s background polling
  const { data, isLoading, error, refetch, isFetching } = useQuery<HeatmapResponseData>({
    queryKey: ['capacity', 'heatmap', from, to, granularity, includeTentative],
    queryFn: () =>
      apiClient.get<HeatmapResponseData>('/api/v1/capacity/heatmap', {
        params: {
          from,
          to,
          granularity,
          includeTentative,
        },
      }),
    refetchInterval: 30_000, // 30s auto-refresh
  });

  // Navigate period by shifting dates
  const shiftPeriod = useCallback(
    (direction: -1 | 1) => {
      const fromDate = new Date(from);
      const toDate = new Date(to);

      let shiftDays = 7;
      if (granularity === 'DAY') shiftDays = 7;
      if (granularity === 'WEEK') shiftDays = 28; // 4 weeks
      if (granularity === 'MONTH') shiftDays = 60; // ~2 months

      fromDate.setDate(fromDate.getDate() + direction * shiftDays);
      toDate.setDate(toDate.getDate() + direction * shiftDays);

      setSearchParams((prev) => {
        prev.set('from', fromDate.toISOString().split('T')[0]);
        prev.set('to', toDate.toISOString().split('T')[0]);
        return prev;
      });
    },
    [from, to, granularity, setSearchParams]
  );

  const resetToToday = useCallback(() => {
    const range = getInitialDateRange(granularity);
    setSearchParams((prev) => {
      prev.set('from', range.from);
      prev.set('to', range.to);
      return prev;
    });
  }, [granularity, setSearchParams]);

  // Keyboard navigation shortcuts: [ for prev, ] for next
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) {
        return;
      }
      if (e.key === '[') {
        shiftPeriod(-1);
      } else if (e.key === ']') {
        shiftPeriod(1);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [shiftPeriod]);

  const handleGranularityChange = (newGran: GranularityType) => {
    const range = getInitialDateRange(newGran);
    setSearchParams((prev) => {
      prev.set('granularity', newGran);
      prev.set('from', range.from);
      prev.set('to', range.to);
      return prev;
    });
  };

  const handleToggleTentative = (checked: boolean) => {
    setSearchParams((prev) => {
      prev.set('includeTentative', String(checked));
      return prev;
    });
  };

  const handleCellSelect = (
    userId: string,
    userName: string,
    userRole: string,
    cell: CapacityCellData
  ) => {
    setSelectedCellInfo({ userId, userName, userRole, cell });
  };

  const openNewAllocationDrawer = (userId?: string, startDate?: string, endDate?: string) => {
    setEditingAllocation(null);
    setFormInitialUserId(userId);
    setFormInitialStartDate(startDate);
    setFormInitialEndDate(endDate);
    setIsAllocationDrawerOpen(true);
  };

  const openEditAllocationDrawer = (alloc: AllocationSummaryData) => {
    setEditingAllocation(alloc);
    setIsAllocationDrawerOpen(true);
  };

  return (
    <div className="space-y-4">
      {/* Page Title & Action Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <span>{t('heatmap.title', 'Beban Kerja & Kapasitas Tim')}</span>
            {isFetching && !isLoading && (
              <span className="w-2 h-2 rounded-full bg-blue-500 animate-pulse" title="Sinkronisasi data..." />
            )}
          </h1>
          <p className="text-xs text-slate-500">
            {t(
              'heatmap.subtitle',
              'Matriks beban alokasi terhadap kapasitas bersih tim secara real-time.'
            )}
          </p>
        </div>

        {/* Quick Action Button */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => openNewAllocationDrawer()}
            className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded text-xs font-medium shadow-xs transition-colors focus:ring-2 focus:ring-slate-400 focus:outline-none flex items-center gap-1.5"
          >
            <span>+</span>
            <span>{t('allocations.createButton', 'Tambah Alokasi')}</span>
          </button>
        </div>
      </div>

      {/* Control Toolbar */}
      <div className="bg-white p-3 rounded-lg border border-slate-200 shadow-xs flex flex-wrap items-center justify-between gap-3 text-xs">
        {/* Granularity Zoom Switch */}
        <div className="flex items-center gap-1 bg-slate-100 p-0.5 rounded border border-slate-200">
          <button
            type="button"
            onClick={() => handleGranularityChange('DAY')}
            className={`px-2.5 py-1 rounded font-medium text-xs transition-colors ${
              granularity === 'DAY'
                ? 'bg-white text-slate-900 shadow-xs font-bold'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            {t('heatmap.granularityDay', 'Hari')}
          </button>
          <button
            type="button"
            onClick={() => handleGranularityChange('WEEK')}
            className={`px-2.5 py-1 rounded font-medium text-xs transition-colors ${
              granularity === 'WEEK'
                ? 'bg-white text-slate-900 shadow-xs font-bold'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            {t('heatmap.granularityWeek', 'Minggu')}
          </button>
          <button
            type="button"
            onClick={() => handleGranularityChange('MONTH')}
            className={`px-2.5 py-1 rounded font-medium text-xs transition-colors ${
              granularity === 'MONTH'
                ? 'bg-white text-slate-900 shadow-xs font-bold'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            {t('heatmap.granularityMonth', 'Bulan')}
          </button>
        </div>

        {/* Date Navigator */}
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => shiftPeriod(-1)}
            title="Periode sebelumnya (Tekan [)"
            className="px-2 py-1 border border-slate-300 rounded hover:bg-slate-50 font-mono text-slate-700"
          >
            ‹
          </button>
          <button
            type="button"
            onClick={resetToToday}
            className="px-2.5 py-1 border border-slate-300 rounded hover:bg-slate-50 font-medium text-slate-700"
          >
            {t('heatmap.today', 'Hari Ini')}
          </button>
          <button
            type="button"
            onClick={() => shiftPeriod(1)}
            title="Periode berikutnya (Tekan ])"
            className="px-2 py-1 border border-slate-300 rounded hover:bg-slate-50 font-mono text-slate-700"
          >
            ›
          </button>
          <span className="font-mono text-slate-500 text-[11px] ml-1">
            {from} s/d {to}
          </span>
        </div>

        {/* Include Tentative Toggle */}
        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={includeTentative}
            onChange={(e) => handleToggleTentative(e.target.checked)}
            className="rounded border-slate-300 text-slate-900 focus:ring-slate-900 w-3.5 h-3.5"
          />
          <span className="text-slate-700 font-medium">
            {t('heatmap.includeTentative', 'Sertakan Beban Tentatif')}
          </span>
        </label>
      </div>

      {/* AI Workload Balancing Suggestions Banner */}
      {balanceSuggestions && balanceSuggestions.length > 0 && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 space-y-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-base">✨</span>
              <span className="text-xs font-semibold text-amber-950">
                {t('ai.balanceSuggestionsTitle', 'Saran Penyeimbangan Beban Kerja AI')} ({balanceSuggestions.length})
              </span>
              <span className="text-[11px] text-amber-700 hidden sm:inline">
                {t(
                  'ai.balanceSuggestionsSubtitle',
                  'Anggota tim dengan beban lebih yang dapat dialihkan ke anggota berkapasitas longgar.'
                )}
              </span>
            </div>
            <button
              type="button"
              onClick={() => setShowAiSuggestions((v) => !v)}
              className="text-xs font-medium text-amber-900 hover:text-amber-950 underline cursor-pointer"
            >
              {showAiSuggestions ? t('common.close', 'Tutup') : t('common.edit', 'Lihat Rincian')}
            </button>
          </div>

          {showAiSuggestions && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-2 pt-1 border-t border-amber-200/60">
              {balanceSuggestions.map((sug) => (
                <div
                  key={`${sug.overloadedUserId}-${sug.suggestedCandidateId}`}
                  className="p-2.5 rounded bg-white border border-amber-200 text-xs space-y-1 shadow-2xs"
                >
                  <div className="flex items-center justify-between font-medium">
                    <span className="text-rose-700 font-semibold">
                      {sug.overloadedUserName} ({sug.currentUtilizationPct}% beban)
                    </span>
                    <span className="text-slate-400">➔</span>
                    <span className="text-emerald-700 font-semibold">
                      {sug.suggestedCandidateName} ({sug.candidateAvailableHours}j luang)
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-600">{sug.suggestionReason}</p>
                  <div className="pt-1 flex justify-end">
                    <button
                      type="button"
                      onClick={() => openNewAllocationDrawer(sug.suggestedCandidateId)}
                      className="px-2 py-0.5 rounded bg-amber-100 hover:bg-amber-200 text-amber-900 text-[11px] font-medium transition-colors cursor-pointer"
                    >
                      + {t('allocations.createButton', 'Tambah Alokasi')}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Main Heatmap Grid View */}
      {isLoading ? (
        <div className="p-16 border border-slate-200 rounded-lg bg-white text-center text-slate-400 text-xs">
          {t('common.loading', 'Memuat matriks beban kerja...')}
        </div>
      ) : error ? (
        <div className="p-6 border border-rose-200 rounded-lg bg-rose-50 text-rose-800 text-xs flex items-center justify-between">
          <div>
            <strong>{t('common.error', 'Gagal memuat data:')}</strong> {(error as Error).message}
          </div>
          <button
            type="button"
            onClick={() => refetch()}
            className="px-3 py-1 bg-rose-700 text-white rounded font-medium text-xs"
          >
            {t('common.retry', 'Coba Lagi')}
          </button>
        </div>
      ) : data ? (
        <HeatmapGrid
          data={data}
          selectedCellCoord={
            selectedCellInfo
              ? {
                  userId: selectedCellInfo.userId,
                  bucketStart: selectedCellInfo.cell.bucketStart,
                }
              : null
          }
          onSelectCell={handleCellSelect}
        />
      ) : null}

      {/* Legend & Accessibility Guide */}
      <div className="p-3 bg-white border border-slate-200 rounded-lg flex flex-wrap items-center gap-4 text-[11px] text-slate-600">
        <span className="font-bold text-slate-900">{t('heatmap.legend', 'Keterangan Status:')}</span>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded bg-amber-100 border border-amber-300" />
          <span>{t('capacity.underutilizedDesc', '< 60% Kurang Beban')}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded bg-emerald-100 border border-emerald-300" />
          <span>{t('capacity.optimalDesc', '60%–100% Optimal')}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded bg-rose-100 border border-rose-300 bg-stripe-overloaded" />
          <span>{t('capacity.overloadedDesc', '> 100% Kelebihan Beban (Arsir)')}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-rose-700 font-bold">⚠️ Konflik Cuti</span>
          <span>{t('capacity.conflictDesc', '(Alokasi aktif di hari cuti)')}</span>
        </div>
      </div>

      {/* Cell Breakdown Drawer */}
      {selectedCellInfo && (
        <CellBreakdownDrawer
          isOpen={Boolean(selectedCellInfo)}
          onClose={() => setSelectedCellInfo(null)}
          userId={selectedCellInfo.userId}
          userName={selectedCellInfo.userName}
          userRole={selectedCellInfo.userRole}
          cell={selectedCellInfo.cell}
          onAddAllocation={() =>
            openNewAllocationDrawer(
              selectedCellInfo.userId,
              selectedCellInfo.cell.bucketStart,
              selectedCellInfo.cell.bucketEnd
            )
          }
          onEditAllocation={openEditAllocationDrawer}
        />
      )}

      {/* Allocation Form Drawer */}
      <AllocationFormDrawer
        isOpen={isAllocationDrawerOpen}
        onClose={() => setIsAllocationDrawerOpen(false)}
        initialUserId={formInitialUserId}
        initialStartDate={formInitialStartDate}
        initialEndDate={formInitialEndDate}
        editingAllocation={editingAllocation}
        onSuccess={() => refetch()}
      />
    </div>
  );
};

