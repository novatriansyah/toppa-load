import React from 'react';
import { useTranslation } from 'react-i18next';

export interface CapacityCellData {
  bucketStart: string;
  bucketEnd: string;
  netCapacityHours: number;
  leaveHours: number;
  confirmedHours: number;
  tentativeHours: number;
  utilizationPct: number | null;
  status: 'UNDERUTILIZED' | 'OPTIMAL' | 'OVERLOADED' | 'NO_CAPACITY';
  hasLeaveConflict: boolean;
}

export interface HeatmapStatusCellProps {
  cell: CapacityCellData;
  onClick?: () => void;
  selected?: boolean;
}

export const HeatmapStatusCell: React.FC<HeatmapStatusCellProps> = ({
  cell,
  onClick,
  selected = false,
}) => {
  const { t } = useTranslation();

  const getStatusStyles = () => {
    switch (cell.status) {
      case 'OVERLOADED':
        return {
          bg: 'bg-rose-50 hover:bg-rose-100 text-rose-950 border-rose-300',
          badge: 'bg-rose-200 text-rose-900 border-rose-300',
          label: t('capacity.statusOverloaded', 'Kelebihan Beban'),
          icon: '⚠️',
        };
      case 'OPTIMAL':
        return {
          bg: 'bg-emerald-50 hover:bg-emerald-100 text-emerald-950 border-emerald-300',
          badge: 'bg-emerald-200 text-emerald-900 border-emerald-300',
          label: t('capacity.statusOptimal', 'Optimal'),
          icon: '✓',
        };
      case 'UNDERUTILIZED':
        return {
          bg: 'bg-amber-50 hover:bg-amber-100 text-amber-950 border-amber-300',
          badge: 'bg-amber-200 text-amber-900 border-amber-300',
          label: t('capacity.statusUnderutilized', 'Kurang Beban'),
          icon: 'ℹ️',
        };
      case 'NO_CAPACITY':
      default:
        return {
          bg: 'bg-slate-100 hover:bg-slate-200 text-slate-600 border-slate-200',
          badge: 'bg-slate-200 text-slate-700 border-slate-300',
          label: t('capacity.statusNoCapacity', 'Tanpa Kapasitas'),
          icon: '—',
        };
    }
  };

  const style = getStatusStyles();
  const isOverloaded = cell.status === 'OVERLOADED';

  const formatPct = (val: number | null): string => {
    if (val === null || val === undefined) {
      return '';
    }
    return `${val.toFixed(1)}%`;
  };

  const formatHours = (hours: number): string => {
    return Number.isInteger(hours) ? hours.toString() : hours.toFixed(1);
  };

  const ariaDescription = `${style.label}: ${cell.confirmedHours} dari ${cell.netCapacityHours} jam.${cell.hasLeaveConflict ? ' Ada konflik cuti.' : ''
    }`;

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaDescription}
      className={`relative w-full h-full p-2 text-left rounded border transition-all focus:outline-none focus:ring-2 focus:ring-slate-900 ${style.bg
        } ${selected ? 'ring-2 ring-slate-900 border-slate-900 shadow-sm' : ''} ${isOverloaded ? 'bg-stripe-overloaded' : ''
        }`}
    >
      <div className="flex items-center justify-between gap-1 mb-1">
        {/* Tabular Percentage or Special Conflict Label */}
        <span className="font-mono text-xs font-bold tabular-nums tracking-tight">
          {cell.utilizationPct !== null ? (
            formatPct(cell.utilizationPct)
          ) : (
            <span className="text-rose-700 font-semibold">{t('capacity.conflict', 'Konflik!')}</span>
          )}
        </span>

        {/* Accessible Badge/Icon */}
        <span
          className={`inline-flex items-center text-[10px] px-1 py-0.2 rounded border font-medium ${style.badge}`}
          title={style.label}
        >
          <span className="mr-0.5">{style.icon}</span>
          <span className="sr-only sm:not-sr-only text-[9px]">{style.label}</span>
        </span>
      </div>

      {/* Hours Ratio */}
      <div className="font-mono text-[11px] tabular-nums text-slate-700">
        <span>{formatHours(cell.confirmedHours)} / {formatHours(cell.netCapacityHours)}j</span>
      </div>

      {/* Tentative indicator if present */}
      {cell.tentativeHours > 0 && (
        <div className="mt-1 text-[10px] font-mono text-purple-700 font-medium">
          +{formatHours(cell.tentativeHours)}j {t('capacity.tentative', 'tentatif')}
        </div>
      )}

      {/* Leave conflict badge */}
      {cell.hasLeaveConflict && (
        <div className="mt-1 inline-flex items-center text-[10px] font-bold text-rose-700 bg-rose-100 border border-rose-300 rounded px-1 py-0.5 animate-pulse">
          <span className="mr-1">⚠️</span>
          <span>{t('capacity.leaveConflict', 'Konflik Cuti')}</span>
        </div>
      )}
    </button>
  );
};
