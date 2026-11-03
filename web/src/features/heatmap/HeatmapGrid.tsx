import React from 'react';
import { useTranslation } from 'react-i18next';
import { HeatmapResponseData, CapacityCellData } from './HeatmapTypes';
import { HeatmapStatusCell } from './HeatmapStatusCell';

export interface HeatmapGridProps {
  data: HeatmapResponseData;
  selectedCellCoord?: { userId: string; bucketStart: string } | null;
  onSelectCell: (
    userId: string,
    userName: string,
    userRole: string,
    cell: CapacityCellData
  ) => void;
}

function formatPeriodLabel(dateStr: string, granularity: string): { main: string; sub: string } {
  const parts = dateStr.split('-');
  if (parts.length !== 3) return { main: dateStr, sub: '' };
  const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));

  if (granularity === 'DAY') {
    const dayNames = ['Min', 'Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab'];
    const dayName = dayNames[d.getDay()];
    return {
      main: `${d.getDate()}/${d.getMonth() + 1}`,
      sub: dayName,
    };
  }

  if (granularity === 'MONTH') {
    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];
    return {
      main: `${monthNames[d.getMonth()]} ${d.getFullYear()}`,
      sub: 'Bulan',
    };
  }

  // WEEK (ISO week default)
  const endDate = new Date(d);
  endDate.setDate(d.getDate() + 6);
  return {
    main: `${d.getDate()}–${endDate.getDate()} ${endDate.toLocaleDateString('id-ID', { month: 'short' })}`,
    sub: `W${getISOWeek(d)}`,
  };
}

function getISOWeek(d: Date): number {
  const date = new Date(d.getTime());
  date.setHours(0, 0, 0, 0);
  date.setDate(date.getDate() + 3 - ((date.getDay() + 6) % 7));
  const week1 = new Date(date.getFullYear(), 0, 4);
  return (
    1 +
    Math.round(
      ((date.getTime() - week1.getTime()) / 86400000 - 3 + ((week1.getDay() + 6) % 7)) / 7
    )
  );
}

export const HeatmapGrid: React.FC<HeatmapGridProps> = ({
  data,
  selectedCellCoord,
  onSelectCell,
}) => {
  const { t } = useTranslation();

  const { periods, rows, granularity } = data;

  if (!rows || rows.length === 0) {
    return (
      <div className="p-8 border border-dashed border-slate-300 rounded-lg text-center bg-white text-slate-500 text-xs">
        {t('heatmap.noUsersFound', 'Belum ada anggota tim aktif pada sistem.')}
      </div>
    );
  }

  // Compute column totals for bottom summary
  const columnTotals = periods.map((_period, colIdx) => {
    let totalCapacity = 0;
    let totalConfirmed = 0;
    let totalTentative = 0;

    rows.forEach((r) => {
      const cell = r.cells[colIdx];
      if (cell) {
        totalCapacity += cell.netCapacityHours;
        totalConfirmed += cell.confirmedHours;
        totalTentative += cell.tentativeHours;
      }
    });

    const totalWorkload = totalConfirmed;
    const utilization =
      totalCapacity > 0 ? (totalWorkload / totalCapacity) * 100 : totalWorkload > 0 ? null : 0;

    return {
      totalCapacity,
      totalConfirmed,
      totalTentative,
      utilization,
    };
  });

  return (
    <div className="overflow-x-auto border border-slate-200 rounded-lg bg-white shadow-xs">
      <table className="w-full border-collapse text-xs">
        <thead>
          <tr className="bg-slate-50 border-b border-slate-200 text-slate-700">
            {/* Sticky Member Column */}
            <th className="sticky left-0 z-20 bg-slate-50 p-3 text-left font-semibold min-w-[200px] max-w-[240px] border-r border-slate-200">
              <span className="block text-slate-900 font-bold">
                {t('heatmap.memberColumn', 'Anggota Tim')}
              </span>
              <span className="block text-[10px] text-slate-400 font-normal mt-0.5">
                {rows.length} {t('common.members', 'orang aktif')}
              </span>
            </th>

            {/* Period Columns */}
            {periods.map((period) => {
              const label = formatPeriodLabel(period, granularity);
              return (
                <th
                  key={period}
                  className="p-2.5 text-center min-w-[130px] font-semibold border-r border-slate-200 last:border-r-0"
                >
                  <span className="font-mono text-xs block text-slate-900">{label.main}</span>
                  <span className="text-[10px] text-slate-400 font-mono block mt-0.5">
                    {label.sub}
                  </span>
                </th>
              );
            })}
          </tr>
        </thead>

        <tbody className="divide-y divide-slate-100">
          {rows.map((row) => (
            <tr key={row.userId} className="hover:bg-slate-50/50 transition-colors">
              {/* Sticky Member Cell */}
              <td className="sticky left-0 z-10 bg-white p-2.5 pl-3 border-r border-slate-200">
                <div className="flex items-center justify-between gap-1">
                  <span className="font-bold text-slate-900 text-xs truncate max-w-[150px]">
                    {row.fullName}
                  </span>
                  <span className="text-[9px] font-mono px-1 py-0.2 rounded bg-slate-100 text-slate-600 border border-slate-200 shrink-0">
                    {row.role}
                  </span>
                </div>
                <div className="text-[10px] font-mono text-slate-400 mt-0.5">
                  {row.workHoursPerDay}j/hari
                </div>
              </td>

              {/* Status Cells */}
              {row.cells.map((cell) => {
                const isSelected =
                  selectedCellCoord?.userId === row.userId &&
                  selectedCellCoord?.bucketStart === cell.bucketStart;

                return (
                  <td
                    key={cell.bucketStart}
                    className="p-1.5 border-r border-slate-200 last:border-r-0 h-16 align-top"
                  >
                    <HeatmapStatusCell
                      cell={cell}
                      selected={isSelected}
                      onClick={() =>
                        onSelectCell(row.userId, row.fullName, row.role, cell)
                      }
                    />
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>

        {/* Team Totals Footer Row */}
        <tfoot>
          <tr className="bg-slate-50 border-t-2 border-slate-200 font-mono text-xs text-slate-800">
            <td className="sticky left-0 z-20 bg-slate-50 p-2.5 pl-3 font-bold border-r border-slate-200">
              <span className="text-slate-900 font-bold">{t('heatmap.teamTotal', 'Total Tim')}</span>
              <span className="block text-[10px] text-slate-400 font-normal">
                {t('heatmap.aggregate', 'Agregat Kapasitas')}
              </span>
            </td>

            {columnTotals.map((col, idx) => (
              <td
                key={periods[idx]}
                className="p-2 text-center border-r border-slate-200 last:border-r-0 align-top"
              >
                <div className="font-bold text-xs text-slate-900 tabular-nums">
                  {col.utilization !== null ? `${col.utilization.toFixed(1)}%` : 'Konflik'}
                </div>
                <div className="text-[10px] text-slate-500 tabular-nums mt-0.5">
                  {col.totalConfirmed.toFixed(0)} / {col.totalCapacity.toFixed(0)}j
                </div>
              </td>
            ))}
          </tr>
        </tfoot>
      </table>
    </div>
  );
};

